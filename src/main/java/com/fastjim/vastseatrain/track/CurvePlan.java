package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一条 90° 弯道的模型排布：<b>每格一份完整模型</b>，沿中心线精确摆放。
 *
 * <h2>为什么是“每格一份”</h2>
 * 直道模型长 1 格、宽约 3 格。弯道上如果按固定角度（例如每 7.5°）摆一份，小半径时一格会被摆上
 * 好几份、彼此大量重叠，看起来就是“一段段碎块”；同时每份模型的中心线起点又各不相同，
 * 接缝处对不上，就成了用户看到的“分段太多、弯道错位”。
 * <p>
 * 正确的做法和弯轨的思路一样：<b>一个格子只放一份模型</b>，把这份模型的原点放在
 * “该格对应的那一段弧”上，并让模型沿该点的切线旋转。于是整条弯由若干个 1 格长的模型首尾相接
 * 组成，每格一个方块、一份模型，既不会叠料，也不会跳位。
 *
 * <h2>格子怎么排</h2>
 * 从入口格中心（{@code s = 0}，与直道完全重合）出发，沿中心线按约 1 格一步步推进：
 * 每一步后的落点必须落在<b>尚未占用的格子</b>里，且离格边留有余量。
 * 落点所在格子的先后顺序就是模型顺序，两个相邻落点之间的弧段就是那一格要覆盖的一段；
 * 模型原点取这段弧的中点，接缝误差均匀摊在每格自己身上。
 * <p>
 * 起步时允许把第 2 格之后的落点稍微“往外推”（多个起点容差各试一次），
 * 以避开入口格本身正好压着格心、落点又弹回同一格的情况。
 */
public final class CurvePlan {
    /** 沿道床宽度找边缘格时的采样间距（格）。 */
    private static final double EDGE_SAMPLE = 0.03D;
    /** 道床半宽（格）：轨道模型宽约 3 格，取 1.45 格覆盖整条道床。 */
    private static final double HALF_WIDTH = 1.45D;
    /** 道床宽度方向的采样步长（格）。 */
    private static final double WIDTH_STEP = 0.25D;
    /** 落点离格边的最小距离（格）。 */
    private static final double CELL_MARGIN = 0.04D;
    /** 1 格步长的允许调整范围（格）：先找接近 1 格、又落在合法格内的落点。 */
    private static final double STEP_RANGE = 0.30D;
    /** 步长搜索的粒度（格）。 */
    private static final double STEP_GRAIN = 0.01D;

    /**
     * 一份模型。
     *
     * @param pos     所在方块
     * @param facing  方块状态朝向（模型局部 −X 大致指向的 90° 方向，只用于形状/碰撞箱）
     * @param arc     模型原点对应的弧长（格，0 = 弯道入口点）
     * @param angle   绕 Y 轴的旋转角（弧度），把模型局部 −X 转到该处切线
     * @param offsetX 模型原点在方块局部坐标里的 X（0..1）
     * @param offsetZ 模型原点在方块局部坐标里的 Z（0..1）
     */
    public record Piece(BlockPos pos, Direction facing, double arc, double angle,
                        double offsetX, double offsetZ) {
        /** 渲染参数。 */
        public RailCurve.Transform transform() {
            return new RailCurve.Transform(
                    (float) Math.cos(angle), (float) Math.sin(angle),
                    (float) offsetX, (float) offsetZ);
        }
    }

    private final RailCurve.Frame frame;
    private final int radius;
    private final boolean left;
    private final double total;
    private final double step;
    private final List<Piece> pieces;
    private final List<BlockPos> positions;

    private CurvePlan(RailCurve.Frame frame, int radius, boolean left, double total, double step,
                      List<Piece> pieces) {
        this.frame = frame;
        this.radius = radius;
        this.left = left;
        this.total = total;
        this.step = step;
        this.pieces = List.copyOf(pieces);
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (Piece piece : pieces) {
            unique.add(piece.pos());
        }
        this.positions = List.copyOf(unique);
    }

    /**
     * 生成弯道。
     *
     * @param start  入口格（弧长 0 所在格），它的 Y 就是整条弯的 Y
     * @param entry  入口前进方向
     * @param left   是否左弯
     * @param radius 轨距中心半径（格）
     */
    public static CurvePlan of(BlockPos start, Direction entry, boolean left, int radius) {
        int safeRadius = Math.max(2, radius);
        RailCurve.Frame frame = RailCurve.frame(start, entry, left, safeRadius);
        double total = RailCurve.length(safeRadius);
        int y = start.getY();

        List<Double> landings = walk(frame, total, y);

        // 每个落点与上一个落点之间的弧段 = 那一格覆盖的范围；原点取弧段中点
        Set<BlockPos> covered = new HashSet<>();
        List<BlockPos> cells = new ArrayList<>(landings.size());
        List<Double> arcs = new ArrayList<>(landings.size());
        double previous = 0.0D;
        // 入口格永远排第一：从入口点（弧长 0）到第一个落点的弧段就是它负责的范围
        BlockPos entryCell = BlockPos.containing(frame.startX(), y, frame.startZ());
        BlockPos firstLandingCell = landings.isEmpty() ? entryCell
                : BlockPos.containing(frame.pointX(landings.get(0)), y, frame.pointZ(landings.get(0)));
        if (firstLandingCell.equals(entryCell)) {
            // 落点还在入口格内：入口格覆盖入口点之后的一段，模型放在这段弧的中点
            double end = landings.isEmpty() ? total : landings.get(0);
            cells.add(entryCell);
            arcs.add(end * 0.5D);
            covered.add(entryCell);
            previous = end;
        } else {
            cells.add(entryCell);
            arcs.add(landings.isEmpty() ? total * 0.5D : landings.get(0) * 0.5D);
            covered.add(entryCell);
        }
        for (double landing : landings) {
            BlockPos cell = BlockPos.containing(frame.pointX(landing), y, frame.pointZ(landing));
            if (!covered.add(cell)) {
                // 同一个格子被走过两次（弧线擦过格角）：不再重复放模型
                previous = landing;
                continue;
            }
            cells.add(cell);
            arcs.add((previous + landing) * 0.5D);
            previous = landing;
        }

        List<Piece> pieces = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            pieces.add(pieceAt(frame, cells.get(i), arcs.get(i)));
        }

        // 道床比中心线宽得多：最外侧那一圈格子也各给一份“最近弧点”的模型，
        // 否则弯道外缘会缺一块，看起来像被啃掉。
        List<Piece> extras = new ArrayList<>();
        int steps = Math.max(8, (int) Math.ceil(total / EDGE_SAMPLE));
        for (int i = 0; i <= steps; i++) {
            double arc = total * i / steps;
            double x = frame.pointX(arc);
            double z = frame.pointZ(arc);
            double nx = -frame.tangentZ(arc);
            double nz = frame.tangentX(arc);
            for (double w = -HALF_WIDTH; w <= HALF_WIDTH + 1.0E-9D; w += WIDTH_STEP) {
                BlockPos cell = BlockPos.containing(x + nx * w, y, z + nz * w);
                if (!covered.add(cell)) {
                    continue;
                }
                extras.add(pieceAt(frame, cell, nearestArc(frame, cell, total)));
            }
        }
        pieces.addAll(extras);
        pieces.sort(Comparator.comparingDouble(Piece::arc)
                .thenComparing(p -> p.pos().getX())
                .thenComparing(p -> p.pos().getZ()));
        return new CurvePlan(frame, safeRadius, left, total, 1.0D, pieces);
    }

    /**
     * 沿中心线一步步走，返回“每一格落到哪里”的弧长列表（不含 {@code s = 0}）。
     * <p>
     * 每一步都在 {@code 1 ± STEP_RANGE} 里找一个落在新格内的落点，优先取最接近 1 格的；
     * 只要还有路可走就继续，直到出口或走不动为止。
     */
    private static List<Double> walk(RailCurve.Frame frame, double total, int y) {
        List<Double> landings = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        // 入口格在下一步时仍允许作为落点（它需要覆盖入口点之后的一整段弧），
        // 但更远的落点不会再回到入口格：这里只在第一步放行
        BlockPos entryCell = BlockPos.containing(frame.startX(), y, frame.startZ());
        double from = 0.0D;
        while (from < total - 1.0E-6D) {
            Double found = nextLanding(frame, from, total, y, used);
            if (found == null) {
                // 走不动了：直接补到出口，最后这一格稍微长一点
                landings.add(total);
                break;
            }
            landings.add(found);
            used.add(BlockPos.containing(frame.pointX(found), y, frame.pointZ(found)));
            from = found;
        }
        if (landings.isEmpty() || landings.get(landings.size() - 1) < total - 1.0E-9D) {
            landings.add(total);
        }
        return landings;
    }

    /** 找从 {@code from} 出发、落在未占用格内的下一个落点；候选按“离 1 格的距离”从小到大。 */
    private static Double nextLanding(RailCurve.Frame frame, double from, double total, int y,
                                      Set<BlockPos> used) {
        for (int i = 0; i * STEP_GRAIN <= STEP_RANGE + 1.0E-9D; i++) {
            for (int sign = 0; sign <= 1; sign++) {
                double delta = i * STEP_GRAIN;
                if (sign == 1) {
                    delta = -delta;
                }
                if (i == 0 && sign == 1) {
                    continue;
                }
                double candidate = from + 1.0D + delta;
                if (candidate >= total - 1.0E-9D) {
                    return total;
                }
                if (candidate <= from + 0.30D || edgeDistance(frame, candidate) < CELL_MARGIN) {
                    continue;
                }
                BlockPos cell = BlockPos.containing(frame.pointX(candidate), y, frame.pointZ(candidate));
                if (!used.contains(cell)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** 弧长 {@code s} 处的中心线点到最近格边的距离（格）。 */
    private static double edgeDistance(RailCurve.Frame frame, double s) {
        double x = frame.pointX(s);
        double z = frame.pointZ(s);
        double fx = x - Math.floor(x);
        double fz = z - Math.floor(z);
        return Math.min(Math.min(fx, 1.0D - fx), Math.min(fz, 1.0D - fz));
    }

    /** 把格心投影到中心线上，返回最近的弧长。 */
    private static double nearestArc(RailCurve.Frame frame, BlockPos pos, double total) {
        double cx = pos.getX() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        double best = 0.0D;
        double bestDistance = Double.MAX_VALUE;
        int steps = Math.max(32, (int) Math.ceil(total / STEP_GRAIN));
        for (int i = 0; i <= steps; i++) {
            double s = total * i / steps;
            double dx = frame.pointX(s) - cx;
            double dz = frame.pointZ(s) - cz;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = s;
            }
        }
        return best;
    }

    /** 在 {@code pos} 里摆一份模型，原点放在弧长 {@code arc} 处。 */
    private static Piece pieceAt(RailCurve.Frame frame, BlockPos pos, double arc) {
        double x = frame.pointX(arc);
        double z = frame.pointZ(arc);
        return new Piece(pos, facingAt(frame, arc), arc, frame.modelAngle(arc),
                x - pos.getX(), z - pos.getZ());
    }

    /** 预估弯道需要的方块数（用于界面估算）。 */
    public static int estimateBlocks(int radius) {
        // 中心线弧长 + 道床两侧各约 1 格宽
        return (int) Math.ceil(RailCurve.length(Math.max(2, radius))) + 6;
    }

    /** 弧长处最接近 90° 的朝向（模型局部 −X 为前进方向）。 */
    public static Direction facingAt(RailCurve.Frame frame, double s) {
        return frame.heading(s);
    }

    public RailCurve.Frame frame() {
        return frame;
    }

    public int radius() {
        return radius;
    }

    public boolean left() {
        return left;
    }

    /** 弯道中心线总弧长。 */
    public double arcLength() {
        return total;
    }

    /** 相邻两份模型的弧长差（正常为 1 格）。 */
    public double step() {
        return step;
    }

    /** 所有模型，按弧长排序。 */
    public List<Piece> pieces() {
        return pieces;
    }

    /** 去重后的方块位置（每格只放一个方块）。 */
    public List<BlockPos> positions() {
        return positions;
    }

    /**
     * 指定格子里的模型应该朝哪个方向：取该格模型的弧长。
     * 这个朝向只影响方块形状，不参与渲染。
     */
    public Direction facingAt(BlockPos pos) {
        for (Piece piece : pieces) {
            if (piece.pos().equals(pos)) {
                return piece.facing();
            }
        }
        return Direction.NORTH;
    }

    /** 弯道出口方向。 */
    public Direction exit() {
        return frame.exit;
    }
}

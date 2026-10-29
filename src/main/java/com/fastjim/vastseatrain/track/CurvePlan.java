package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次 90° 弯道的条带排布。
 * <h2>怎么摆</h2>
 * 每格放一份<b>完整的</b>模型（与直道同一份美术资源），只做平移 + 绕 Y 轴旋转：
 * <pre>
 *   x' = cos · p.x − sin · p.z + originX
 *   z' = sin · p.x + cos · p.z + originZ
 * </pre>
 * 模型原点放在<b>它所在格里的那一段弧</b>上（取该段弧的中点），旋转角取该处的切线方向。
 * <h2>为什么按“格内的那一段弧”放，而不是按全局弧长等分</h2>
 * 弯道的中心线是斜着穿过方格的，沿全局弧长等分时，下一份模型的原点可能落在两格开外，
 * 结果“下一格”根本分不到模型，那一格就只能拿远处模型的原点来凑，画面里就是一条条歪掉的错位。
 * 按格内弧段放，每份模型的中心永远在它自己的格子里，轨道在每格范围内都与弧线对齐：
 * <pre>
 *   本格分到的弧段 [a, b]  →  模型原点 = 弧长 (a+b)/2 处的点
 * </pre>
 * 相邻两格的弧段首尾相接，只要模型半长（0.8 格）不小于弧段长度的一半，接缝处就不会有空段。
 * <!-- -->
 * <h2>格子怎么找</h2>
 * 只按“弧线经过的格子”放方块，相邻格会经常只在对角相接，看起来断开。
 * 所以先沿弧线采样、把采样点扩到道床半宽（1.6 格），得到弯道覆盖的每一格；
 * 每格的弧段从“上一格与本格采样区间的中点”开始，到“本格与下一格的采样区间中点”结束。
 */
public final class CurvePlan {
    /** 沿弧线的采样间距（格）。 */
    private static final double SAMPLE = 0.05D;
    /** 道床半宽（格）：模型宽约 3 格，多留一点余量，保证外侧格子不被漏掉。 */
    private static final double HALF_WIDTH = 1.6D;
    /** 采样点向道床宽度方向扩展的步长（格）。 */
    private static final double WIDTH_STEP = 0.5D;
    /** 单条条带最多覆盖的弧长（格）：模型长 1.6，留接缝余量。 */
    private static final double MAX_REACH = RailCurve.STRIP_HALF_LENGTH * 2.0D - 0.08D;

    /**
     * 一份模型。
     *
     * @param pos     所在方块
     * @param facing  方块状态朝向（模型局部 −X 大致指向的 90° 方向，只用于形状/碰撞箱）
     * @param arc     模型原点对应的弧长（格，0 = 弯道入口）
     * @param angle   绕 Y 轴的旋转角（弧度），把模型局部 −X 转到该处切线
     * @param originX 模型原点在方块局部坐标里的 X（0..1）
     * @param originZ 模型原点在方块局部坐标里的 Z（0..1）
     */
    public record Piece(BlockPos pos, Direction facing, double arc, double angle,
                        double originX, double originZ) {
        /** 渲染参数。 */
        public RailCurve.Transform transform() {
            return new RailCurve.Transform(
                    (float) Math.cos(angle), (float) Math.sin(angle),
                    (float) originX, (float) originZ);
        }
    }

    /** 一个被弯道穿过的格子，以及它分到的弧段 {@code [from, to]}。 */
    private record Cell(BlockPos pos, double from, double to) {
    }

    private final RailCurve.Frame frame;
    private final int radius;
    private final boolean left;
    private final double total;
    private final List<Piece> pieces;
    private final List<BlockPos> positions;

    private CurvePlan(RailCurve.Frame frame, int radius, boolean left, double total, List<Piece> pieces) {
        this.frame = frame;
        this.radius = radius;
        this.left = left;
        this.total = total;
        this.pieces = List.copyOf(pieces);
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (Piece piece : pieces) {
            unique.add(piece.pos());
        }
        this.positions = List.copyOf(unique);
    }

    /**
     * 生成弯道条带。
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

        // 1) 采样得到“覆盖到的格子”，按第一次被穿过的顺序排列（弧长递增）
        Map<BlockPos, double[]> spans = new LinkedHashMap<>();
        int steps = Math.max(8, (int) Math.ceil(total / SAMPLE));
        for (int i = 0; i <= steps; i++) {
            double s = total * i / steps;
            double x = frame.pointX(s);
            double z = frame.pointZ(s);
            double nx = -frame.tangentZ(s);
            double nz = frame.tangentX(s);
            record(spans, new BlockPos(Mth.floor(x), start.getY(), Mth.floor(z)), s);
            for (double w = -HALF_WIDTH; w <= HALF_WIDTH + 1.0E-9D; w += WIDTH_STEP) {
                record(spans, new BlockPos(Mth.floor(x + nx * w), start.getY(), Mth.floor(z + nz * w)), s);
            }
        }
        List<BlockPos> order = new ArrayList<>(spans.keySet());

        // 2) 每格的弧段：从“上一格与本格采样区间的中点”到“本格与下一格采样区间的中点”。
        //    起点格子从弧长 0 起算，末格一直延伸到弯道终点。
        List<Cell> cells = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            double[] here = spans.get(order.get(i));
            double from = i == 0 ? 0.0D : (spans.get(order.get(i - 1))[1] + here[0]) * 0.5D;
            double to = i == order.size() - 1 ? total : (here[1] + spans.get(order.get(i + 1))[0]) * 0.5D;
            double mid = (from + to) * 0.5D;
            double reach = Math.min(MAX_REACH, to - from);
            // 一份模型只能盖 1.6 格：弧段比它长时，以中点为中心缩小；比它短时原样居中
            from = mid - reach * 0.5D;
            cells.add(new Cell(order.get(i), from, from + reach));
        }

        // 3) 每格一份模型，原点落在该格弧段的中点
        List<Piece> pieces = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            pieces.add(pieceAt(frame, start, cell.pos(), (cell.from() + cell.to()) * 0.5D));
        }
        pieces.sort(Comparator.comparingDouble(Piece::arc)
                .thenComparing(p -> p.pos().getX())
                .thenComparing(p -> p.pos().getZ()));
        return new CurvePlan(frame, safeRadius, left, total, pieces);
    }

    private static void record(Map<BlockPos, double[]> spans, BlockPos pos, double s) {
        double[] span = spans.get(pos);
        if (span == null) {
            spans.put(pos, new double[]{s, s});
            return;
        }
        span[0] = Math.min(span[0], s);
        span[1] = Math.max(span[1], s);
    }

    /** 在 {@code pos} 里摆一份模型，原点放在弧长 {@code arc} 处。 */
    private static Piece pieceAt(RailCurve.Frame frame, BlockPos start, BlockPos pos, double arc) {
        double x = frame.pointX(arc);
        double z = frame.pointZ(arc);
        return new Piece(pos, facingAt(frame, arc), arc, frame.modelAngle(arc), x - pos.getX(), z - pos.getZ());
    }

    /** 预估弯道需要的方块数（用于界面估算）。 */
    public static int estimateBlocks(int radius) {
        // 中心线弧长 × 平均宽度（约 1.8 格）+ 少量边角
        return (int) Math.ceil(RailCurve.length(Math.max(2, radius)) * 1.8D) + 4;
    }

    /** 弧长处的最近 90° 方向（模型局部 −X 为前进方向）。 */
    public static Direction facingAt(RailCurve.Frame frame, double s) {
        double tx = frame.tangentX(s);
        double tz = frame.tangentZ(s);
        if (Math.abs(tx) >= Math.abs(tz)) {
            return tx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return tz >= 0 ? Direction.SOUTH : Direction.NORTH;
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

    /** 所有条带，按弧长排序。 */
    public List<Piece> pieces() {
        return pieces;
    }

    /** 去重后的方块位置（每格只放一个方块）。 */
    public List<BlockPos> positions() {
        return positions;
    }

    /**
     * 指定格子里的模型应该朝哪个方向：取该格条带的弧长。
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

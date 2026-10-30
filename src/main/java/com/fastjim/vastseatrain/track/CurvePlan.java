package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一条 90° 弯道的模型排布：沿中心线等弧长采样，包含首尾端点。
 *
 * <h2>模型实例与占位方块</h2>
 * 采样间隔不大于 1 格，不沿道床宽度额外复制模型。急弯时多个采样点可能落入同一方块，
 * 方块实体会保留该格内的全部模型实例；占位位置则按方块去重。
 * <p>
 * 相比固定角度采样，等弧长采样可使模型沿弧线均匀衔接，避免模型起点分布忽疏忽密。
 */
public final class CurvePlan {
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

        int intervals = Math.max(1, (int) Math.ceil(total));
        double sampleStep = total / intervals;
        List<Piece> pieces = new ArrayList<>(intervals + 1);
        for (int i = 0; i <= intervals; i++) {
            double arc = i == intervals ? total : i * sampleStep;
            BlockPos pos = BlockPos.containing(frame.pointX(arc), y, frame.pointZ(arc));
            pieces.add(pieceAt(frame, pos, arc));
        }
        return new CurvePlan(frame, safeRadius, left, total, sampleStep, pieces);
    }

    /** 在 {@code pos} 里摆一份模型，原点放在弧长 {@code arc} 处。 */
    private static Piece pieceAt(RailCurve.Frame frame, BlockPos pos, double arc) {
        double x = frame.pointX(arc);
        double z = frame.pointZ(arc);
        return new Piece(pos, facingAt(frame, arc), arc, frame.modelAngle(arc),
                x - pos.getX(), z - pos.getZ());
    }

    /** 弯道沿中心线的模型实例数（用于界面估算）。 */
    public static int estimatePieces(int radius) {
        return (int) Math.ceil(RailCurve.length(Math.max(2, radius))) + 1;
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

    /** 相邻两份模型的等弧长间隔（不大于 1 格）。 */
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

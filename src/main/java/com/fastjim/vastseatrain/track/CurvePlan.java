package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次 90° 弯道的“条带”排布，做法与 Immersive Railroading 一致：
 * 沿弧线按固定弧长重复摆放<b>整份</b>轨道模型，每份只做平移 + 绕 Y 轴旋转（转到该处的切线方向）。
 * <ul>
 *     <li>每份模型都是满长度、满宽度，不做裁剪，所以永远不会出现“钢轨被切断”的断面；</li>
 *     <li>圆周转弯时内侧的条带自然重叠、外侧略有间隙，间隙由模型自身宽度（3 格）盖住；</li>
 *     <li>每份模型只占 1 个方块，方块位置取它的中心所在格；同一格落了两份时合并成一份。</li>
 * </ul>
 * <h2>条带的位置</h2>
 * 弧长轴被等分成 {@code n = round(弧长)} 份，第 {@code i} 份覆盖弧段 {@code [i·Δ, (i+1)·Δ]}，
 * 条带中心放在该弧段中点，因此相邻条带首尾相接、误差平均分摊。
 * <p>
 * 条带的格内偏移（{@code offsetX/offsetZ}）与旋转角一起写进方块实体，客户端渲染时直接取用：
 * 方块状态只记录“模型大致朝向”（用于碰撞箱与破坏判定），不参与几何。
 */
public final class CurvePlan {
    /**
     * 一条条带：一个方块里放一份完整模型。
     *
     * @param pos     方块位置（条带中心所在格）
     * @param facing  方块状态朝向（模型局部 −X 大致指向的 90° 方向，仅用于形状/碰撞）
     * @param arc     条带中心的弧长（格，0 = 弯道入口）
     * @param from    本条带覆盖弧段的起点
     * @param to      本条带覆盖弧段的终点（{@code to - from} 就是被合并的条带跨度）
     * @param angle   模型绕 Y 轴的旋转角（弧度，世界坐标；把局部 −X 转到切线）
     * @param offsetX 条带中心在方块内的 X 偏移（0..1）
     * @param offsetZ 条带中心在方块内的 Z 偏移（0..1）
     */
    public record Piece(BlockPos pos, Direction facing, double arc, double from, double to, double angle,
                        double offsetX, double offsetZ) {
    }

    private final RailCurve.Frame frame;
    private final int radius;
    private final boolean left;
    private final double step;
    private final List<Piece> pieces;
    private final List<BlockPos> positions;

    private CurvePlan(RailCurve.Frame frame, int radius, boolean left, double step, List<Piece> pieces) {
        this.frame = frame;
        this.radius = radius;
        this.left = left;
        this.step = step;
        this.pieces = List.copyOf(pieces);
        List<BlockPos> list = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            list.add(piece.pos());
        }
        this.positions = List.copyOf(list);
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
        int count = stripCount(total);
        double step = total / count;

        // 同格合并：半径很小时相邻两条可能落进同一格（方块只能有一个）。
        // 合并后必须仍然覆盖两条的整段弧，否则相邻条带中间会空出缺口，
        // 所以合并条的弧段是两条之并，中心与旋转角取并集的中点。
        Map<BlockPos, double[]> spans = new LinkedHashMap<>();
        List<Piece> pieces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double from = i * step;
            double to = (i + 1) * step;
            double mid = (from + to) * 0.5D;
            BlockPos pos = new BlockPos(Mth.floor(frame.pointX(mid)), start.getY(), Mth.floor(frame.pointZ(mid)));
            double[] span = spans.get(pos);
            if (span == null) {
                spans.put(pos, new double[]{from, to});
            } else {
                span[0] = Math.min(span[0], from);
                span[1] = Math.max(span[1], to);
            }
        }
        for (Map.Entry<BlockPos, double[]> entrySpan : spans.entrySet()) {
            BlockPos pos = entrySpan.getKey();
            double from = entrySpan.getValue()[0];
            double to = entrySpan.getValue()[1];
            double mid = (from + to) * 0.5D;
            double x = frame.pointX(mid);
            double z = frame.pointZ(mid);
            pieces.add(new Piece(pos, facingAt(frame, mid), mid, from, to, frame.modelAngle(mid),
                    x - pos.getX(), z - pos.getZ()));
        }
        pieces.sort((a, b) -> Double.compare(a.arc(), b.arc()));
        return new CurvePlan(frame, safeRadius, left, step, pieces);
    }

    /** 90° 弯等分出的条数（至少 1）。 */
    public static int stripCount(double arcLength) {
        return Math.max(1, (int) Math.round(arcLength));
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

    /** 相邻条带之间的弧长间距（合并前的等分长度）。 */
    public double step() {
        return step;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public List<BlockPos> positions() {
        return positions;
    }

    /** 弯道出口方向。 */
    public Direction exit() {
        return frame.exit;
    }
}

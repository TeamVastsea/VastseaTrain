package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次 90° 弯道的“逐格条带”排布。
 * <h2>为什么必须逐格铺满</h2>
 * 弯道的中心线是斜着穿过方格的。如果只按“弧线经过哪格”去放方块，
 * 相邻两格会经常只在对角相接，中间没有方块，轨道看起来就“断开”了。
 * 所以这里先把弯道覆盖到的<b>每一个格子</b>都找出来（沿弧线采样，把采样点向外扩到道床半宽），
 * 再让每格都有自己的一份模型。这样四邻接意义上不会断开。
 * <h2>模型怎么摆</h2>
 * 每格放一份<b>完整的</b>模型（与直道同一份美术资源），只做平移 + 绕 Y 轴旋转，
 * 转到该格对应弧长处的切线方向。整条弯道共用同一个径向微调 {@code radial}
 * （见 {@link RailCurve.Transform}），模型钢轨因此正好压在以圆心为中心的圆弧上；
 * 模型原点按<b>弧长</b>交替摆放（第 k 份在弧长 k 处），相邻条带沿切线方向自然重叠、
 * 接缝处顶点完全一致。格内再对原点做一次限幅，保证每格轨道大致落在自己的格子里。
 */
public final class CurvePlan {
    /** 沿弧线的采样间距（格）。 */
    private static final double SAMPLE = 0.05D;
    /** 道床半宽（格）：模型宽 3 格，多放一点余量，保证外侧格子不被漏掉。 */
    private static final double HALF_WIDTH = 1.6D;
    /** 采样点向道床宽度方向扩展的步长（格）。 */
    private static final double WIDTH_STEP = 0.25D;
    /** 模型原点相对格心的最大偏移（格）。 */
    private static final double MAX_CELL_DRIFT = 0.47D;
    /** 入口过渡长度（格）：第一份与直道完全一致，之后在这么长的弧长内过渡到整条弯的固定值。 */
    private static final double BLEND = 2.0D;

    /**
     * 一个弯道方块。
     *
     * @param pos     方块位置
     * @param facing  方块状态朝向（模型局部 −X 大致指向的 90° 方向，只用于形状/碰撞箱）
     * @param arc     该份模型对应的弧长（格，0 = 弯道入口）
     * @param angle   模型绕 Y 轴的旋转角（弧度，世界坐标；把局部 −X 转到切线）
     * @param originX 模型原点在该格里的 X（方块局部坐标，0..1）
     * @param originZ 模型原点在该格里的 Z（方块局部坐标，0..1）
     * @param radial  模型原点的径向微调（格，负值朝圆心）
     */
    public record Piece(BlockPos pos, Direction facing, double arc, double angle,
                        double originX, double originZ, double radial) {
        /** 渲染参数（旋转中心取格心）。 */
        public RailCurve.Transform transform() {
            return new RailCurve.Transform(
                    (float) Math.cos(angle), (float) Math.sin(angle),
                    (float) originX, (float) originZ, (float) radial);
        }
    }

    /** 一个被弯道覆盖的格子。 */
    private static final class Cell {
        /** 离中心线最近的采样弧长（避免格内多次采样时取到边角）。 */
        double arc;
        double distance = Double.MAX_VALUE;
    }

    private final RailCurve.Frame frame;
    private final int radius;
    private final boolean left;
    private final double total;
    private final double radial;
    private final List<Piece> pieces;
    private final List<BlockPos> positions;

    private CurvePlan(RailCurve.Frame frame, int radius, boolean left, double total, double radial,
                      List<Piece> pieces) {
        this.frame = frame;
        this.radius = radius;
        this.left = left;
        this.total = total;
        this.radial = radial;
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

        // 1) 采样：记录弯道覆盖到的格子，以及每格中心线经过的弧长
        Map<BlockPos, Cell> cells = new HashMap<>();
        Set<BlockPos> order = new LinkedHashSet<>();
        int steps = Math.max(8, (int) Math.ceil(total / SAMPLE));
        for (int i = 0; i <= steps; i++) {
            double s = total * i / steps;
            double x = frame.pointX(s);
            double z = frame.pointZ(s);
            double nx = -frame.tangentZ(s);
            double nz = frame.tangentX(s);
            BlockPos centre = new BlockPos(Mth.floor(x), start.getY(), Mth.floor(z));
            register(cells, order, centre, s, 0.0D);
            for (double w = -HALF_WIDTH; w <= HALF_WIDTH + 1.0E-9D; w += WIDTH_STEP) {
                double px = x + nx * w;
                double pz = z + nz * w;
                register(cells, order, new BlockPos(Mth.floor(px), start.getY(), Mth.floor(pz)), s, Math.abs(w));
            }
        }

        // 2) 第 k 份模型放在弧长 k 处（k = 0..ceil(总弧长)-1），相邻份首尾相接、接缝顶点一致
        List<Piece> pieces = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        int count = Math.max(1, (int) Math.ceil(total));
        for (int k = 0; k < count; k++) {
            double s = Math.min(total, k);
            BlockPos pos = new BlockPos(Mth.floor(frame.pointX(s)), start.getY(), Mth.floor(frame.pointZ(s)));
            if (claimed.add(pos)) {
                pieces.add(piece(frame, pos, s, total));
            }
        }
        // 3) 被覆盖到但还没放模型的格子（急弯处格子多出来），补一份离它最近的那段弧
        for (BlockPos pos : order) {
            if (claimed.add(pos)) {
                pieces.add(piece(frame, pos, cells.get(pos).arc, total));
            }
        }
        pieces.sort(Comparator.comparingDouble(Piece::arc).thenComparing(p -> p.pos().getX())
                .thenComparing(p -> p.pos().getZ()));
        return new CurvePlan(frame, safeRadius, left, total, RailCurve.HALF_GAUGE / safeRadius, pieces);
    }

    private static void register(Map<BlockPos, Cell> cells, Set<BlockPos> order,
                                 BlockPos pos, double arc, double distance) {
        Cell cell = cells.computeIfAbsent(pos, key -> {
            order.add(key);
            return new Cell();
        });
        if (distance < cell.distance) {
            cell.distance = distance;
            cell.arc = arc;
        }
    }

    private static Piece piece(RailCurve.Frame frame, BlockPos pos, double arc, double total) {
        double angle = frame.modelAngle(arc);
        // 入口那份必须与直道完全一致（原点在格心、不做径向微调），之后很快过渡到整条弯的固定值
        double shaped = Mth.clamp(arc / BLEND, 0.0D, 1.0D);
        // 按弧长摆出来的原点可能贴到格边（急弯时相邻格弧长差接近 2 格），限幅到格内
        double x = Mth.clamp(frame.pointX(arc) - pos.getX(), 0.5D - MAX_CELL_DRIFT, 0.5D + MAX_CELL_DRIFT);
        double z = Mth.clamp(frame.pointZ(arc) - pos.getZ(), 0.5D - MAX_CELL_DRIFT, 0.5D + MAX_CELL_DRIFT);
        return new Piece(pos, facingAt(frame, arc), arc, angle, x, z,
                RailCurve.HALF_GAUGE / frame.radius * shaped);
    }

    /** 预设半径下弯道大致需要的格数，用于界面估算。 */
    public static int estimateBlocks(int radius) {
        // 中心线弧长 × 平均宽度（约 2 格）+ 少量边角
        return (int) Math.ceil(RailCurve.length(Math.max(2, radius)) * 2.2D) + 4;
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

    /** 整条弯共用的径向微调（格）。 */
    public double radial() {
        return radial;
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

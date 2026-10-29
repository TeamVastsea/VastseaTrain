package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 90° 弯道的平面几何，是服务端铺设、客户端渲染、摘要显示共用的“唯一真相”。
 * <h2>约定</h2>
 * <ul>
 *     <li>轨距中心半径 {@code R}（格），单根钢轨到轨距中心 {@link #HALF_GAUGE}（从美术模型的钢轨位置实测）。</li>
 *     <li>{@code start} 是入口格，{@code entry} 是进入弯道时的前进方向；弧长参数 {@code s} 从入口格中心起算。</li>
 *     <li>圆心 = 入口格中心 + {@code R} × 弯心方向，所以 {@code s = 0} 处的点就是入口格中心、切线就是 {@code entry}，
 *     与前面的直道严丝合缝。</li>
 *     <li>{@code s = 0..πR/2} 就是整条弯，条带如何摆放见 {@link CurvePlan}。</li>
 * </ul>
 * 世界 X 向东、Z 向南；方块 {@code (x,y,z)} 占 {@code [x,x+1] × [z,z+1]}。
 */
public final class RailCurve {
    /**
     * 单根钢轨中心到轨距中心的距离（格）。
     * <p>
     * 直道模型实测：两根钢轨的横坐标落在 {@code |x| ≈ 0.5..0.79}，中心线约 {@code |x| = 0.645}，
     * 所以半个轨距（中心线到中心线的一半）取 0.645。弯道按这个值推算内外轨各自的半径，
     * 直道与弯道衔接处钢轨位置才对得上。
     */
    public static final double HALF_GAUGE = 0.645D;
    /** 弯道总圆心角（90°）。 */
    public static final double ANGLE = Math.PI * 0.5D;

    private RailCurve() {
    }

    /** 弯道总弧长（轨距中心，格）。 */
    public static double length(int radius) {
        return ANGLE * radius;
    }

    public static double directionX(Direction dir) {        return switch (dir) {
            case EAST -> 1.0D;
            case WEST -> -1.0D;
            default -> 0.0D;
        };
    }

    public static double directionZ(Direction dir) {
        return switch (dir) {
            case SOUTH -> 1.0D;
            case NORTH -> -1.0D;
            default -> 0.0D;
        };
    }

    /** 弯心方向（左弯取左侧、右弯取右侧）。 */
    public static Direction inward(Direction entry, boolean left) {
        return left ? entry.getCounterClockWise() : entry.getClockWise();
    }

    /** 出口前进方向（绕完 90° 后正好朝弯心方向）。 */
    public static Direction exit(Direction entry, boolean left) {
        return inward(entry, left);
    }

    /**
     * 一条弯道的坐标系。
     * <p>
     * 以圆心为原点：{@code u} 指向入口点（{@code θ = 0}），{@code v} 指向切线（{@code θ} 增大的方向）。
     */
    public static final class Frame {
        public final int radius;
        public final double centerX;
        public final double centerZ;
        public final Direction entry;
        public final Direction exit;
        public final boolean left;
        final double ux;
        final double uz;
        final double vx;
        final double vz;

        Frame(int radius, BlockPos start, Direction entry, boolean left) {
            this.radius = Math.max(1, radius);
            this.entry = entry;
            this.left = left;
            this.exit = RailCurve.exit(entry, left);
            Direction inward = RailCurve.inward(entry, left);
            this.centerX = start.getX() + 0.5D + directionX(inward) * this.radius;
            this.centerZ = start.getZ() + 0.5D + directionZ(inward) * this.radius;
            this.ux = -directionX(inward);
            this.uz = -directionZ(inward);
            this.vx = directionX(entry);
            this.vz = directionZ(entry);
        }

        /** 弯道入口点（世界坐标，轨距中心线上）。 */
        public double startX() {
            return centerX + radius * ux;
        }

        public double startZ() {
            return centerZ + radius * uz;
        }

        public double pointX(double s) {
            double t = s / radius;
            return centerX + radius * (ux * Math.cos(t) + vx * Math.sin(t));
        }

        public double pointZ(double s) {
            double t = s / radius;
            return centerZ + radius * (uz * Math.cos(t) + vz * Math.sin(t));
        }

        /** 弧长处的单位切线 {@code (tangentX, tangentZ)}。 */
        public double tangentX(double s) {
            double t = s / radius;
            return -ux * Math.sin(t) + vx * Math.cos(t);
        }

        public double tangentZ(double s) {
            double t = s / radius;
            return -uz * Math.sin(t) + vz * Math.cos(t);
        }

        /**
         * 把模型从局部摆到世界里所需的角度（弧度）。
         * <p>
         * 模型局部前进方向是 {@code −X}，要让它在世界里指向切线 {@code (tx, tz)}：
         * 绕 Y 轴转 {@code θ} 后局部 {@code (−1, 0)} 变成 {@code (−cos θ, −sin θ)}，
         * 所以 {@code cos θ = −tx}、{@code sin θ = −tz}。
         */
        public double modelAngle(double s) {
            return Math.atan2(-tangentZ(s), -tangentX(s));
        }

        public double distance(double x, double z) {
            return Math.hypot(x - centerX, z - centerZ);
        }

        /** 世界点 {@code (x, z)} 到圆心的方位角（弧度，{@code 0} 在入口点，向切线方向增大）。 */
        public double angleOf(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return Math.atan2(dx * vx + dz * vz, dx * ux + dz * uz);
        }

        /** 世界点 {@code (x, z)} 到轨距中心的径向距离（格）。 */
        public double radial(double x, double z) {
            return Math.hypot(x - centerX, z - centerZ);
        }

        /** 入口点（世界坐标）到该点的<b>有符号</b>径向距离：圆内为负、圆外为正，钢轨设计值即 {@code ±HALF_GAUGE}。 */
        public double signedOffset(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return Math.hypot(dx, dz) - radius;
        }

        /**
         * 把世界坐标换到“圆轨道局部坐标”：长度轴沿切线（前进方向为正）、宽度轴沿半径方向
         * （与直道模型一致，{@code +Z} 在前进方向左侧），径向用 {@link #signedOffset} 衡量。
         * 这样同一套判定可以直接套在直道模型上。
         */
        public double localLength(double x, double z, double arc) {
            double t = arc / radius;
            double tx = -ux * Math.sin(t) + vx * Math.cos(t);
            double tz = -uz * Math.sin(t) + vz * Math.cos(t);
            return (x - pointX(arc)) * tx + (z - pointZ(arc)) * tz;
        }
    }

    public static Frame frame(BlockPos start, Direction entry, boolean left, int radius) {
        return new Frame(radius, start, entry, left);
    }

    /** 一块道床覆盖的弧长（格）。模型沿长度方向的半长 0.801+，所以接缝余量按 0.8 算。 */
    public static final double STRIP_HALF_LENGTH = 0.8D;

    /**
     * 一份模型的摆放：绕 Y 轴旋转角的余弦/正弦，以及模型原点在方块局部坐标里的位置。
     * <p>
     * 渲染公式（{@code p} 是模型原始顶点，原点垂直居中）：
     * <pre>
     *   x' = cos · p.x − sin · p.z + originX
     *   y' = p.y + 0.5
     *   z' = sin · p.x + cos · p.z + originZ
     * </pre>
     */
    public record Transform(float cos, float sin, float originX, float originZ) {
    }
}

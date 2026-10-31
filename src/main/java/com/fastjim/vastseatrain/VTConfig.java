package com.fastjim.vastseatrain;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置：直线最大长度、弯道半径范围及性能提示阈值。
 * <ul>
 *     <li>直线段数与弯道中心线弧长分别限制；</li>
 *     <li>{@code curve.maxRadius} 允许最大弯道半径，实际还受角度与弧长上限约束；</li>
 *     <li>弯道半径还受最小半径限制；</li>
 *     <li>模型实例达到 256 和 512 时，蓝图界面分别显示黄色和红色性能警告。</li>
 * </ul>
 */
public final class VTConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue CURVE_MIN_RADIUS;
    private static final ForgeConfigSpec.IntValue MAX_PLACEMENT;
    private static final ForgeConfigSpec.IntValue CURVE_MAX_RADIUS;
    public static final int MAX_CURVE_ARC_LENGTH = 1024;
    public static final int MAX_CURVE_RADIUS = 32768;
    public static final int PERFORMANCE_WARNING_THRESHOLD = 256;
    public static final int PERFORMANCE_CRITICAL_THRESHOLD = 512;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Track placement settings").push("curve");
        CURVE_MIN_RADIUS = builder
                .comment("Minimum curve radius, in blocks.")
                .defineInRange("minRadius", 10, 3, 100000);
        MAX_PLACEMENT = builder
                .comment("Maximum straight length in segments.")
                .defineInRange("maxRadius", 1024, 10, 100000);
        CURVE_MAX_RADIUS = builder
                .comment("Maximum curve centerline radius in blocks; curve arc length is capped separately.")
                .defineInRange("curveMaxRadius", MAX_CURVE_RADIUS, 10, MAX_CURVE_RADIUS);
        builder.pop();
        SPEC = builder.build();
    }

    private VTConfig() {
    }

    public static int minRadius() {
        return Math.min(CURVE_MIN_RADIUS.get(), maxPlacement());
    }

    /** 直道段数与弯道半径共用的最大铺设上限。 */
    public static int maxPlacement() {
        return MAX_PLACEMENT.get();
    }

    public static int maxRadius() {
        return CURVE_MAX_RADIUS.get();
    }

    /** Largest radius that respects both the configured radius and the per-placement arc limit. */
    public static int maxCurveRadius(int angleDegrees) {
        int angle = Math.max(1, Math.min(90, angleDegrees));
        int byLength = (int) Math.floor((MAX_CURVE_ARC_LENGTH - Math.sqrt(0.5D))
                / Math.toRadians(angle));
        return Math.max(2, Math.min(maxRadius(), byLength));
    }

    public static int minCurveRadius(int angleDegrees) {
        return Math.min(minRadius(), maxCurveRadius(angleDegrees));
    }

    public static int clampCurveRadius(int radius, int angleDegrees) {
        return Math.max(minCurveRadius(angleDegrees), Math.min(maxCurveRadius(angleDegrees), radius));
    }

    public static double curveArcLength(int radius, int angleDegrees) {
        return Math.max(1, radius) * Math.toRadians(Math.max(1, Math.min(90, angleDegrees)));
    }

    /** Distance from the requested circular endpoint to the nearest block-center grid point. */
    public static double curveFitDistance(int radius, int angleDegrees) {
        double angle = Math.toRadians(Math.max(1, Math.min(90, angleDegrees)));
        double advance = Math.max(1, radius) * Math.sin(angle);
        double lateral = Math.max(1, radius) * (1.0D - Math.cos(angle));
        return Math.hypot(advance - Math.rint(advance), lateral - Math.rint(lateral));
    }

    public static boolean curveEndpointMoves(int radius, int angleDegrees) {
        double angle = Math.toRadians(Math.max(1, Math.min(90, angleDegrees)));
        double advance = Math.max(1, radius) * Math.sin(angle);
        double inward = Math.max(1, radius) * (1.0D - Math.cos(angle));
        return Math.rint(advance) != 0.0D || Math.rint(inward) != 0.0D;
    }

    /** 0 = no warning, 1 = warning, 2 = critical warning. */
    public static int performanceWarningLevel(int estimatedModelPieces) {
        if (estimatedModelPieces >= PERFORMANCE_CRITICAL_THRESHOLD) return 2;
        if (estimatedModelPieces >= PERFORMANCE_WARNING_THRESHOLD) return 1;
        return 0;
    }

    /**
     * 界面里首次进入弯道模式时的默认半径。
     * <p>
     * 取 64：弧长 ∓ 100 条左右，铺出来是一段很明显的弯，又不至于让第一次尝试就等太久。
     */
    public static int defaultRadius() {
        return clampRadius(64);
    }

    /** 把半径限制进配置允许的范围。 */
    public static int clampRadius(int radius) {
        return clampCurveRadius(radius, 90);
    }
}

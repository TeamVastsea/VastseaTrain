package com.fastjim.vastseatrain;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置：轨道最大铺设长度与弯道最小半径。
 * <ul>
 *     <li>{@code curve.maxRadius} 同时作为直道最大段数与弯道最大半径；</li>
 *     <li>弯道半径还受最小半径限制；</li>
 *     <li>模型实例超过 256 和 512 时，蓝图界面分别显示黄色和红色性能警告。</li>
 * </ul>
 */
public final class VTConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue CURVE_MIN_RADIUS;
    private static final ForgeConfigSpec.IntValue MAX_PLACEMENT;
    public static final int PERFORMANCE_WARNING_THRESHOLD = 256;
    public static final int PERFORMANCE_CRITICAL_THRESHOLD = 512;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Track placement settings").push("curve");
        CURVE_MIN_RADIUS = builder
                .comment("Minimum radius of a 90 degree curve, in blocks.")
                .defineInRange("minRadius", 10, 3, 100000);
        MAX_PLACEMENT = builder
                .comment("Maximum straight length in segments and maximum curve radius in blocks.")
                .defineInRange("maxRadius", 1024, 10, 100000);
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
        return maxPlacement();
    }

    /** 0 = no warning, 1 = warning, 2 = critical warning. */
    public static int performanceWarningLevel(int estimatedModelPieces) {
        if (estimatedModelPieces > PERFORMANCE_CRITICAL_THRESHOLD) return 2;
        if (estimatedModelPieces > PERFORMANCE_WARNING_THRESHOLD) return 1;
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
        return Math.max(minRadius(), Math.min(maxRadius(), radius));
    }
}

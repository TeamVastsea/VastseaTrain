package com.fastjim.vastseatrain;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置。目前只有弯道半径范围：
 * <ul>
 *     <li>最小值/最大值限制界面与指令可输入的半径，代码本身不设硬上限；</li>
 *     <li>超过与性能警告线（默认 256）时界面会提示段数，避免一次铺出上千格。</li>
 * </ul>
 */
public final class VTConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue CURVE_MIN_RADIUS;
    private static final ForgeConfigSpec.IntValue CURVE_MAX_RADIUS;
    private static final ForgeConfigSpec.IntValue CURVE_WARN_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Track curve settings").push("curve");
        CURVE_MIN_RADIUS = builder
                .comment("Minimum radius of a 90 degree curve, in blocks.")
                .defineInRange("minRadius", 10, 3, 100000);
        CURVE_MAX_RADIUS = builder
                .comment("Maximum radius of a 90 degree curve, in blocks.")
                .defineInRange("maxRadius", 1024, 10, 100000);
        CURVE_WARN_RADIUS = builder
                .comment("Radii above this value show a performance warning in the blueprint screen.")
                .defineInRange("warnRadius", 256, 1, 100000);
        builder.pop();
        SPEC = builder.build();
    }

    private VTConfig() {
    }

    public static int minRadius() {
        return CURVE_MIN_RADIUS.get();
    }

    public static int maxRadius() {
        return Math.max(minRadius(), CURVE_MAX_RADIUS.get());
    }

    public static int warnRadius() {
        return CURVE_WARN_RADIUS.get();
    }

    /** 半径是否超过性能警告线。 */
    public static boolean needsWarning(int radius) {
        return radius > warnRadius();
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

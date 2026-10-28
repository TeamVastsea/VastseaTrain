package com.fastjim.vastseatrain.track;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** 轨距选项，命名沿用 IR 的常用写法。 */
public enum RailGauge implements StringRepresentable {
    STANDARD("standard", 1.435F),
    NARROW("narrow", 1.0F),
    BROAD("broad", 1.676F);

    private final String name;
    private final float meters;

    RailGauge(String name, float meters) {
        this.name = name;
        this.meters = meters;
    }

    public float meters() {
        return meters;
    }

    public Component getDisplayName() {
        return Component.translatable("gauge.vastseatrain." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static RailGauge byName(String name) {
        for (RailGauge gauge : values()) {
            if (gauge.name.equals(name)) {
                return gauge;
            }
        }
        return STANDARD;
    }

    public RailGauge next() {
        return values()[(ordinal() + 1) % values().length];
    }
}

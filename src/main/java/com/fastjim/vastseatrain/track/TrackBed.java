package com.fastjim.vastseatrain.track;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** 道床类型，对应轨道模型中的底座材质分组。 */
public enum TrackBed implements StringRepresentable {
    GRAVEL("gravel"),
    CONCRETE("concrete"),
    NONE("none");

    private final String name;

    TrackBed(String name) {
        this.name = name;
    }

    public Component getDisplayName() {
        return Component.translatable("bed.vastseatrain." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static TrackBed byName(String name) {
        for (TrackBed bed : values()) {
            if (bed.name.equals(name)) {
                return bed;
            }
        }
        return GRAVEL;
    }

    public TrackBed next() {
        return values()[(ordinal() + 1) % values().length];
    }
}

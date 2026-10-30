package com.fastjim.vastseatrain.track;

import net.minecraft.util.StringRepresentable;

/** Placement mode selected in the blueprint screen. */
public enum BlueprintMode implements StringRepresentable {
    STRAIGHT("straight"),
    CURVE("curve");

    private final String name;

    BlueprintMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public BlueprintMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static BlueprintMode byName(String name) {
        for (BlueprintMode mode : values()) {
            if (mode.name.equals(name)) {
                return mode;
            }
        }
        return STRAIGHT;
    }
}

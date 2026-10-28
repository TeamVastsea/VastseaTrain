package com.fastjim.vastseatrain.track;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * 轨道蓝图保存在物品上的铺设参数。服务器端以这里的数据为准，客户端界面只负责编辑后同步。
 */
public record BlueprintSettings(int length, RailGauge gauge, TrackBed bed, boolean preview) {
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 256;

    public static final BlueprintSettings DEFAULT =
            new BlueprintSettings(8, RailGauge.STANDARD, TrackBed.GRAVEL, false);

    public BlueprintSettings withLength(int value) {
        return new BlueprintSettings(clampLength(value), gauge, bed, preview);
    }

    public BlueprintSettings withGauge(RailGauge value) {
        return new BlueprintSettings(length, value, bed, preview);
    }

    public BlueprintSettings withBed(TrackBed value) {
        return new BlueprintSettings(length, gauge, value, preview);
    }

    public BlueprintSettings withPreview(boolean value) {
        return new BlueprintSettings(length, gauge, bed, value);
    }

    /** 一段轨道沿延伸方向跨越的方块数。 */
    public int blockSpan() {
        return TrackLayout.BLOCK_SPAN;
    }

    /** 沿线方块数（不含两侧支撑块）。 */
    public int totalBlocks() {
        return length * TrackLayout.BLOCK_SPAN;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("length", length);
        tag.putString("gauge", gauge.getSerializedName());
        tag.putString("bed", bed.getSerializedName());
        tag.putBoolean("preview", preview);
        return tag;
    }

    public static BlueprintSettings load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return DEFAULT;
        }
        BlueprintSettings settings = DEFAULT;
        if (tag.contains("length")) {
            settings = settings.withLength(tag.getInt("length"));
        }
        if (tag.contains("gauge")) {
            settings = settings.withGauge(RailGauge.byName(tag.getString("gauge")));
        }
        if (tag.contains("bed")) {
            settings = settings.withBed(TrackBed.byName(tag.getString("bed")));
        }
        if (tag.contains("preview")) {
            settings = settings.withPreview(tag.getBoolean("preview"));
        }
        return settings;
    }

    public static BlueprintSettings from(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TrackBlueprintData.TAG)) {
            return DEFAULT;
        }
        return load(tag.getCompound(TrackBlueprintData.TAG));
    }

    public void write(ItemStack stack) {
        stack.getOrCreateTag().put(TrackBlueprintData.TAG, save());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(length);
        buf.writeEnum(gauge);
        buf.writeEnum(bed);
        buf.writeBoolean(preview);
    }

    public static BlueprintSettings decode(FriendlyByteBuf buf) {
        return new BlueprintSettings(
                clampLength(buf.readVarInt()),
                buf.readEnum(RailGauge.class),
                buf.readEnum(TrackBed.class),
                buf.readBoolean());
    }

    public static int clampLength(int value) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, value));
    }
}

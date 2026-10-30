package com.fastjim.vastseatrain.track;

import com.fastjim.vastseatrain.VTConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * 轨道蓝图保存在物品上的铺设参数。服务器端以这里的数据为准，客户端界面只负责编辑后同步。
 *
 * @param length      直线段数（1..{@link #MAX_LENGTH}）
 * @param gauge       轨距
 * @param bed         道床
 * @param preview     是否显示预览
 * @param curveRadius 90° 弯道半径（格，0 表示铺直道）
 * @param curveLeft   弯道是否左转（否则右转）
 * @param style       轨道外观（只影响之后新铺设的轨道）
 */
public record BlueprintSettings(int length, RailGauge gauge, TrackBed bed, boolean preview,
                                int curveRadius, boolean curveLeft, RailStyle style) {
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 256;

    public static final BlueprintSettings DEFAULT =
            new BlueprintSettings(8, RailGauge.STANDARD, TrackBed.GRAVEL, false, 0, false, RailStyle.STANDARD);

    public BlueprintSettings withLength(int value) {
        return new BlueprintSettings(clampLength(value), gauge, bed, preview, curveRadius, curveLeft, style);
    }

    public BlueprintSettings withGauge(RailGauge value) {
        return new BlueprintSettings(length, value, bed, preview, curveRadius, curveLeft, style);
    }

    public BlueprintSettings withBed(TrackBed value) {
        return new BlueprintSettings(length, gauge, value, preview, curveRadius, curveLeft, style);
    }

    public BlueprintSettings withPreview(boolean value) {
        return new BlueprintSettings(length, gauge, bed, value, curveRadius, curveLeft, style);
    }

    /**
     * 设置弯道半径；0 表示铺直道，其余按配置限制在允许区间内。
     */
    public BlueprintSettings withCurveRadius(int value) {
        int radius = value <= 0 ? 0 : VTConfig.clampRadius(value);
        return new BlueprintSettings(length, gauge, bed, preview, radius, curveLeft, style);
    }

    public BlueprintSettings withCurveLeft(boolean value) {
        return new BlueprintSettings(length, gauge, bed, preview, curveRadius, value, style);
    }

    /** 换一套轨道外观（之后新铺设的轨道才使用）。 */
    public BlueprintSettings withStyle(RailStyle value) {
        return new BlueprintSettings(length, gauge, bed, preview, curveRadius, curveLeft,
                value == null ? RailStyle.STANDARD : value);
    }

    /** 是否铺 90° 弯道。 */
    public boolean isCurve() {
        return curveRadius > 0;
    }

    /** 一段轨道沿延伸方向跨越的方块数。 */
    public int blockSpan() {
        return TrackLayout.BLOCK_SPAN;
    }

    /** 直线轨道沿线的方块数，不含两侧支撑块。 */
    public int totalBlocks() {
        return length * TrackLayout.BLOCK_SPAN;
    }

    /** 弯道沿中心线摆放的模型实例估算数。 */
    public int estimatedCurvePieces() {
        return CurvePlan.estimatePieces(curveRadius);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("length", length);
        tag.putString("gauge", gauge.getSerializedName());
        tag.putString("bed", bed.getSerializedName());
        tag.putBoolean("preview", preview);
        tag.putInt("curve_radius", curveRadius);
        tag.putBoolean("curve_left", curveLeft);
        tag.putString("rail_style", style.getSerializedName());
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
        if (tag.contains("curve_radius")) {
            settings = settings.withCurveRadius(tag.getInt("curve_radius"));
        }
        if (tag.contains("curve_left")) {
            settings = settings.withCurveLeft(tag.getBoolean("curve_left"));
        }
        if (tag.contains("rail_style")) {
            settings = settings.withStyle(RailStyle.byName(tag.getString("rail_style")));
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
        buf.writeVarInt(curveRadius);
        buf.writeBoolean(curveLeft);
        buf.writeEnum(style);
    }

    public static BlueprintSettings decode(FriendlyByteBuf buf) {
        return new BlueprintSettings(
                clampLength(buf.readVarInt()),
                buf.readEnum(RailGauge.class),
                buf.readEnum(TrackBed.class),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readEnum(RailStyle.class));
    }

    public static int clampLength(int value) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, value));
    }
}

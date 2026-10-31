package com.fastjim.vastseatrain.track;

import com.fastjim.vastseatrain.VTConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Blueprint placement settings persisted on the item and validated by the server. */
public record BlueprintSettings(RailGauge gauge, TrackBed bed, boolean preview,
                                int curveRadius, boolean curveLeft, RailStyle style,
                                BlueprintMode mode, int curveAngle, int curveFitPercent,
                                int lineAngle, int lineMultiplier) {
    public static final int MIN_MULTIPLIER = 1;

    public static final BlueprintSettings DEFAULT = new BlueprintSettings(
            RailGauge.STANDARD, TrackBed.GRAVEL, false, VTConfig.defaultRadius(), false,
            RailStyle.STANDARD, BlueprintMode.STRAIGHT, 90, 0, 0, 8);

    public BlueprintSettings withGauge(RailGauge value) {
        return copy(value, bed, preview, curveRadius, curveLeft, style,
                mode, curveAngle, curveFitPercent, lineAngle, lineMultiplier);
    }

    public BlueprintSettings withBed(TrackBed value) {
        return copy(gauge, value, preview, curveRadius, curveLeft, style,
                mode, curveAngle, curveFitPercent, lineAngle, lineMultiplier);
    }

    public BlueprintSettings withPreview(boolean value) {
        return copy(gauge, bed, value, curveRadius, curveLeft, style,
                mode, curveAngle, curveFitPercent, lineAngle, lineMultiplier);
    }

    public BlueprintSettings withCurveRadius(int value) {
        if (value <= 0) {
            return withMode(BlueprintMode.STRAIGHT);
        }
        return copy(gauge, bed, preview, VTConfig.clampCurveRadius(value, curveAngle), curveLeft, style,
                BlueprintMode.CURVE, curveAngle, curveFitPercent, lineAngle, lineMultiplier);
    }

    public BlueprintSettings withCurveAngle(int value) {
        int angle = Math.max(1, Math.min(90, value));
        int radius = VTConfig.clampCurveRadius(curveRadius, angle);
        return copy(gauge, bed, preview, radius, curveLeft, style,
                mode, angle, curveFitPercent, lineAngle, lineMultiplier);
    }

    public BlueprintSettings withCurveFitPercent(int value) {
        return copy(gauge, bed, preview, curveRadius, curveLeft, style,
                mode, curveAngle, Math.max(0, Math.min(100, value)), lineAngle, lineMultiplier);
    }

    public BlueprintSettings withCurveLeft(boolean value) {
        return copy(gauge, bed, preview, curveRadius, value, style,
                mode, curveAngle, curveFitPercent, lineAngle, lineMultiplier);
    }

    public BlueprintSettings withMode(BlueprintMode value) {
        return copy(gauge, bed, preview, curveRadius, curveLeft, style,
                value == null ? BlueprintMode.STRAIGHT : value, curveAngle, curveFitPercent,
                lineAngle, lineMultiplier);
    }

    public BlueprintSettings withLineAngle(int value) {
        int angle = Math.max(0, Math.min(89, value));
        DiagonalSolver.Solution solution = DiagonalSolver.solve(angle);
        int multiplier = Math.max(MIN_MULTIPLIER,
                Math.min(lineMultiplier, DiagonalSolver.maxMultiplier(solution)));
        return copy(gauge, bed, preview, curveRadius, curveLeft, style,
                mode, curveAngle, curveFitPercent, angle, multiplier);
    }

    public BlueprintSettings withLineMultiplier(int value) {
        int max = maxLineMultiplier();
        int multiplier = Math.max(MIN_MULTIPLIER, Math.min(max, value));
        return copy(gauge, bed, preview, curveRadius, curveLeft, style,
                mode, curveAngle, curveFitPercent, lineAngle, multiplier);
    }

    public BlueprintSettings withStyle(RailStyle value) {
        return copy(gauge, bed, preview, curveRadius, curveLeft,
                value == null ? RailStyle.STANDARD : value, mode, curveAngle, curveFitPercent,
                lineAngle, lineMultiplier);
    }

    private static BlueprintSettings copy(RailGauge gauge, TrackBed bed, boolean preview,
                                          int curveRadius, boolean curveLeft, RailStyle style,
                                          BlueprintMode mode, int curveAngle, int curveFitPercent,
                                          int lineAngle, int lineMultiplier) {
        return new BlueprintSettings(gauge, bed, preview, curveRadius, curveLeft, style,
                mode, curveAngle, curveFitPercent, lineAngle, lineMultiplier);
    }

    public boolean isCurve() {
        return mode == BlueprintMode.CURVE;
    }

    public int estimatedCurvePieces() {
        return CurvePlan.estimatePieces(curveRadius, curveAngle);
    }

    public double curveFitTolerance() {
        return curveFitPercent / 100.0D;
    }

    public double curveFitDistance() {
        return VTConfig.curveFitDistance(curveRadius, curveAngle);
    }

    public int maxCurveRadius() {
        return VTConfig.maxCurveRadius(curveAngle);
    }

    public boolean curveFitValid() {
        return curveFitDistance() <= curveFitTolerance() + 1.0E-8D
                && VTConfig.curveEndpointMoves(curveRadius, curveAngle);
    }

    public DiagonalSolver.Solution lineSolution() {
        return DiagonalSolver.solve(lineAngle);
    }

    public int maxLineMultiplier() {
        return DiagonalSolver.maxMultiplier(lineSolution());
    }

    public double lineLength() {
        return lineSolution().minimumLength() * lineMultiplier;
    }

    /** Longest block-axis span of one primitive grid-aligned increment. */
    public int lineGridSpanUnit() {
        DiagonalSolver.Solution solution = lineSolution();
        return Math.max(solution.advance(), solution.lateral());
    }

    public int lineGridSpan() {
        return lineGridSpanUnit() * lineMultiplier;
    }

    public int lineTotalAdvance() {
        return lineSolution().advance() * lineMultiplier;
    }

    public int lineTotalLateral() {
        return lineSolution().lateral() * lineMultiplier;
    }

    public int estimatedLinePieces() {
        return lineAngle == 0 ? lineMultiplier : (int) Math.ceil(lineLength()) + 1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("gauge", gauge.getSerializedName());
        tag.putString("bed", bed.getSerializedName());
        tag.putBoolean("preview", preview);
        tag.putInt("curve_radius", curveRadius);
        tag.putBoolean("curve_left", curveLeft);
        tag.putString("rail_style", style.getSerializedName());
        tag.putString("mode", mode.getSerializedName());
        tag.putInt("curve_angle", curveAngle);
        tag.putInt("curve_fit", curveFitPercent);
        tag.putInt("line_angle", lineAngle);
        tag.putInt("line_multiplier", lineMultiplier);
        return tag;
    }

    public static BlueprintSettings load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return DEFAULT;
        }
        BlueprintSettings settings = DEFAULT;
        if (tag.contains("gauge")) settings = settings.withGauge(RailGauge.byName(tag.getString("gauge")));
        if (tag.contains("bed")) settings = settings.withBed(TrackBed.byName(tag.getString("bed")));
        if (tag.contains("preview")) settings = settings.withPreview(tag.getBoolean("preview"));
        if (tag.contains("curve_angle")) settings = settings.withCurveAngle(tag.getInt("curve_angle"));
        if (tag.contains("curve_radius")) settings = settings.withCurveRadius(tag.getInt("curve_radius"));
        if (tag.contains("curve_fit")) settings = settings.withCurveFitPercent(tag.getInt("curve_fit"));
        if (tag.contains("curve_left")) settings = settings.withCurveLeft(tag.getBoolean("curve_left"));
        if (tag.contains("rail_style")) settings = settings.withStyle(RailStyle.byName(tag.getString("rail_style")));
        if (tag.contains("mode")) settings = settings.withMode(BlueprintMode.byName(tag.getString("mode")));
        if (tag.contains("line_angle")) settings = settings.withLineAngle(tag.getInt("line_angle"));
        if (tag.contains("line_multiplier")) settings = settings.withLineMultiplier(tag.getInt("line_multiplier"));
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
        buf.writeEnum(gauge);
        buf.writeEnum(bed);
        buf.writeBoolean(preview);
        buf.writeVarInt(curveRadius);
        buf.writeBoolean(curveLeft);
        buf.writeEnum(style);
        buf.writeEnum(mode);
        buf.writeVarInt(curveAngle);
        buf.writeVarInt(curveFitPercent);
        buf.writeVarInt(lineAngle);
        buf.writeVarInt(lineMultiplier);
    }

    public static BlueprintSettings decode(FriendlyByteBuf buf) {
        RailGauge gauge = buf.readEnum(RailGauge.class);
        TrackBed bed = buf.readEnum(TrackBed.class);
        boolean preview = buf.readBoolean();
        int curveRadius = Math.max(VTConfig.minRadius(), Math.min(VTConfig.maxRadius(), buf.readVarInt()));
        boolean curveLeft = buf.readBoolean();
        RailStyle style = buf.readEnum(RailStyle.class);
        BlueprintMode mode = buf.readEnum(BlueprintMode.class);
        int curveAngle = Math.max(1, Math.min(90, buf.readVarInt()));
        int curveFitPercent = Math.max(0, Math.min(100, buf.readVarInt()));
        int lineAngle = Math.max(0, Math.min(89, buf.readVarInt()));
        int lineMultiplier = buf.readVarInt();
        return new BlueprintSettings(gauge, bed, preview, curveRadius, curveLeft, style, mode,
                curveAngle, curveFitPercent, lineAngle, MIN_MULTIPLIER)
                .withCurveRadius(curveRadius)
                .withLineMultiplier(lineMultiplier);
    }
}

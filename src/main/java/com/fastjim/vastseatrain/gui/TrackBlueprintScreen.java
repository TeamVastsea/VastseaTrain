package com.fastjim.vastseatrain.gui;

import com.fastjim.vastseatrain.VTConfig;
import com.fastjim.vastseatrain.network.NetworkHandler;
import com.fastjim.vastseatrain.network.SaveBlueprintMessage;
import com.fastjim.vastseatrain.track.BlueprintSettings;
import com.fastjim.vastseatrain.track.BlueprintMode;
import com.fastjim.vastseatrain.track.DiagonalSolver;
import com.fastjim.vastseatrain.track.RailStyle;
import com.fastjim.vastseatrain.track.TrackLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * 轨道蓝图三栏界面：左栏轨道参数、中栏道床与预览、右栏实时摘要。
 * <p>
 * 窗口足够大时使用设计尺寸；较小时统一缩放面板、控件与点击坐标，避免任何一栏被裁掉。
 */
public class TrackBlueprintScreen extends AdaptiveScreen {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_GAP = 8;
    private static final int PADDING = 8;
    private static final int MARGIN = 6;

    private final ItemStack blueprint;
    private BlueprintSettings settings;

    private RadiusSlider radiusSlider;
    private CurveValueSlider curveAngleSlider;
    private CurveValueSlider curveFitSlider;
    private EditBox lineMultiplierBox;
    private EditBox lineAngleBox;
    private DiagonalAngleSlider lineAngleSlider;
    private Button modeButton;
    private Button turnButton;
    private Button gaugeButton;
    private Button styleButton;

    /** 本次排版的左上角与面板宽度（窗口尺寸变化时重新计算）。 */
    private int originX;
    private int originY;
    private int panelWidth;
    private int panelGap;
    /** 三个面板的高度（独立于左栏控件行距）。 */
    private int panelHeight;

    public TrackBlueprintScreen(ItemStack blueprint) {
        super(Component.translatable("screen.vastseatrain.blueprint"), 380, 218);
        this.blueprint = blueprint;
        this.settings = BlueprintSettings.from(blueprint);
    }

    @Override
    protected void init() {
        hideHud();
        layout();

        int top = originY + 18;
        int leftX = originX + PADDING;
        int middleX = originX + panelWidth + panelGap;
        int rightX = originX + (panelWidth + panelGap) * 2;
        int innerWidth = panelWidth - PADDING * 2;

        // 左栏：铺设模式（直线 / 90° 弯道）
        modeButton = addRenderableWidget(new DirectionalButton(leftX, top + 20, innerWidth, 20,
                modeLabel(), (b, direction) -> {
                    commitActiveInput();
                    settings = settings.withMode(cycle(settings.mode(), direction));
                    b.setMessage(modeLabel());
                    railControlsVisible(settings.mode(), false);
                }));

        int angleInputWidth = Math.min(48, Math.max(40, innerWidth / 3));
        int angleSliderWidth = innerWidth - angleInputWidth - 6;
        lineAngleSlider = addRenderableWidget(new DiagonalAngleSlider(leftX, top + 54,
                angleSliderWidth, 20, settings.lineAngle(), angle -> {
                    settings = settings.withLineAngle(angle);
                    if (lineAngleBox != null && !lineAngleBox.isFocused()) {
                        lineAngleBox.setValue(Integer.toString(angle));
                    }
                    clampLineMultiplierInput();
                }));
        lineAngleBox = new EditBox(font, leftX + angleSliderWidth + 6, top + 54,
                angleInputWidth, 20, Component.empty());
        lineAngleBox.setValue(Integer.toString(settings.lineAngle()));
        lineAngleBox.setMaxLength(2);
        lineAngleBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        lineAngleBox.setResponder(text -> {
            if (text.isEmpty()) return;
            int angle = Math.max(0, Math.min(89, safeParse(text, settings.lineAngle())));
            settings = settings.withLineAngle(angle);
            if (lineAngleSlider != null) lineAngleSlider.setDegrees(angle);
            if (angle != safeParse(text, angle)) lineAngleBox.setValue(Integer.toString(angle));
            clampLineMultiplierInput();
        });
        addRenderableWidget(lineAngleBox);

        curveAngleSlider = addRenderableWidget(new CurveValueSlider(leftX, top + 52, innerWidth, 20,
                1, 90, settings.curveAngle(), value -> {
                    settings = settings.withCurveAngle(value);
                    if (radiusSlider != null) radiusSlider.setAngle(settings.curveAngle(), settings.curveRadius());
                }));

        radiusSlider = addRenderableWidget(new RadiusSlider(leftX, top + 80, innerWidth, 20,
                settings.curveAngle(), settings.curveRadius(), value -> settings = settings.withCurveRadius(value)));

        curveFitSlider = addRenderableWidget(new CurveValueSlider(leftX, top + 142, innerWidth, 20,
                0, 100, settings.curveFitPercent(), value -> settings = settings.withCurveFitPercent(value)));

        lineMultiplierBox = new EditBox(font, leftX, top + 100, innerWidth, 18, Component.empty());
        lineMultiplierBox.setValue(Integer.toString(settings.lineMultiplier()));
        lineMultiplierBox.setMaxLength(Integer.toString(VTConfig.maxPlacement()).length());
        lineMultiplierBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        lineMultiplierBox.setResponder(text -> {
            if (!text.isEmpty() && !settings.isCurve()) {
                int parsed = safeParse(text, settings.lineMultiplier());
                settings = settings.withLineMultiplier(parsed);
                if (parsed != settings.lineMultiplier()) {
                    lineMultiplierBox.setValue(Integer.toString(settings.lineMultiplier()));
                }
            }
        });
        addRenderableWidget(lineMultiplierBox);

        turnButton = addRenderableWidget(new DirectionalButton(leftX, top + 108, innerWidth, 20,
                turnLabel(), (b, direction) -> {
                    settings = settings.withCurveLeft(!settings.curveLeft());
                    b.setMessage(turnLabel());
                }));
        turnButton.active = settings.isCurve();
        turnButton.visible = settings.isCurve();

        gaugeButton = addRenderableWidget(new DirectionalButton(leftX, top + 166, innerWidth, 20,
                gaugeLabel(), (b, direction) -> {
                    settings = settings.withGauge(cycle(settings.gauge(), direction));
                    b.setMessage(gaugeLabel());
                }));

        railControlsVisible(settings.mode(), false);

        addRenderableWidget(new DirectionalButton(middleX + PADDING, top + 20, innerWidth, 20,
                bedLabel(), (b, direction) -> {
                    settings = settings.withBed(cycle(settings.bed(), direction));
                    b.setMessage(bedLabel());
                }));

        addRenderableWidget(new DirectionalButton(middleX + PADDING, top + 62, innerWidth, 20,
                previewLabel(), (b, direction) -> {
                    settings = settings.withPreview(!settings.preview());
                    b.setMessage(previewLabel());
                }));

        // 中栏：轨道模型（打开二级界面：左侧列表 + 右侧 3D 预览）
        styleButton = addRenderableWidget(Button.builder(styleLabel(), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new TrackStyleScreen(this, settings.style()));
            }
        }).bounds(middleX + PADDING, top + 104, innerWidth, 20).build());

    }

    @Override
    public void removed() {
        com.fastjim.vastseatrain.client.render.RailPreviewRenderer.release();
        super.removed();
    }

    /** 按窗口尺寸排版：放得下时用设计尺寸，放不下时收窄面板（但不小于可用的最小宽度）。 */
    private void layout() {
        int logicalWidth = logicalWidth();
        int logicalHeight = logicalHeight();
        panelGap = Math.max(4, Math.min(PANEL_GAP, logicalWidth / 80));
        int usable = logicalWidth - MARGIN * 2;
        panelWidth = Mth.clamp((usable - panelGap * 2) / 3, 120, PANEL_WIDTH);
        int contentWidth = panelWidth * 3 + panelGap * 2;
        // 左栏最后一行控件在 top + 168 处结束，面板至少要比它多留一点边距
        panelHeight = Mth.clamp(logicalHeight - MARGIN * 2, 222, 250);
        originX = (logicalWidth - contentWidth) / 2;
        originY = (logicalHeight - panelHeight) / 2;
    }

    /** Shows only the controls belonging to the selected placement mode. */
    private void railControlsVisible(BlueprintMode mode, boolean keep) {
        boolean curve = mode == BlueprintMode.CURVE;
        if (curveAngleSlider != null) {
            curveAngleSlider.visible = curve;
            curveAngleSlider.active = curve;
        }
        if (curveFitSlider != null) {
            curveFitSlider.visible = curve;
            curveFitSlider.active = curve;
        }
        if (radiusSlider != null) {
            radiusSlider.visible = curve;
            radiusSlider.active = curve;
        }
        if (lineAngleSlider != null) {
            lineAngleSlider.visible = !curve;
            lineAngleSlider.active = !curve;
        }
        if (lineAngleBox != null) {
            lineAngleBox.visible = !curve;
            lineAngleBox.setEditable(!curve);
            if (!keep) lineAngleBox.setFocused(false);
        }
        if (lineMultiplierBox != null) {
            lineMultiplierBox.visible = !curve;
            lineMultiplierBox.setEditable(!curve);
            if (!keep) lineMultiplierBox.setFocused(false);
        }
        if (turnButton != null) {
            turnButton.visible = curve;
            turnButton.active = curve;
        }
    }

    private Component gaugeLabel() {
        return Component.translatable("gui.vastseatrain.gauge", settings.gauge().getDisplayName());
    }

    private Component modeLabel() {
        return Component.translatable("gui.vastseatrain.mode", Component.translatable(switch (settings.mode()) {
            case STRAIGHT -> "gui.vastseatrain.mode.straight";
            case CURVE -> "gui.vastseatrain.mode.curve";
        }));
    }

    private Component turnLabel() {
        return Component.translatable("gui.vastseatrain.curve_dir", Component.translatable(settings.curveLeft()
                ? "gui.vastseatrain.curve_left" : "gui.vastseatrain.curve_right"));
    }

    private Component bedLabel() {
        return Component.translatable("gui.vastseatrain.bed", settings.bed().getDisplayName());
    }

    private Component previewLabel() {
        return Component.translatable("gui.vastseatrain.preview",
                Component.translatable(settings.preview() ? "gui.vastseatrain.on" : "gui.vastseatrain.off"));
    }

    private Component styleLabel() {
        return Component.translatable("gui.vastseatrain.style", settings.style().getDisplayName());
    }

    private static <T extends Enum<T>> T cycle(T current, int direction) {
        T[] values = current.getDeclaringClass().getEnumConstants();
        return values[Math.floorMod(current.ordinal() + direction, values.length)];
    }

    private static int safeParse(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void clampLineMultiplierInput() {
        if (lineMultiplierBox == null) return;
        int value = safeParse(lineMultiplierBox.getValue(), settings.lineMultiplier());
        settings = settings.withLineMultiplier(value);
        lineMultiplierBox.setValue(Integer.toString(settings.lineMultiplier()));
    }

    /** 由“轨道模型”二级界面回调：选中的外观立即生效（只影响之后新铺设的轨道）。 */
    public void applyStyle(RailStyle style) {
        settings = settings.withStyle(style);
        if (styleButton != null) {
            styleButton.setMessage(styleLabel());
        }
    }

    @Override
    public void onClose() {
        commitActiveInput();
        settings.write(blueprint);
        if (minecraft != null && minecraft.getConnection() != null) {
            NetworkHandler.CHANNEL.sendToServer(new SaveBlueprintMessage(settings));
        }
        super.onClose();
    }

    private void commitActiveInput() {
        if (settings.isCurve()) {
            return;
        }
        if (lineAngleBox != null) {
            int angle = lineAngleBox.getValue().isEmpty() ? settings.lineAngle()
                    : Math.max(0, Math.min(89, safeParse(lineAngleBox.getValue(), settings.lineAngle())));
            settings = settings.withLineAngle(angle);
            lineAngleBox.setValue(Integer.toString(angle));
            if (lineAngleSlider != null) lineAngleSlider.setDegrees(angle);
        }
        if (lineMultiplierBox != null) {
            int value = lineMultiplierBox.getValue().isEmpty() ? settings.lineMultiplier()
                    : safeParse(lineMultiplierBox.getValue(), settings.lineMultiplier());
            settings = settings.withLineMultiplier(value);
            lineMultiplierBox.setValue(Integer.toString(settings.lineMultiplier()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        beginScaledContent(graphics);
        double scale = uiScale();
        mouseX = (int) (mouseX / scale);
        mouseY = (int) (mouseY / scale);

        int leftX = originX;
        int middleX = originX + panelWidth + panelGap;
        int rightX = originX + (panelWidth + panelGap) * 2;
        int top = originY + 18;
        int panelTop = originY;
        int panelBottom = originY + panelHeight;

        drawPanel(graphics, leftX, panelTop, panelBottom, panelWidth);
        drawPanel(graphics, middleX, panelTop, panelBottom, panelWidth);
        drawPanel(graphics, rightX, panelTop, panelBottom, panelWidth);

        int titleCenter = leftX + (panelWidth * 3 + panelGap * 2) / 2;
        graphics.drawCenteredString(font, title, titleCenter, originY + 6, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.track"),
                leftX + PADDING, top + PADDING, 0xFFD27F);
        if (settings.isCurve()) {
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_angle",
                            settings.curveAngle()), leftX + PADDING, top + 42, 0xA0A0A0);
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_radius",
                            settings.curveRadius()), leftX + PADDING, top + 70, 0xA0A0A0);
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_fit.label",
                            String.format(Locale.ROOT, "%.2f", settings.curveFitTolerance())),
                    leftX + PADDING, top + 132, 0xA0A0A0);
        } else {
            graphics.drawString(font, Component.translatable("gui.vastseatrain.angle_value",
                            settings.lineAngle()), leftX + PADDING, top + 77, 0xA0A0A0);
            if (settings.lineAngle() == 0) {
                graphics.drawString(font, Component.translatable("gui.vastseatrain.straight_length",
                                settings.lineMultiplier()), leftX + PADDING, top + 122, 0xA0A0A0);
            } else {
                graphics.drawString(font, Component.translatable("gui.vastseatrain.line_multiplier",
                                settings.lineMultiplier()), leftX + PADDING, top + 122, 0xA0A0A0);
                graphics.drawString(font, Component.literal(settings.lineGridSpanUnit() + " x "
                                + settings.lineMultiplier() + " = " + settings.lineGridSpan()),
                        leftX + PADDING, top + 134, 0xA0A0A0);
            }
        }
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.bed"),
                middleX + PADDING, top + PADDING, 0xFFD27F);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.title"),
                middleX + PADDING, top + 94, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.summary"),
                rightX + PADDING, top + PADDING, 0xFFD27F);

        int summaryX = rightX + PADDING;
        int summaryY = top + 24;
        int summaryWidth = panelWidth - PADDING * 2;
        int estimatedModelPieces = settings.isCurve()
                ? settings.estimatedCurvePieces() : settings.estimatedLinePieces();
        int warningLevel = VTConfig.performanceWarningLevel(estimatedModelPieces);
        if (settings.isCurve()) {
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.mode",
                    Component.translatable("gui.vastseatrain.mode.curve")), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("gui.vastseatrain.curve_radius", settings.curveRadius()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.curve_angle",
                    settings.curveAngle()), summaryX, summaryY, summaryWidth);
            boolean fitValid = settings.curveFitValid();
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.curve_fit",
                    String.format(Locale.ROOT, "%.3f", settings.curveFitDistance()),
                    String.format(Locale.ROOT, "%.2f", settings.curveFitTolerance()),
                    Component.translatable(fitValid ? "summary.vastseatrain.curve_fit_ok"
                            : "summary.vastseatrain.curve_fit_failed")),
                    summaryX, summaryY, summaryWidth, fitValid ? 0xFFFFFF : 0xFFFF5555);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.curve_length",
                    String.format(Locale.ROOT, "%.2f", VTConfig.curveArcLength(
                            settings.curveRadius(), settings.curveAngle()))), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, turnLabel(), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("gui.vastseatrain.curve_estimate", estimatedModelPieces),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawPerformanceWarning(graphics, summaryX, summaryY, summaryWidth,
                    warningLevel, estimatedModelPieces);
        } else if (settings.lineAngle() != 0) {
            DiagonalSolver.Solution solution = settings.lineSolution();
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.mode",
                    Component.translatable("gui.vastseatrain.mode.straight")), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.line_angle",
                    settings.lineAngle(), String.format(Locale.ROOT, "%.2f", solution.actualDegrees())),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.line_grid_offset",
                    solution.advance(), solution.lateral(), settings.lineTotalAdvance(),
                    settings.lineTotalLateral()), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.line_grid_length",
                    settings.lineGridSpanUnit(), settings.lineMultiplier(), settings.lineGridSpan()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.line_actual_length",
                    String.format(Locale.ROOT, "%.2f", settings.lineLength())),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.line_pieces",
                    estimatedModelPieces), summaryX, summaryY, summaryWidth);
            summaryY = drawPerformanceWarning(graphics, summaryX, summaryY, summaryWidth,
                    warningLevel, estimatedModelPieces);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.gauge",
                    settings.gauge().getDisplayName()), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.bed",
                    settings.bed().getDisplayName()), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, styleLabel(), summaryX, summaryY, summaryWidth);
        } else {
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.mode",
                    Component.translatable("gui.vastseatrain.mode.straight")), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.line_angle", 0, "0.00"),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.length", settings.lineMultiplier()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.line_pieces",
                    estimatedModelPieces), summaryX, summaryY, summaryWidth);
            summaryY = drawPerformanceWarning(graphics, summaryX, summaryY, summaryWidth,
                    warningLevel, estimatedModelPieces);
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.blocks", settings.lineMultiplier()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.width", TrackLayout.WIDTH_BLOCKS),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.gauge", settings.gauge().getDisplayName()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.bed", settings.bed().getDisplayName()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, styleLabel(), summaryX, summaryY, summaryWidth);
        }

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        endScaledContent(graphics);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth) {
        return drawWrapped(graphics, text, x, y, maxWidth, 0xFFFFFF);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            graphics.drawString(font, line, x, y, color);
            y += 10;
        }
        return y + 2;
    }

    private int drawPerformanceWarning(GuiGraphics graphics, int x, int y, int maxWidth,
                                       int warningLevel, int estimatedModelPieces) {
        if (warningLevel == 0) return y;
        boolean critical = warningLevel == 2;
        Component warning = Component.translatable(critical
                ? "gui.vastseatrain.performance_warn.critical"
                : "gui.vastseatrain.performance_warn.warning", estimatedModelPieces);
        return drawWrapped(graphics, warning, x, y, maxWidth, critical ? 0xFFFF5555 : 0xFFFFD34D);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int top, int bottom, int width) {
        graphics.fill(x, top, x + width, bottom, 0xC0101018);
        graphics.fill(x, top, x + width, top + 1, 0x80FFD27F);
        graphics.fill(x, bottom - 1, x + width, bottom, 0x80FFD27F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class CurveValueSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        CurveValueSlider(int x, int y, int width, int height, int min, int max,
                         int initial, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), ratio(initial, min, max));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private static double ratio(int value, int min, int max) {
            return max <= min ? 0.0D : (double) (Mth.clamp(value, min, max) - min) / (max - min);
        }

        private int current() {
            return Mth.clamp(min + (int) Math.round(value * (max - min)), min, max);
        }

        @Override protected void updateMessage() { setMessage(Component.empty()); }

        @Override
        protected void applyValue() {
            int selected = current();
            value = ratio(selected, min, max);
            onChange.accept(selected);
        }
    }

    /** Radius slider whose upper bound follows the selected sweep angle. */
    private static final class RadiusSlider extends AbstractSliderButton {
        private final IntConsumer onChange;
        private int angle;

        RadiusSlider(int x, int y, int width, int height, int angle, int radius, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), toRatio(radius, angle));
            this.angle = angle;
            this.onChange = onChange;
            updateMessage();
        }

        private static double toRatio(int radius, int angle) {
            int min = VTConfig.minCurveRadius(angle);
            int max = VTConfig.maxCurveRadius(angle);
            if (max <= min) return 0.0D;
            double logRange = Math.log((double) max / min);
            return logRange <= 0.0D ? 0.0D
                    : Math.log((double) VTConfig.clampCurveRadius(radius, angle) / min) / logRange;
        }

        private int currentRadius() {
            int min = VTConfig.minCurveRadius(angle);
            int max = VTConfig.maxCurveRadius(angle);
            if (max <= min) return min;
            double radius = min * Math.exp(value * Math.log((double) max / min));
            return VTConfig.clampCurveRadius((int) Math.round(radius), angle);
        }

        @Override protected void updateMessage() { setMessage(Component.empty()); }

        @Override
        protected void applyValue() {
            int radius = currentRadius();
            value = toRatio(radius, angle);
            onChange.accept(radius);
        }

        void setAngle(int angle, int radius) {
            this.angle = angle;
            value = toRatio(radius, angle);
            updateMessage();
        }
    }

}

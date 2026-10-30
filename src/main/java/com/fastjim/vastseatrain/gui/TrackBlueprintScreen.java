package com.fastjim.vastseatrain.gui;

import com.fastjim.vastseatrain.VTConfig;
import com.fastjim.vastseatrain.network.NetworkHandler;
import com.fastjim.vastseatrain.network.SaveBlueprintMessage;
import com.fastjim.vastseatrain.track.BlueprintSettings;
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

    private EditBox lengthBox;
    private LengthSlider lengthSlider;
    private RadiusSlider radiusSlider;
    private Button modeButton;
    private Button turnButton;
    private Button gaugeButton;
    private Button styleButton;

    /** 本次排版的左上角与面板宽度（窗口尺寸变化时重新计算）。 */
    private int originX;
    private int originY;
    private int panelWidth;
    private int panelGap;
    /** 三个面板的高度（独立于左栏控件行距，窗口矮时只压缩下方按钮）。 */
    private int panelHeight;

    public TrackBlueprintScreen(ItemStack blueprint) {
        super(Component.translatable("screen.vastseatrain.blueprint"), 380, 218);
        this.blueprint = blueprint;
        this.settings = BlueprintSettings.from(blueprint);
    }

    @Override
    protected void init() {
        layout();

        int top = originY + 18;
        int leftX = originX + PADDING;
        int middleX = originX + panelWidth + panelGap;
        int rightX = originX + (panelWidth + panelGap) * 2;
        int innerWidth = panelWidth - PADDING * 2;

        // 左栏：铺设模式（直道 / 90° 弯道）
        modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
            settings = settings.withCurveRadius(settings.isCurve() ? 0 : VTConfig.defaultRadius());
            if (settings.isCurve() && radiusSlider != null) {
                radiusSlider.setRadiusSilently(settings.curveRadius());
            }
            b.setMessage(modeLabel());
            railControlsVisible(settings.isCurve(), true);
        }).bounds(leftX, top + 20, innerWidth, 20).build());

        // 左栏：直道长度
        lengthSlider = addRenderableWidget(new LengthSlider(leftX, top + 62, innerWidth, 20,
                settings.length(), value -> {
                    settings = settings.withLength(value);
                    if (lengthBox != null && !lengthBox.isFocused()) {
                        lengthBox.setValue(Integer.toString(value));
                    }
                }));

        lengthBox = new EditBox(font, leftX, top + 104, Math.max(40, innerWidth - 20), 18,
                Component.translatable("gui.vastseatrain.length"));
        lengthBox.setValue(Integer.toString(settings.length()));
        lengthBox.setFilter(text -> text.isEmpty()
                || text.length() <= 3 && text.chars().allMatch(Character::isDigit));
        lengthBox.setResponder(text -> {
            if (text.isEmpty()) {
                return;
            }
            int value = BlueprintSettings.clampLength(Integer.parseInt(text));
            settings = settings.withLength(value);
            if (lengthSlider != null) {
                lengthSlider.setLengthSilently(value);
            }
        });
        addRenderableWidget(lengthBox);

        // 左栏（弯道模式）：半径滑条 + 左右转
        int curveRadius = settings.isCurve() ? settings.curveRadius() : VTConfig.defaultRadius();
        radiusSlider = addRenderableWidget(new RadiusSlider(leftX, top + 62, innerWidth, 20,
                curveRadius, value -> settings = settings.withCurveRadius(value)));
        radiusSlider.active = settings.isCurve();
        radiusSlider.visible = settings.isCurve();

        turnButton = addRenderableWidget(Button.builder(turnLabel(), b -> {
            settings = settings.withCurveLeft(!settings.curveLeft());
            b.setMessage(turnLabel());
        }).bounds(leftX, top + 104, innerWidth, 20).build());
        turnButton.active = settings.isCurve();
        turnButton.visible = settings.isCurve();

        gaugeButton = addRenderableWidget(Button.builder(gaugeLabel(), b -> {
            settings = settings.withGauge(settings.gauge().next());
            b.setMessage(gaugeLabel());
        }).bounds(leftX, top + 148, innerWidth, 20).build());

        railControlsVisible(settings.isCurve(), false);

        addRenderableWidget(Button.builder(bedLabel(), b -> {
            settings = settings.withBed(settings.bed().next());
            b.setMessage(bedLabel());
        }).bounds(middleX + PADDING, top + 20, innerWidth, 20).build());

        addRenderableWidget(Button.builder(previewLabel(), b -> {
            settings = settings.withPreview(!settings.preview());
            b.setMessage(previewLabel());
        }).bounds(middleX + PADDING, top + 62, innerWidth, 20).build());

        // 中栏：轨道模型（打开二级界面：左侧列表 + 右侧 3D 预览）
        styleButton = addRenderableWidget(Button.builder(styleLabel(), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new TrackStyleScreen(this, settings.style()));
            }
        }).bounds(middleX + PADDING, top + 104, innerWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.vastseatrain.apply"), b -> applyAndClose())
                .bounds(rightX + PADDING, top + 112, innerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(rightX + PADDING, top + 136, innerWidth, 20).build());
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
        panelHeight = Mth.clamp(logicalHeight - MARGIN * 2, 206, 250);
        originX = (logicalWidth - contentWidth) / 2;
        originY = (logicalHeight - panelHeight) / 2;
    }

    /**
     * 直道控件（长度）与弯道控件（半径、转向）互斥显示。
     *
     * @param curve 当前是否弯道模式
     * @param keep  是否保留切换前的输入值（切换到弯道时把长度框内容留着，切回来不必重输）
     */
    private void railControlsVisible(boolean curve, boolean keep) {
        if (lengthSlider != null) {
            lengthSlider.visible = !curve;
            lengthSlider.active = !curve;
        }
        if (lengthBox != null) {
            lengthBox.visible = !curve;
            lengthBox.setEditable(!curve);
            if (!keep) {
                lengthBox.setFocused(false);
            }
        }
        if (radiusSlider != null) {
            radiusSlider.visible = curve;
            radiusSlider.active = curve;
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
        return Component.translatable("gui.vastseatrain.mode", Component.translatable(settings.isCurve()
                ? "gui.vastseatrain.mode.curve" : "gui.vastseatrain.mode.straight"));
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

    /** 由“轨道模型”二级界面回调：选中的外观立即生效（只影响之后新铺设的轨道）。 */
    public void applyStyle(RailStyle style) {
        settings = settings.withStyle(style);
        if (styleButton != null) {
            styleButton.setMessage(styleLabel());
        }
    }

    private void applyAndClose() {
        settings.write(blueprint);
        if (minecraft != null && minecraft.getConnection() != null) {
            NetworkHandler.CHANNEL.sendToServer(new SaveBlueprintMessage(settings));
        }
        onClose();
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
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_radius", settings.curveRadius()),
                    leftX + PADDING, top + 52, 0xA0A0A0);
        } else {
            graphics.drawString(font, Component.translatable("gui.vastseatrain.length"),
                    leftX + PADDING, top + 52, 0xA0A0A0);
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
        if (settings.isCurve()) {
            summaryY = drawWrapped(graphics, Component.translatable("summary.vastseatrain.length",
                    Component.translatable("gui.vastseatrain.mode.curve")), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("gui.vastseatrain.curve_radius", settings.curveRadius()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics, turnLabel(), summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("gui.vastseatrain.curve_estimate", settings.estimatedCurvePieces()),
                    summaryX, summaryY, summaryWidth);
        } else {
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.length", settings.length()),
                    summaryX, summaryY, summaryWidth);
            summaryY = drawWrapped(graphics,
                    Component.translatable("summary.vastseatrain.blocks", settings.totalBlocks()),
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

        // 半径过大时给出性能提示：模型实例数直接影响铺设耗时与渲染负担
        if (settings.isCurve() && VTConfig.needsWarning(settings.curveRadius())) {
            drawWrapped(graphics, Component.translatable("gui.vastseatrain.curve_warn",
                    VTConfig.warnRadius(), settings.estimatedCurvePieces()), summaryX, summaryY,
                    summaryWidth, 0xFF6B6B);
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

    private static void drawPanel(GuiGraphics graphics, int x, int top, int bottom, int width) {
        graphics.fill(x, top, x + width, bottom, 0xC0101018);
        graphics.fill(x, top, x + width, top + 1, 0x80FFD27F);
        graphics.fill(x, bottom - 1, x + width, bottom, 0x80FFD27F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 长度滑条：1 ~ {@link BlueprintSettings#MAX_LENGTH} 段。 */
    private static final class LengthSlider extends AbstractSliderButton {
        private final IntConsumer onChange;

        LengthSlider(int x, int y, int width, int height, int length, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), toRatio(length));
            this.onChange = onChange;
        }

        private static double toRatio(int length) {
            return (double) (BlueprintSettings.clampLength(length) - 1) / (BlueprintSettings.MAX_LENGTH - 1);
        }

        private int currentLength() {
            return 1 + (int) Math.round(value * (BlueprintSettings.MAX_LENGTH - 1));
        }

        void setLengthSilently(int length) {
            value = Mth.clamp(toRatio(length), 0.0D, 1.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.vastseatrain.length_value", currentLength()));
        }

        @Override
        protected void applyValue() {
            int length = BlueprintSettings.clampLength(currentLength());
            value = toRatio(length);
            onChange.accept(length);
        }
    }

    /**
     * 弯道半径滑条：{@code minRadius} ~ {@code maxRadius}（来自配置）。
     * <p>
     * 滑条是线性映射，半径很大时小半径端会挤在一起；切换时用 {@link #setRadiusSilently} 保持显示正确。
     */
    private static final class RadiusSlider extends AbstractSliderButton {
        private final IntConsumer onChange;

        RadiusSlider(int x, int y, int width, int height, int radius, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), toRatio(radius));
            this.onChange = onChange;
        }

        private static double toRatio(int radius) {
            int min = VTConfig.minRadius();
            int max = VTConfig.maxRadius();
            int clamped = VTConfig.clampRadius(radius);
            return max <= min ? 0.0D : (double) (clamped - min) / (max - min);
        }

        private int currentRadius() {
            int min = VTConfig.minRadius();
            int max = VTConfig.maxRadius();
            return VTConfig.clampRadius(min + (int) Math.round(value * (max - min)));
        }

        void setRadiusSilently(int radius) {
            value = Mth.clamp(toRatio(radius), 0.0D, 1.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.vastseatrain.curve_radius", currentRadius()));
        }

        @Override
        protected void applyValue() {
            int radius = currentRadius();
            value = toRatio(radius);
            onChange.accept(radius);
        }
    }
}

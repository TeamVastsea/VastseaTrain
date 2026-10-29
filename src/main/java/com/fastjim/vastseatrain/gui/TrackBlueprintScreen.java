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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.function.IntConsumer;

/**
 * 轨道蓝图三栏界面：左栏轨道参数、中栏道床与预览、右栏实时摘要。
 * <p>
 * 界面按“设计尺寸 560 × 210”排版，渲染和点击都会先缩放到当前窗口：
 * 窗口足够大时 1:1 显示，窗口较小（例如 960×540）时整体等比缩小并居中，
 * 三栏和按钮永远完整落在屏幕内，不再被裁切。
 */
public class TrackBlueprintScreen extends Screen {
    private static final int DESIGN_WIDTH = 560;
    private static final int DESIGN_HEIGHT = 210;
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_GAP = 8;
    private static final int PADDING = 8;

    private final ItemStack blueprint;
    private BlueprintSettings settings;

    private EditBox lengthBox;
    private LengthSlider lengthSlider;
    private RadiusSlider radiusSlider;
    private Button modeButton;
    private Button turnButton;
    private Button gaugeButton;
    private Button styleButton;

    /** 当前缩放倍数（像素 / 设计单位），在 {@link #init()} 中按窗口尺寸计算。 */
    private float scale = 1.0F;
    /** 缩放后左上角的屏幕坐标。 */
    private float originX;
    private float originY;

    public TrackBlueprintScreen(ItemStack blueprint) {
        super(Component.translatable("screen.vastseatrain.blueprint"));
        this.blueprint = blueprint;
        this.settings = BlueprintSettings.from(blueprint);
    }

    @Override
    protected void init() {
        scale = computeScale();
        originX = (width - DESIGN_WIDTH * scale) / 2.0F;
        originY = (height - DESIGN_HEIGHT * scale) / 2.0F;

        int top = 26;
        int leftX = PADDING + PADDING;
        int middleX = PADDING + PANEL_WIDTH + PANEL_GAP + PADDING;
        int rightX = PADDING + (PANEL_WIDTH + PANEL_GAP) * 2 + PADDING;
        int innerWidth = PANEL_WIDTH - PADDING * 2;

        // 左栏：铺设模式（直道 / 90° 弯道）
        modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
            settings = settings.withCurveRadius(settings.isCurve() ? 0 : VTConfig.defaultRadius());
            if (settings.isCurve() && radiusSlider != null) {
                radiusSlider.setRadiusSilently(settings.curveRadius());
            }
            b.setMessage(modeLabel());
            railControlsVisible(settings.isCurve(), true);
        }).bounds(sx(leftX), sy(top + 20), si(innerWidth), si(20)).build());

        // 左栏：直道长度
        lengthSlider = addRenderableWidget(new LengthSlider(sx(leftX), sy(top + 62), si(innerWidth), si(20),
                settings.length(), value -> {
                    settings = settings.withLength(value);
                    if (lengthBox != null && !lengthBox.isFocused()) {
                        lengthBox.setValue(Integer.toString(value));
                    }
                }));

        lengthBox = new EditBox(font, sx(leftX), sy(top + 104), si(62), si(18),
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
        radiusSlider = addRenderableWidget(new RadiusSlider(sx(leftX), sy(top + 62), si(innerWidth), si(20),
                curveRadius, value -> settings = settings.withCurveRadius(value)));
        radiusSlider.active = settings.isCurve();
        radiusSlider.visible = settings.isCurve();

        turnButton = addRenderableWidget(Button.builder(turnLabel(), b -> {
            settings = settings.withCurveLeft(!settings.curveLeft());
            b.setMessage(turnLabel());
        }).bounds(sx(leftX), sy(top + 104), si(innerWidth), si(20)).build());
        turnButton.active = settings.isCurve();
        turnButton.visible = settings.isCurve();

        gaugeButton = addRenderableWidget(Button.builder(gaugeLabel(), b -> {
            settings = settings.withGauge(settings.gauge().next());
            b.setMessage(gaugeLabel());
        }).bounds(sx(leftX), sy(top + 148), si(innerWidth), si(20)).build());

        railControlsVisible(settings.isCurve(), false);

        addRenderableWidget(Button.builder(bedLabel(), b -> {
            settings = settings.withBed(settings.bed().next());
            b.setMessage(bedLabel());
        }).bounds(sx(middleX), sy(top + 20), si(innerWidth), si(20)).build());

        addRenderableWidget(Button.builder(previewLabel(), b -> {
            settings = settings.withPreview(!settings.preview());
            b.setMessage(previewLabel());
        }).bounds(sx(middleX), sy(top + 62), si(innerWidth), si(20)).build());

        // 中栏：轨道模型（打开二级界面：左侧列表 + 右侧 3D 预览）
        styleButton = addRenderableWidget(Button.builder(styleLabel(), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new TrackStyleScreen(this, settings.style()));
            }
        }).bounds(sx(middleX), sy(top + 104), si(innerWidth), si(20)).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.vastseatrain.apply"), b -> applyAndClose())
                .bounds(sx(rightX), sy(top + 112), si(innerWidth), si(20)).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(sx(rightX), sy(top + 136), si(innerWidth), si(20)).build());
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

    /** 屏幕放不下设计尺寸时整体等比缩小；最小不低于 1/2。 */
    private float computeScale() {
        float fitWidth = (width - 8) / (float) DESIGN_WIDTH;
        float fitHeight = (height - 8) / (float) DESIGN_HEIGHT;
        return Math.max(0.5F, Math.min(1.0F, Math.min(fitWidth, fitHeight)));
    }

    /**
     * 设计坐标 → 屏幕 X。
     * <p>
     * 注意：控件本身必须放在<b>设计坐标系</b>里创建（见 {@link #init()}），
     * 由 {@link #render} 的 pose 统一缩放；点击则由 {@link #mouseClicked} 等把屏幕坐标反算回设计坐标。
     * 两者都只做一次缩放，才不会出现“看起来在这里、点起来在那里”。
     */
    private int sx(int designX) {
        return designX;
    }

    private int sy(int designY) {
        return designY;
    }

    private int si(int designSize) {
        return designSize;
    }

    /** 屏幕 X → 设计 X。 */
    private int designX(double screenX) {
        return Math.round((float) ((screenX - originX) / scale));
    }

    /** 屏幕 Y → 设计 Y。 */
    private int designY(double screenY) {
        return Math.round((float) ((screenY - originY) / scale));
    }

    private void pushDesignPose(GuiGraphics graphics) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0.0F);
        pose.scale(scale, scale, 1.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(designX(mouseX), designY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(designX(mouseX), designY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(designX(mouseX), designY(mouseY), button,
                dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(designX(mouseX), designY(mouseY), delta);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(designX(mouseX), designY(mouseY));
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

        pushDesignPose(graphics);

        int panelLeft = PADDING;
        int top = 26;
        int leftX = panelLeft + PADDING;
        int middleX = panelLeft + PANEL_WIDTH + PANEL_GAP;
        int rightX = middleX + PANEL_WIDTH + PANEL_GAP;

        int panelBottom = DESIGN_HEIGHT;
        drawPanel(graphics, panelLeft, top, panelBottom);
        drawPanel(graphics, middleX, top, panelBottom);
        drawPanel(graphics, rightX, top, panelBottom);

        graphics.drawCenteredString(font, title, DESIGN_WIDTH / 2, 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.track"), leftX, top + PADDING, 0xFFD27F);
        if (settings.isCurve()) {
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_radius", settings.curveRadius()),
                    leftX, top + 52, 0xA0A0A0);
        } else {
            graphics.drawString(font, Component.translatable("gui.vastseatrain.length"), leftX, top + 52, 0xA0A0A0);
        }
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.bed"), middleX + PADDING, top + PADDING, 0xFFD27F);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.title"), middleX + PADDING, top + 94, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.summary"), rightX + PADDING, top + PADDING, 0xFFD27F);

        int summaryX = rightX + PADDING;
        int summaryY = top + 24;
        if (settings.isCurve()) {
            graphics.drawString(font, Component.translatable("summary.vastseatrain.length",
                    Component.translatable("gui.vastseatrain.mode.curve")), summaryX, summaryY, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_radius", settings.curveRadius()),
                    summaryX, summaryY + 12, 0xFFFFFF);
            graphics.drawString(font, turnLabel(), summaryX, summaryY + 24, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_estimate", settings.totalBlocks()),
                    summaryX, summaryY + 36, 0xFFFFFF);
        } else {
            graphics.drawString(font, Component.translatable("summary.vastseatrain.length", settings.length()), summaryX, summaryY, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("summary.vastseatrain.blocks", settings.totalBlocks()), summaryX, summaryY + 12, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("summary.vastseatrain.width", TrackLayout.WIDTH_BLOCKS), summaryX, summaryY + 24, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("summary.vastseatrain.gauge", settings.gauge().getDisplayName()), summaryX, summaryY + 36, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("summary.vastseatrain.bed", settings.bed().getDisplayName()), summaryX, summaryY + 48, 0xFFFFFF);
            graphics.drawString(font, styleLabel(), summaryX, summaryY + 60, 0xFFFFFF);
        }

        // 半径过大时给出性能提示：条带数 = 每格一条模型，数量直接影响铺设耗时与渲染负担
        if (settings.isCurve() && VTConfig.needsWarning(settings.curveRadius())) {
            graphics.drawString(font, Component.translatable("gui.vastseatrain.curve_warn",
                    VTConfig.warnRadius(), settings.totalBlocks()), summaryX, summaryY + 62, 0xFF6B6B);
        }

        // 控件坐标为设计坐标，与文字同一套 pose，确保“画在哪就在哪点得中”
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, designX(mouseX), designY(mouseY), partialTick);
        }
        graphics.pose().popPose();
    }

    private static void drawPanel(GuiGraphics graphics, int x, int top, int bottom) {
        graphics.fill(x, top, x + PANEL_WIDTH, bottom, 0xC0101018);
        graphics.fill(x, top, x + PANEL_WIDTH, top + 1, 0x80FFD27F);
        graphics.fill(x, bottom - 1, x + PANEL_WIDTH, bottom, 0x80FFD27F);
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

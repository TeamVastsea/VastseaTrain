package com.fastjim.vastseatrain.gui;

import com.fastjim.vastseatrain.network.NetworkHandler;
import com.fastjim.vastseatrain.network.SaveBlueprintMessage;
import com.fastjim.vastseatrain.track.BlueprintSettings;
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

        lengthSlider = addRenderableWidget(new LengthSlider(sx(leftX), sy(top + 20), si(innerWidth), si(20),
                settings.length(), value -> {
                    settings = settings.withLength(value);
                    if (lengthBox != null && !lengthBox.isFocused()) {
                        lengthBox.setValue(Integer.toString(value));
                    }
                }));

        lengthBox = new EditBox(font, sx(leftX), sy(top + 62), si(62), si(18),
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

        addRenderableWidget(Button.builder(gaugeLabel(), b -> {
            settings = settings.withGauge(settings.gauge().next());
            b.setMessage(gaugeLabel());
        }).bounds(sx(leftX), sy(top + 112), si(innerWidth), si(20)).build());

        addRenderableWidget(Button.builder(bedLabel(), b -> {
            settings = settings.withBed(settings.bed().next());
            b.setMessage(bedLabel());
        }).bounds(sx(middleX), sy(top + 20), si(innerWidth), si(20)).build());

        addRenderableWidget(Button.builder(previewLabel(), b -> {
            settings = settings.withPreview(!settings.preview());
            b.setMessage(previewLabel());
        }).bounds(sx(middleX), sy(top + 62), si(innerWidth), si(20)).build());

        Button curveButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.vastseatrain.curve_soon"), b -> {
                }).bounds(sx(middleX), sy(top + 104), si(innerWidth), si(19)).build());
        curveButton.active = false;

        addRenderableWidget(Button.builder(Component.translatable("gui.vastseatrain.apply"), b -> applyAndClose())
                .bounds(sx(rightX), sy(top + 112), si(innerWidth), si(20)).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(sx(rightX), sy(top + 136), si(innerWidth), si(20)).build());
    }

    /** 屏幕放不下设计尺寸时整体等比缩小；最小不低于 1/2。 */
    private float computeScale() {
        float fitWidth = (width - 8) / (float) DESIGN_WIDTH;
        float fitHeight = (height - 8) / (float) DESIGN_HEIGHT;
        return Math.max(0.5F, Math.min(1.0F, Math.min(fitWidth, fitHeight)));
    }

    private int sx(int designX) {
        return Math.round(originX + designX * scale);
    }

    private int sy(int designY) {
        return Math.round(originY + designY * scale);
    }

    private int si(int designSize) {
        return Math.max(1, Math.round(designSize * scale));
    }

    private Component gaugeLabel() {
        return Component.translatable("gui.vastseatrain.gauge", settings.gauge().getDisplayName());
    }

    private Component bedLabel() {
        return Component.translatable("gui.vastseatrain.bed", settings.bed().getDisplayName());
    }

    private Component previewLabel() {
        return Component.translatable("gui.vastseatrain.preview",
                Component.translatable(settings.preview() ? "gui.vastseatrain.on" : "gui.vastseatrain.off"));
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

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0.0F);
        pose.scale(scale, scale, 1.0F);

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
        graphics.drawString(font, Component.translatable("gui.vastseatrain.length"), leftX, top + 52, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.bed"), middleX + PADDING, top + PADDING, 0xFFD27F);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.summary"), rightX + PADDING, top + PADDING, 0xFFD27F);

        int sx = rightX + PADDING;
        int sy = top + 24;
        graphics.drawString(font, Component.translatable("summary.vastseatrain.length", settings.length()), sx, sy, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.blocks", settings.totalBlocks()), sx, sy + 12, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.width", TrackLayout.WIDTH_BLOCKS), sx, sy + 24, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.gauge", settings.gauge().getDisplayName()), sx, sy + 36, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.bed", settings.bed().getDisplayName()), sx, sy + 48, 0xFFFFFF);

        // 控件已按屏幕坐标定位；设到设计坐标系下，由上面的 pose 缩放回屏幕
        pose.scale(1.0F / scale, 1.0F / scale, 1.0F);
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        pose.popPose();
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
}

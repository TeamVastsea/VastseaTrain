package com.fastjim.vastseatrain.gui;

import com.fastjim.vastseatrain.network.NetworkHandler;
import com.fastjim.vastseatrain.network.SaveBlueprintMessage;
import com.fastjim.vastseatrain.track.BlueprintSettings;
import com.fastjim.vastseatrain.track.TrackLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntConsumer;

/**
 * 轨道蓝图三栏界面：左栏轨道参数、中栏道床与预览、右栏实时摘要。
 * 界面只在客户端存在，保存时通过 {@link SaveBlueprintMessage} 回写物品数据。
 */
public class TrackBlueprintScreen extends Screen {
    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_GAP = 8;
    private static final int PADDING = 8;
    private static final int PANEL_HEIGHT = 150;

    private final ItemStack blueprint;
    private BlueprintSettings settings;

    private EditBox lengthBox;
    private LengthSlider lengthSlider;

    public TrackBlueprintScreen(ItemStack blueprint) {
        super(Component.translatable("screen.vastseatrain.blueprint"));
        this.blueprint = blueprint;
        this.settings = BlueprintSettings.from(blueprint);
    }

    private int panelLeft() {
        return Math.max(PADDING, (width - (PANEL_WIDTH * 3 + PANEL_GAP * 2)) / 2);
    }

    private int panelTop() {
        return Math.max(28, (height - PANEL_HEIGHT) / 2);
    }

    @Override
    protected void init() {
        int panelLeft = panelLeft();
        int top = panelTop();
        int leftX = panelLeft + PADDING;
        int middleX = panelLeft + PANEL_WIDTH + PANEL_GAP + PADDING;
        int rightX = panelLeft + (PANEL_WIDTH + PANEL_GAP) * 2 + PADDING;
        int innerWidth = PANEL_WIDTH - PADDING * 2;

        lengthSlider = addRenderableWidget(new LengthSlider(leftX, top + 22, innerWidth, 20,
                settings.length(), value -> {
                    settings = settings.withLength(value);
                    if (lengthBox != null && !lengthBox.isFocused()) {
                        lengthBox.setValue(Integer.toString(value));
                    }
                }));

        lengthBox = new EditBox(font, leftX, top + 66, 62, 18, Component.translatable("gui.vastseatrain.length"));
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
        }).bounds(leftX, top + 112, innerWidth, 20).build());

        addRenderableWidget(Button.builder(bedLabel(), b -> {
            settings = settings.withBed(settings.bed().next());
            b.setMessage(bedLabel());
        }).bounds(middleX, top + 22, innerWidth, 20).build());

        addRenderableWidget(Button.builder(previewLabel(), b -> {
            settings = settings.withPreview(!settings.preview());
            b.setMessage(previewLabel());
        }).bounds(middleX, top + 66, innerWidth, 20).build());

        Button curveButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.vastseatrain.curve_soon"), b -> {
                }).bounds(middleX, top + 112, innerWidth, 20).build());
        curveButton.active = false;

        addRenderableWidget(Button.builder(Component.translatable("gui.vastseatrain.apply"), b -> applyAndClose())
                .bounds(rightX, top + 64, innerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(rightX, top + 90, innerWidth, 20).build());
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
        int panelLeft = panelLeft();
        int top = panelTop();
        int leftX = panelLeft + PADDING;
        int middleX = panelLeft + PANEL_WIDTH + PANEL_GAP;
        int rightX = middleX + PANEL_WIDTH + PANEL_GAP;

        drawPanel(graphics, panelLeft, top);
        drawPanel(graphics, middleX, top);
        drawPanel(graphics, rightX, top);

        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.track"), leftX, top + PADDING, 0xFFD27F);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.length"), leftX, top + 56, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.bed"), middleX + PADDING, top + PADDING, 0xFFD27F);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.section.summary"), rightX + PADDING, top + PADDING, 0xFFD27F);

        int sx = rightX + PADDING;
        int sy = top + 24;
        graphics.drawString(font, Component.translatable("summary.vastseatrain.length", settings.length()), sx, sy, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.blocks", settings.totalBlocks()), sx, sy + 12, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.width", TrackLayout.WIDTH_BLOCKS), sx, sy + 24, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.gauge", settings.gauge().getDisplayName()), sx, sy + 36, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("summary.vastseatrain.bed", settings.bed().getDisplayName()), sx, sy + 48, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xC0101018);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 1, 0x80FFD27F);
        graphics.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0x80FFD27F);
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

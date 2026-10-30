package com.fastjim.vastseatrain.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scales a screen only when its logical minimum layout does not fit the active GUI viewport. */
abstract class AdaptiveScreen extends Screen {
    private final int minWidth;
    private final int minHeight;

    protected AdaptiveScreen(Component title, int minWidth, int minHeight) {
        super(title);
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    protected final double uiScale() {
        return Math.min(1.0D, Math.min((double) width / minWidth, (double) height / minHeight));
    }

    protected final int logicalWidth() {
        return (int) Math.ceil(width / uiScale());
    }

    protected final int logicalHeight() {
        return (int) Math.ceil(height / uiScale());
    }

    protected final void beginScaledContent(GuiGraphics graphics) {
        float scale = (float) uiScale();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
    }

    protected final void endScaledContent(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    protected final double logicalCoordinate(double value) {
        return value / uiScale();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(logicalCoordinate(mouseX), logicalCoordinate(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(logicalCoordinate(mouseX), logicalCoordinate(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double scale = uiScale();
        return super.mouseDragged(mouseX / scale, mouseY / scale, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(logicalCoordinate(mouseX), logicalCoordinate(mouseY), delta);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(logicalCoordinate(mouseX), logicalCoordinate(mouseY));
    }
}

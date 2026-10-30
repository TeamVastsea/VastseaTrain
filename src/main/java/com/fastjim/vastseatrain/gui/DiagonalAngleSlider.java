package com.fastjim.vastseatrain.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;

/** Compact integer-degree slider with its value rendered below the control. */
final class DiagonalAngleSlider extends AbstractSliderButton {
    private final IntConsumer onChange;

    DiagonalAngleSlider(int x, int y, int width, int height, int degrees, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), Math.max(0, Math.min(89, degrees)) / 89.0D);
        this.onChange = onChange;
        updateMessage();
    }

    int degrees() {
        return (int) Math.round(value * 89.0D);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.empty());
    }

    @Override
    protected void applyValue() {
        int degrees = degrees();
        value = degrees / 89.0D;
        updateMessage();
        onChange.accept(degrees);
    }

    void setDegrees(int degrees) {
        value = Math.max(0, Math.min(89, degrees)) / 89.0D;
        updateMessage();
    }
}

package com.fastjim.vastseatrain.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/** Button whose left and right clicks cycle a choice in opposite directions. */
final class DirectionalButton extends Button {
    private final BiConsumer<DirectionalButton, Integer> action;
    private int direction = 1;

    DirectionalButton(int x, int y, int width, int height, Component message,
                      BiConsumer<DirectionalButton, Integer> action) {
        super(x, y, width, height, message, button -> { }, DEFAULT_NARRATION);
        this.action = action;
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        direction = button == 0 ? 1 : -1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        direction = 1;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onPress() {
        action.accept(this, direction);
    }
}

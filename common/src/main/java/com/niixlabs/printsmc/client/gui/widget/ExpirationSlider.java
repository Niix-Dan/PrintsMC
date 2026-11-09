package com.niixlabs.printsmc.client.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ExpirationSlider extends AbstractSliderButton {
    private static final String[] DURATIONS = {"24h", "7d", "30d"};
    private boolean dragging;

    public ExpirationSlider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), 0.5);
        this.updateMessage();
    }

    public String getSelectedDuration() {
        return DURATIONS[selectedIndex()];
    }

    private int selectedIndex() {
        int maxIndex = DURATIONS.length - 1;
        return Mth.clamp((int) Math.round(this.value * maxIndex), 0, maxIndex);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.dragging = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (this.dragging) {
            super.onDrag(mouseX, mouseY, dragX, dragY);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.dragging) {
            return false;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.dragging) {
            return false;
        }
        this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.translatable("gui.printsmc.slider.expires", getSelectedDuration()));
    }

    @Override
    protected void applyValue() {
        this.updateMessage();
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        this.value = (double) selectedIndex() / (DURATIONS.length - 1);
        this.updateMessage();
    }
}
package com.niixlabs.printsmc.client.gui.widget;

import com.niixlabs.printsmc.client.gui.util.GuiScale;
import com.niixlabs.printsmc.config.ConfigManager;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ScaleSlider extends AbstractSliderButton {
    private final Runnable onScaleChanged;

    public ScaleSlider(int x, int y, int width, int height, Runnable onScaleChanged) {
        super(x, y, width, height, Component.empty(), computeInitialValue());
        this.onScaleChanged = onScaleChanged;
        this.updateMessage();
    }

    private static double computeInitialValue() {
        int maxScale = GuiScale.maxSupportedScale(Minecraft.getInstance());
        if (maxScale <= 0) {
            return 0.0;
        }
        int current = Mth.clamp(ModConfig.customGuiScale, 0, maxScale);
        return (double) current / maxScale;
    }

    private int currentStep() {
        int maxScale = GuiScale.maxSupportedScale(Minecraft.getInstance());
        return (int) Math.round(this.value * maxScale);
    }

    @Override
    protected void updateMessage() {
        int current = currentStep();
        if (current == 0) {
            this.setMessage(Component.translatable("gui.printsmc.scale_auto"));
        } else {
            this.setMessage(Component.translatable("gui.printsmc.scale_label", current));
        }
    }

    @Override
    protected void applyValue() {
        this.updateMessage();
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        int maxScale = GuiScale.maxSupportedScale(Minecraft.getInstance());
        int current = currentStep();

        this.value = maxScale > 0 ? (double) current / maxScale : 0.0;
        this.updateMessage();

        if (ModConfig.customGuiScale != current) {
            ConfigManager.updateAndSave(ModConfig.class, "customGuiScale", current);
            this.onScaleChanged.run();
        }
    }
}
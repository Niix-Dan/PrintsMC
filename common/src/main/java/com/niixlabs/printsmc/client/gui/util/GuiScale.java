package com.niixlabs.printsmc.client.gui.util;

import com.mojang.blaze3d.platform.Window;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class GuiScale {
    public static final int MAX_CUSTOM_SCALE = 8;

    private GuiScale() {}

    public static double targetScale(Minecraft minecraft) {
        double maxSafeScale = computeMaxSafeScale(minecraft.getWindow());
        double vanillaScale = minecraft.getWindow().getGuiScale();

        if (ModConfig.customGuiScale == 0) {
            return Math.min(vanillaScale, maxSafeScale);
        }
        return Mth.clamp((double) ModConfig.customGuiScale, 1.0, maxSafeScale);
    }

    public static double scaleFactor(Minecraft minecraft) {
        return minecraft.getWindow().getGuiScale() / targetScale(minecraft);
    }

    public static float scaleModifier(Minecraft minecraft) {
        return (float) (targetScale(minecraft) / minecraft.getWindow().getGuiScale());
    }

    public static int maxSupportedScale(Minecraft minecraft) {
        int maxSafeScale = (int) computeMaxSafeScale(minecraft.getWindow());
        return Math.min(maxSafeScale, MAX_CUSTOM_SCALE);
    }

    public static List<Integer> supportedScaleOptions(Minecraft minecraft) {
        int maxScale = maxSupportedScale(minecraft);
        List<Integer> options = new ArrayList<>(maxScale + 1);
        for (int scale = 0; scale <= maxScale; scale++) {
            options.add(scale);
        }
        return options;
    }

    private static double computeMaxSafeScale(Window window) {
        double maxScaleX = (double) window.getScreenWidth() / ModConfig.scaleMinVirtualWidth;
        double maxScaleY = (double) window.getScreenHeight() / ModConfig.scaleMinVirtualHeight;
        return Math.max(1.0, Math.floor(Math.min(maxScaleX, maxScaleY)));
    }
}
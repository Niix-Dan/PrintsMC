package com.niixlabs.printsmc.mixin;

import com.niixlabs.printsmc.client.gui.screen.GalleryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private static final String PRINTSMC_PREFIX = "printsmc://view/";

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void printsmc$onChatComponentClick(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null) {
            return;
        }

        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent != null && clickEvent.getAction() == ClickEvent.Action.OPEN_FILE) {
            String value = clickEvent.getValue();
            if (value != null && value.startsWith(PRINTSMC_PREFIX)) {
                String fileName = new File(value.substring(PRINTSMC_PREFIX.length())).getName();
                Minecraft mc = Minecraft.getInstance();
                File screenshotsDir = new File(mc.gameDirectory, Screenshot.SCREENSHOT_DIR);
                File targetFile = new File(screenshotsDir, fileName);

                mc.setScreen(new GalleryScreen(null, targetFile));
                cir.setReturnValue(true);
            }
        }
    }
}
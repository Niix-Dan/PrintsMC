package com.niixlabs.printsmc.mixin;

import com.niixlabs.printsmc.client.KeyBinds;
import com.niixlabs.printsmc.client.gui.screen.GalleryScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void PrintsMC$onTick(CallbackInfo ci) {
        while (KeyBinds.OPEN_GALLERY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new GalleryScreen());
            }
        }
    }
}
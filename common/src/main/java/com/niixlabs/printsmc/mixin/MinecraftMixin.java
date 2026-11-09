package com.niixlabs.printsmc.mixin;

import com.niixlabs.printsmc.client.KeyBinds;
import com.niixlabs.printsmc.client.gui.screen.GalleryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @Shadow
    public abstract void setScreen(@Nullable Screen guiScreen);

    @Inject(method = "tick", at = @At("TAIL"))
    private void printsmc$onTick(CallbackInfo ci) {
        while (KeyBinds.OPEN_GALLERY.consumeClick()) {
            if (this.screen == null) {
                this.setScreen(new GalleryScreen());
            }
        }
    }
}
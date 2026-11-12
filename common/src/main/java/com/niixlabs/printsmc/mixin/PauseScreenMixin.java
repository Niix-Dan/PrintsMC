package com.niixlabs.printsmc.mixin;

import com.niixlabs.printsmc.client.gui.screen.GalleryScreen;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void printsmc$addGalleryButton(CallbackInfo ci) {

        if (!ModConfig.renderPauseMenuWidget) return;

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.printsmc.gallery_button"), b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new GalleryScreen(this));
                    }
                }).bounds(this.width - 105, this.height - 25, 100, 20).build()
        );
    }
}
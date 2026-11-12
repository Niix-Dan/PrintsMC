package com.niixlabs.printsmc.mixin;

import com.niixlabs.printsmc.client.gui.screen.GalleryScreen;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void printsmc$addGalleryButton(CallbackInfo ci) {

        if (!ModConfig.renderTitleScreenWidget) return;

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.printsmc.gallery_button"), b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new GalleryScreen(this));
                    }
                }).bounds(this.width - 105, this.height - 32, 100, 20).build()
        );
    }
}
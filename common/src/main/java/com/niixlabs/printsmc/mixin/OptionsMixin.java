package com.niixlabs.printsmc.mixin;

import com.niixlabs.printsmc.client.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Arrays;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Mutable
    @Shadow
    @Final
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void printsmc$registerKeybind(Minecraft minecraft, File file, CallbackInfo ci) {
        for (KeyMapping mapping : this.keyMappings) {
            if (mapping == KeyBinds.OPEN_GALLERY) {
                return;
            }
        }

        KeyMapping[] updated = Arrays.copyOf(this.keyMappings, this.keyMappings.length + 1);
        updated[updated.length - 1] = KeyBinds.OPEN_GALLERY;
        this.keyMappings = updated;
    }
}
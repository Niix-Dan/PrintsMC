package com.niixlabs.printsmc.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    @Unique
    private static final String PRINTSMC_PREFIX = "printsmc://view/";

    @ModifyVariable(method = "_grab", at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> printsmc$wrapMessageConsumer(Consumer<Component> originalConsumer) {
        return message -> originalConsumer.accept(printsmc$decorateScreenshotMessage(message));
    }

    @Unique
    private static Component printsmc$decorateScreenshotMessage(Component original) {
        if (!(original.getContents() instanceof TranslatableContents contents)) {
            return original;
        }
        if (!"screenshot.success".equals(contents.getKey()) || contents.getArgs().length == 0) {
            return original;
        }

        Object firstArg = contents.getArgs()[0];
        if (!(firstArg instanceof Component fileComponent)) {
            return original;
        }

        ClickEvent oldClick = fileComponent.getStyle().getClickEvent();
        String fileName = fileComponent.getString();
        if (oldClick != null && oldClick.getAction() == ClickEvent.Action.OPEN_FILE && !oldClick.getValue().isEmpty()) {
            fileName = new File(oldClick.getValue()).getName();
        }

        String targetAction = PRINTSMC_PREFIX + fileName;

        MutableComponent customFileLink = fileComponent.copy().withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, targetAction))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("gui.printsmc.chat.hover")))
        );

        MutableComponent actionBadge = Component.literal(" [PrintsMC]")
                .withStyle(ChatFormatting.AQUA)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, targetAction))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("gui.printsmc.chat.hover")))
                );

        return Component.translatable("screenshot.success", customFileLink).append(actionBadge);
    }
}
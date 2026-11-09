package com.niixlabs.printsmc.client.gui.screen;

import com.niixlabs.printsmc.client.gui.card.ScreenshotCard;
import com.niixlabs.printsmc.client.gui.util.PrintsMcUploader;
import com.niixlabs.printsmc.client.gui.widget.ExpirationSlider;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

public class PreviewModal {
    private static final int MODAL_WIDTH = 440;
    private static final int MODAL_HEIGHT = 250;
    private static final int THUMB_WIDTH = 224;
    private static final int THUMB_HEIGHT = 126;

    private final Font font;
    private final int screenWidth;
    private final int screenHeight;
    private final ScreenshotCard card;
    private final Consumer<String> statusCallback;
    private final Runnable onListChanged;
    private final Runnable onClose;

    private final int modalX;
    private final int modalY;
    private final int uploadRowY;

    private final EditBox renameBox;
    private final Button renameButton;
    private final Button viewButton;
    private final Button copyImageButton;
    private final Button deleteButton;
    private final Button closeButton;
    private final ExpirationSlider expirationSlider;
    private final Button uploadButton;

    public PreviewModal(
            Font font,
            int screenWidth,
            int screenHeight,
            ScreenshotCard card,
            Consumer<String> statusCallback,
            Runnable onListChanged,
            Runnable onClose
    ) {
        this.font = font;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.card = card;
        this.statusCallback = statusCallback;
        this.onListChanged = onListChanged;
        this.onClose = onClose;

        this.modalX = (screenWidth - MODAL_WIDTH) / 2;
        this.modalY = (screenHeight - MODAL_HEIGHT) / 2;

        int actionRowY = this.modalY + MODAL_HEIGHT - 32;
        this.uploadRowY = actionRowY - 26;

        int infoX = this.modalX + THUMB_WIDTH + 24;
        int infoY = this.modalY + 16;

        this.renameBox = new EditBox(font, infoX, infoY + 82, 115, 18, Component.empty());
        this.renameBox.setMaxLength(50);
        this.renameBox.setValue(stripExtension(card.getFile().getName()));

        this.renameButton = Button.builder(Component.translatable("gui.printsmc.button.rename"), b -> {
            if (card.renameFile(this.renameBox.getValue())) {
                onListChanged.run();
            }
        }).bounds(infoX + 119, infoY + 81, 60, 20).build();

        this.viewButton = Button.builder(Component.translatable("gui.printsmc.button.open_file"), b ->
                Util.getPlatform().openFile(card.getFile())
        ).bounds(this.modalX + 16, actionRowY, 80, 20).build();

        this.copyImageButton = Button.builder(Component.translatable("gui.printsmc.button.copy_image"), b ->
                card.copyImageToSystemClipboard()
        ).bounds(this.modalX + 100, actionRowY, 80, 20).build();

        this.deleteButton = Button.builder(Component.translatable("gui.printsmc.button.delete"), b ->
                deleteSelectedFile()
        ).bounds(this.modalX + 184, actionRowY, 70, 20).build();

        this.closeButton = Button.builder(Component.translatable("gui.printsmc.button.close"), b ->
                onClose.run()
        ).bounds(this.modalX + MODAL_WIDTH - 86, actionRowY, 70, 20).build();

        this.expirationSlider = new ExpirationSlider(this.modalX + 16, this.uploadRowY, 120, 20);

        this.uploadButton = Button.builder(Component.translatable("gui.printsmc.button.upload"), b ->
                handleUploadClick()
        ).bounds(this.modalX + 140, this.uploadRowY, 90, 20).build();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);

        graphics.fill(0, 0, this.screenWidth, this.screenHeight, 0xD8101010);
        graphics.fill(this.modalX - 2, this.modalY - 2, this.modalX + MODAL_WIDTH + 2, this.modalY + MODAL_HEIGHT + 2, 0xFF00AAFF);
        graphics.fill(this.modalX, this.modalY, this.modalX + MODAL_WIDTH, this.modalY + MODAL_HEIGHT, 0xFF1E1E1E);

        if (this.card.getTextureLocation() != null) {
            graphics.blit(this.card.getTextureLocation(), this.modalX + 16, this.modalY + 16, 0, 0, THUMB_WIDTH, THUMB_HEIGHT, THUMB_WIDTH, THUMB_HEIGHT);
        }

        int infoX = this.modalX + THUMB_WIDTH + 24;
        int infoY = this.modalY + 16;

        graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.details_title"), infoX, infoY, 0xFFFFFFFF, true);
        graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.date", this.card.getMetaDate()), infoX, infoY + 20, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.size", this.card.getMetaSize()), infoX, infoY + 35, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.resolution", this.card.getMetaResolution()), infoX, infoY + 50, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.rename"), infoX, infoY + 68, 0xFFDDDDDD, false);

        this.renameBox.render(graphics, mouseX, mouseY, partialTick);
        this.renameButton.render(graphics, mouseX, mouseY, partialTick);
        this.viewButton.render(graphics, mouseX, mouseY, partialTick);
        this.copyImageButton.render(graphics, mouseX, mouseY, partialTick);
        this.deleteButton.render(graphics, mouseX, mouseY, partialTick);
        this.closeButton.render(graphics, mouseX, mouseY, partialTick);

        if (ModConfig.screenshotUploadEnabled) {
            syncUploadButtonState();
            this.expirationSlider.render(graphics, mouseX, mouseY, partialTick);
            this.uploadButton.render(graphics, mouseX, mouseY, partialTick);
            renderUploadStatusText(graphics);
        }

        graphics.pose().popPose();
    }

    private void renderUploadStatusText(GuiGraphics graphics) {
        int textX = this.modalX + 16;
        int textY = this.uploadRowY - 14;

        switch (this.card.getUploadState()) {
            case UPLOADED -> {
                if (this.card.getUploadUrl() != null) {
                    graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.link_display", this.card.getUploadUrl()), textX, textY, 0xFFFFFFFF, false);
                }
            }
            case ERROR -> {
                if (this.card.getUploadError() != null) {
                    graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.error_display", this.card.getUploadError()), textX, textY, 0xFFFF5555, false);
                }
            }
            case UPLOADING -> graphics.drawString(this.font, Component.translatable("gui.printsmc.modal.uploading_display"), textX, textY, 0xFFFFFF55, false);
            case IDLE -> {}
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean boxClicked = this.renameBox.mouseClicked(mouseX, mouseY, button);
        this.renameBox.setFocused(boxClicked);
        if (boxClicked) {
            return true;
        }

        for (Button btn : List.of(this.renameButton, this.viewButton, this.copyImageButton, this.deleteButton, this.closeButton)) {
            if (btn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (ModConfig.screenshotUploadEnabled) {
            if (this.expirationSlider.mouseClicked(mouseX, mouseY, button) || this.uploadButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (isUploadedLinkClicked(mouseX, mouseY)) {
                this.card.copyUploadLink();
                return true;
            }
        }

        if (!isInsideModal(mouseX, mouseY)) {
            this.onClose.run();
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ModConfig.screenshotUploadEnabled) {
            return this.expirationSlider.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (ModConfig.screenshotUploadEnabled) {
            return this.expirationSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return this.renameBox.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.renameBox.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isInsideModal(double mouseX, double mouseY) {
        return mouseX >= this.modalX && mouseX <= this.modalX + MODAL_WIDTH
                && mouseY >= this.modalY && mouseY <= this.modalY + MODAL_HEIGHT;
    }

    private boolean isUploadedLinkClicked(double mouseX, double mouseY) {
        if (this.card.getUploadState() != ScreenshotCard.UploadState.UPLOADED || this.card.getUploadUrl() == null) {
            return false;
        }
        int textX = this.modalX + 16;
        int textY = this.uploadRowY - 14;
        int textWidth = this.font.width(this.card.getUploadUrl());
        return mouseX >= textX && mouseX <= textX + textWidth
                && mouseY >= textY && mouseY <= textY + this.font.lineHeight;
    }

    private void deleteSelectedFile() {
        try {
            this.card.unloadTexture();
            Files.deleteIfExists(this.card.getFile().toPath());
            this.statusCallback.accept(Component.translatable("gui.printsmc.action.delete_success").getString());
            this.onListChanged.run();
        } catch (Exception e) {
            this.statusCallback.accept(Component.translatable("gui.printsmc.action.delete_fail").getString());
        }
    }

    private void handleUploadClick() {
        switch (this.card.getUploadState()) {
            case UPLOADED -> this.card.copyUploadLink();
            case IDLE, ERROR -> this.card.startUpload(this.expirationSlider.getSelectedDuration());
            case UPLOADING -> {}
        }
    }

    private void syncUploadButtonState() {
        switch (this.card.getUploadState()) {
            case IDLE -> {
                PrintsMcUploader.RejectReason reason = PrintsMcUploader.canUpload();
                if (reason == PrintsMcUploader.RejectReason.COOLDOWN) {
                    this.uploadButton.setMessage(Component.translatable("gui.printsmc.button.cooldown"));
                    this.uploadButton.active = false;
                } else if (reason == PrintsMcUploader.RejectReason.BUSY) {
                    this.uploadButton.setMessage(Component.translatable("gui.printsmc.button.busy"));
                    this.uploadButton.active = false;
                } else {
                    this.uploadButton.setMessage(Component.translatable("gui.printsmc.button.upload"));
                    this.uploadButton.active = true;
                }
            }
            case UPLOADING -> {
                this.uploadButton.setMessage(Component.translatable("gui.printsmc.button.uploading"));
                this.uploadButton.active = false;
            }
            case UPLOADED -> {
                this.uploadButton.setMessage(Component.translatable("gui.printsmc.button.copy_link"));
                this.uploadButton.active = true;
            }
            case ERROR -> {
                this.uploadButton.setMessage(Component.translatable("gui.printsmc.button.retry"));
                this.uploadButton.active = true;
            }
        }
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }
}
package com.niixlabs.printsmc.client.gui.screen;

import com.niixlabs.printsmc.client.gui.card.ScreenshotCard;
import com.niixlabs.printsmc.client.gui.util.GuiScale;
import com.niixlabs.printsmc.client.gui.util.PrintsMcUploader;
import com.niixlabs.printsmc.client.gui.util.TextureLoader;
import com.niixlabs.printsmc.client.gui.util.GuiScrollHandler;
import com.niixlabs.printsmc.config.ConfigManager;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GalleryScreen extends Screen {
    private final List<ScreenshotCard> cards = new ArrayList<>();
    private final GuiScrollHandler scrollHandler = new GuiScrollHandler();
    private ScreenshotCard selectedCard = null;
    private String statusMessage = "";
    private long statusTimer = 0;

    private Button viewButton;
    private Button deleteButton;
    private Button closeButton;
    private Button uploadButton;
    private Button copyImageButton;
    private Button renameButton;
    private EditBox renameBox;
    private ExpirationSlider expirationSlider;

    private int previewX;
    private int previewY;
    private final int previewWidth = 440;
    private final int previewHeight = 250;
    private int uploadRowY;

    public GalleryScreen() {
        super(Component.translatable("gui.printsmc.title"));
    }

    @Override
    protected void init() {
        this.width = (int) (this.minecraft.getWindow().getScreenWidth() / GuiScale.targetScale(this.minecraft));
        this.height = (int) (this.minecraft.getWindow().getScreenHeight() / GuiScale.targetScale(this.minecraft));
        this.clearWidgets();
        reloadScreenshots();

        int centerX = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.printsmc.open_folder_button"), b -> {
            File screenshotsDir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
            Util.getPlatform().openFile(screenshotsDir);
        }).bounds(centerX - 155, 10, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.printsmc.refresh_button"), b -> reloadScreenshots())
                .bounds(centerX - 50, 10, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.printsmc.close_button"), b -> this.onClose())
                .bounds(centerX + 55, 10, 100, 20).build());

        this.addRenderableWidget(new ScaleSlider(this.width - 110, 10, 100, 20));

        this.previewX = (this.width - previewWidth) / 2;
        this.previewY = (this.height - previewHeight) / 2;

        int btnY = previewY + previewHeight - 32;
        this.uploadRowY = btnY - 26;

        this.viewButton = Button.builder(Component.translatable("gui.printsmc.button.open_file"), b -> {
            if (selectedCard != null) Util.getPlatform().openFile(selectedCard.getFile());
        }).bounds(previewX + 16, btnY, 80, 20).build();

        this.copyImageButton = Button.builder(Component.translatable("gui.printsmc.button.copy_image"), b -> {
            if (selectedCard != null) selectedCard.copyImageToSystemClipboard();
        }).bounds(previewX + 100, btnY, 80, 20).build();

        this.deleteButton = Button.builder(Component.translatable("gui.printsmc.button.delete"), b -> {
            if (selectedCard == null) return;
            try {
                Files.delete(selectedCard.getFile().toPath());
                showStatus(Component.translatable("gui.printsmc.action.delete_success").getString());
                selectedCard = null;
                setModalElementsVisible(false);
                reloadScreenshots();
            } catch (Exception e) {
                showStatus(Component.translatable("gui.printsmc.action.delete_fail").getString());
            }
        }).bounds(previewX + 184, btnY, 70, 20).build();

        this.closeButton = Button.builder(Component.translatable("gui.printsmc.button.close"), b -> {
            selectedCard = null;
            setModalElementsVisible(false);
        }).bounds(previewX + previewWidth - 86, btnY, 70, 20).build();

        this.expirationSlider = new ExpirationSlider(previewX + 16, uploadRowY, 120, 20);
        this.expirationButtonVisible(false);
        this.addRenderableWidget(this.expirationSlider);

        this.uploadButton = Button.builder(Component.translatable("gui.printsmc.button.upload"), b -> handleUploadButtonClick())
                .bounds(previewX + 140, uploadRowY, 90, 20).build();
        this.uploadButton.visible = false;
        this.addRenderableWidget(this.uploadButton);

        this.renameBox = new EditBox(this.font, previewX + 225, previewY + 110, 135, 18, Component.empty());
        this.renameBox.setMaxLength(50);
        this.renameBox.setVisible(false);
        this.addRenderableWidget(this.renameBox);

        this.renameButton = Button.builder(Component.translatable("gui.printsmc.button.rename"), b -> {
            if (selectedCard != null) {
                String val = renameBox.getValue();
                if (selectedCard.renameFile(val)) {
                    showStatus(Component.translatable("gui.printsmc.action.rename_success").getString());
                }
            }
        }).bounds(previewX + 364, previewY + 109, 60, 20).build();
        this.renameButton.visible = false;
        this.addRenderableWidget(this.renameButton);
    }

    private void setModalElementsVisible(boolean visible) {
        if (renameBox != null) renameBox.setVisible(visible);
        if (renameButton != null) renameButton.visible = visible;
        expirationButtonVisible(visible);
        if (uploadButton != null) uploadButton.visible = visible;
    }

    private void expirationButtonVisible(boolean visible) {
        if (expirationSlider != null) expirationSlider.visible = visible;
    }

    private void reloadScreenshots() {
        cards.clear();
        File screenshotsDir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
        if (screenshotsDir.exists() && screenshotsDir.isDirectory()) {
            File[] files = screenshotsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".webp"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                for (File file : files) {
                    cards.add(new ScreenshotCard(file, this::showStatus, () -> {
                        this.selectedCard = null;
                        setModalElementsVisible(false);
                        this.reloadScreenshots();
                    }, card -> {
                        this.selectedCard = card;
                        if (this.renameBox != null) {
                            this.renameBox.setValue(card.getFile().getName().replaceAll("\\.[^.]+$", ""));
                            setModalElementsVisible(true);
                        }
                    }));
                }
            }
        }
        recalculateLayout();
    }

    private void recalculateLayout() {
        int cols = Math.max(1, ModConfig.gridColumns);
        int margin = 10;
        int startY = 40;
        int availableWidth = this.width - 40;
        int cardWidth = (availableWidth - (cols - 1) * margin) / cols;
        int cardHeight = (int) (cardWidth * 0.65);

        for (int i = 0; i < cards.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = 20 + col * (cardWidth + margin);
            int y = startY + row * (cardHeight + margin) - (int) scrollHandler.getScrollOffset();
            cards.get(i).setBounds(x, y, cardWidth, cardHeight);
        }

        int totalRows = (int) Math.ceil((double) cards.size() / cols);
        int totalHeight = totalRows * (cardHeight + margin);
        double maxScroll = Math.max(0, totalHeight - (this.height - startY - 20));
        scrollHandler.updateMaxScroll(maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedCard != null) return true;
        if (scrollHandler.handleMouseScrolled(scrollY, 25.0)) {
            recalculateLayout();
            return true;
        }
        float scale = GuiScale.scaleModifier(this.minecraft);
        return super.mouseScrolled(mouseX / scale, mouseY / scale, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = GuiScale.scaleModifier(this.minecraft);
        double scaledX = mouseX / scale;
        double scaledY = mouseY / scale;

        if (selectedCard != null) {
            if (renameBox.mouseClicked(scaledX, scaledY, button)) {
                renameBox.setFocused(true);
            } else {
                renameBox.setFocused(false);
            }

            if (renameButton.mouseClicked(scaledX, scaledY, button)) return true;
            if (viewButton.mouseClicked(scaledX, scaledY, button)) return true;
            if (copyImageButton.mouseClicked(scaledX, scaledY, button)) return true;
            if (deleteButton.mouseClicked(scaledX, scaledY, button)) return true;

            if (ModConfig.screenshotUploadEnabled) {
                if (expirationSlider.mouseClicked(scaledX, scaledY, button)) return true;
                if (uploadButton.mouseClicked(scaledX, scaledY, button)) return true;

                if (selectedCard.getUploadState() == ScreenshotCard.UploadState.UPLOADED && selectedCard.getUploadUrl() != null) {
                    String url = selectedCard.getUploadUrl();
                    int textX = previewX + 16;
                    int textY = uploadRowY - 14;
                    int textWidth = this.font.width(url);
                    int textHeight = this.font.lineHeight;

                    if (scaledX >= textX && scaledX <= textX + textWidth && scaledY >= textY && scaledY <= textY + textHeight) {
                        selectedCard.copyUploadLink();
                        return true;
                    }
                }
            }

            if (closeButton.mouseClicked(scaledX, scaledY, button)) {
                selectedCard = null;
                setModalElementsVisible(false);
                return true;
            }

            if (scaledX < previewX || scaledX > previewX + previewWidth || scaledY < previewY || scaledY > previewY + previewHeight) {
                selectedCard = null;
                setModalElementsVisible(false);
            }
            return true;
        }

        if (scrollHandler.handleMouseDown(scaledX, scaledY, this.width, 40, this.height - 40)) {
            return true;
        }
        for (ScreenshotCard card : cards) {
            if (card.getY() + card.getHeight() >= 40 && card.getY() <= this.height) {
                if (card.mouseClicked(scaledX, scaledY)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(scaledX, scaledY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollHandler.setDragging(false);
        float scale = GuiScale.scaleModifier(this.minecraft);
        if (selectedCard != null && ModConfig.screenshotUploadEnabled) {
            expirationSlider.mouseReleased(mouseX / scale, mouseY / scale, button);
        }
        return super.mouseReleased(mouseX / scale, mouseY / scale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float scale = GuiScale.scaleModifier(this.minecraft);
        double scaledX = mouseX / scale;
        double scaledY = mouseY / scale;

        if (selectedCard != null && ModConfig.screenshotUploadEnabled) {
            expirationSlider.mouseDragged(scaledX, scaledY, button, dragX / scale, dragY / scale);
        }

        if (scrollHandler.handleMouseDragged(scaledY, 40, this.height - 40)) {
            recalculateLayout();
            return true;
        }
        return super.mouseDragged(mouseX / scale, mouseY / scale, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (selectedCard != null && renameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedCard != null && renameBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256 && selectedCard != null) {
            selectedCard = null;
            setModalElementsVisible(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float scale = GuiScale.scaleModifier(this.minecraft);
        boolean focused = this.minecraft.isWindowActive();
        int scaledMouseX = focused ? (int) (mouseX / scale) : Integer.MIN_VALUE;
        int scaledMouseY = focused ? (int) (mouseY / scale) : Integer.MIN_VALUE;
        boolean modalOpen = selectedCard != null;
        int bgMouseX = modalOpen ? Integer.MIN_VALUE : scaledMouseX;
        int bgMouseY = modalOpen ? Integer.MIN_VALUE : scaledMouseY;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);

        this.renderBackground(guiGraphics, bgMouseX, bgMouseY, partialTick);

        int viewTop = 40;
        int viewBottom = this.height;
        for (ScreenshotCard card : cards) {
            if (card.getY() + card.getHeight() >= viewTop && card.getY() <= viewBottom) {
                card.render(guiGraphics, bgMouseX, bgMouseY);
            } else {
                card.unloadTexture();
            }
        }

        guiGraphics.fill(0, 0, this.width, 34, 0x66000000);
        scrollHandler.renderScrollbar(guiGraphics, this.width, 40, this.height - 40);

        if (System.currentTimeMillis() < statusTimer) {
            guiGraphics.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 25, 0x55FF55);
        }

        guiGraphics.pose().popPose();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);

        renderTopMenu(guiGraphics, bgMouseX, bgMouseY, partialTick);

        if (modalOpen) {
            renderPreviewModal(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
        }

        guiGraphics.pose().popPose();
    }

    private void renderTopMenu(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);

        for (var listener : this.children()) {
            if (listener instanceof Renderable renderable) {
                if (selectedCard == null && (renderable == renameBox || renderable == renameButton || renderable == expirationSlider || renderable == uploadButton)) {
                    continue;
                }
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        guiGraphics.pose().popPose();
    }

    private void renderPreviewModal(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 500);

        guiGraphics.fill(0, 0, this.width, this.height, 0xD8101010);
        guiGraphics.fill(previewX - 2, previewY - 2, previewX + previewWidth + 2, previewY + previewHeight + 2, 0xFF00AAFF);
        guiGraphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF1E1E1E);

        int imgW = 224;
        int imgH = 126;
        if (selectedCard.getTextureLocation() != null) {
            guiGraphics.blit(selectedCard.getTextureLocation(), previewX + 16, previewY + 16, 0, 0, imgW, imgH, imgW, imgH);
        }

        int infoX = previewX + imgW + 24;
        int infoY = previewY + 16;

        guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.details_title"), infoX, infoY, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.date", selectedCard.getMetaDate()), infoX, infoY + 20, 0xFFAAAAAA, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.size", selectedCard.getMetaSize()), infoX, infoY + 35, 0xFFAAAAAA, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.resolution", selectedCard.getMetaResolution()), infoX, infoY + 50, 0xFFAAAAAA, false);

        guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.rename"), infoX, infoY + 68, 0xFFDDDDDD, false);

        renameBox.setX(infoX);
        renameBox.setY(infoY + 82);
        renameBox.setWidth(115);
        renameBox.render(guiGraphics, mouseX, mouseY, partialTick);

        renameButton.setX(infoX + 119);
        renameButton.setY(infoY + 81);
        renameButton.render(guiGraphics, mouseX, mouseY, partialTick);

        viewButton.render(guiGraphics, mouseX, mouseY, partialTick);
        copyImageButton.render(guiGraphics, mouseX, mouseY, partialTick);
        deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);
        closeButton.render(guiGraphics, mouseX, mouseY, partialTick);

        if (ModConfig.screenshotUploadEnabled) {
            updateUploadButtonState();
            expirationSlider.render(guiGraphics, mouseX, mouseY, partialTick);
            uploadButton.render(guiGraphics, mouseX, mouseY, partialTick);

            if (selectedCard.getUploadState() == ScreenshotCard.UploadState.UPLOADED && selectedCard.getUploadUrl() != null) {
                guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.link_display", selectedCard.getUploadUrl()), previewX + 16, uploadRowY - 14, 0xFFFFFFFF, false);
            } else if (selectedCard.getUploadState() == ScreenshotCard.UploadState.ERROR && selectedCard.getUploadError() != null) {
                guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.error_display", selectedCard.getUploadError()), previewX + 16, uploadRowY - 14, 0xFFFF5555, false);
            } else if (selectedCard.getUploadState() == ScreenshotCard.UploadState.UPLOADING) {
                guiGraphics.drawString(this.font, Component.translatable("gui.printsmc.modal.uploading_display"), previewX + 16, uploadRowY - 14, 0xFFFFFF55, false);
            }
        }

        guiGraphics.pose().popPose();
    }

    private void handleUploadButtonClick() {
        if (selectedCard == null) return;
        switch (selectedCard.getUploadState()) {
            case UPLOADED -> selectedCard.copyUploadLink();
            case IDLE, ERROR -> selectedCard.startUpload(expirationSlider.getSelectedDuration());
            case UPLOADING -> {}
        }
    }

    private void updateUploadButtonState() {
        if (selectedCard == null) return;

        switch (selectedCard.getUploadState()) {
            case IDLE -> {
                PrintsMcUploader.RejectReason reason = PrintsMcUploader.canUpload();
                if (reason == PrintsMcUploader.RejectReason.COOLDOWN) {
                    uploadButton.setMessage(Component.translatable("gui.printsmc.button.cooldown"));
                    uploadButton.active = false;
                } else if (reason == PrintsMcUploader.RejectReason.BUSY) {
                    uploadButton.setMessage(Component.translatable("gui.printsmc.button.busy"));
                    uploadButton.active = false;
                } else {
                    uploadButton.setMessage(Component.translatable("gui.printsmc.button.upload"));
                    uploadButton.active = true;
                }
            }
            case UPLOADING -> {
                uploadButton.setMessage(Component.translatable("gui.printsmc.button.uploading"));
                uploadButton.active = false;
            }
            case UPLOADED -> {
                uploadButton.setMessage(Component.translatable("gui.printsmc.button.copy_link"));
                uploadButton.active = true;
            }
            case ERROR -> {
                uploadButton.setMessage(Component.translatable("gui.printsmc.button.retry"));
                uploadButton.active = true;
            }
        }
    }

    private void showStatus(String msg) {
        this.statusMessage = msg;
        this.statusTimer = System.currentTimeMillis() + 3000;
    }

    @Override
    public void onClose() {
        TextureLoader.clearCache();
        super.onClose();
    }

    private class ExpirationSlider extends AbstractSliderButton {
        private final String[] durations = {"24h", "7d", "30d"};

        public ExpirationSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.5);
            this.updateMessage();
        }

        public String getSelectedDuration() {
            int index = (int) Math.round(this.value * (durations.length - 1));
            return durations[Mth.clamp(index, 0, durations.length - 1)];
        }

        @Override
        protected void updateMessage() {
            int index = (int) Math.round(this.value * (durations.length - 1));
            String duration = durations[Mth.clamp(index, 0, durations.length - 1)];
            this.setMessage(Component.translatable("gui.printsmc.slider.expires", duration));
        }

        @Override
        protected void applyValue() {
            this.updateMessage();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            int index = (int) Math.round(this.value * (durations.length - 1));
            this.value = (double) index / (durations.length - 1);
            this.updateMessage();
        }
    }

    private class ScaleSlider extends AbstractSliderButton {
        public ScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), getInitialValue());
            this.updateMessage();
        }

        private static double getInitialValue() {
            int maxScale = GuiScale.maxSupportedScale(Minecraft.getInstance());
            int current = Mth.clamp(ModConfig.customGuiScale, 0, maxScale);
            return maxScale > 0 ? (double) current / maxScale : 0.0;
        }

        @Override
        protected void updateMessage() {
            int maxScale = GuiScale.maxSupportedScale(Minecraft.getInstance());
            int current = (int) Math.round(this.value * maxScale);

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
            int current = (int) Math.round(this.value * maxScale);

            this.value = maxScale > 0 ? (double) current / maxScale : 0.0;
            this.updateMessage();

            if (ModConfig.customGuiScale != current) {
                ConfigManager.updateAndSave(ModConfig.class, "customGuiScale", current);
                GalleryScreen.this.init(Minecraft.getInstance(), Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
            }
        }
    }
}
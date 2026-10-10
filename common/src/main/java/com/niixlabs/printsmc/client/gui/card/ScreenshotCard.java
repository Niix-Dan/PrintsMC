package com.niixlabs.printsmc.client.gui.card;

import com.niixlabs.printsmc.client.gui.util.ClipboardManager;
import com.niixlabs.printsmc.client.gui.util.PrintsMcUploader;
import com.niixlabs.printsmc.client.gui.util.TextureLoader;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.function.Consumer;

public class ScreenshotCard {

    public enum UploadState { IDLE, UPLOADING, UPLOADED, ERROR }

    private File file;
    private final Consumer<String> statusCallback;
    private final Runnable refreshCallback;
    private final Consumer<ScreenshotCard> selectCallback;
    private ResourceLocation textureLocation;
    private int x;
    private int y;
    private int width;
    private int height;
    private String metaDate = "";
    private String metaSize = "";
    private String metaResolution = "";
    private boolean metaLoaded = false;

    private UploadState uploadState = UploadState.IDLE;
    private String uploadUrl = null;
    private String uploadError = null;

    public ScreenshotCard(File file, Consumer<String> statusCallback, Runnable refreshCallback, Consumer<ScreenshotCard> selectCallback) {
        this.file = file;
        this.statusCallback = statusCallback;
        this.refreshCallback = refreshCallback;
        this.selectCallback = selectCallback;

        String cachedUrl = PrintsMcUploader.getCachedUrl(file);
        if (cachedUrl != null) {
            this.uploadUrl = cachedUrl;
            this.uploadState = UploadState.UPLOADED;
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void loadMetadata() {
        if (metaLoaded) return;
        try {
            this.metaDate = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(file.lastModified());
            this.metaSize = (file.length() / 1024) + " KB";
            Iterator<ImageReader> iter = ImageIO.getImageReadersBySuffix("png");
            if (!iter.hasNext()) iter = ImageIO.getImageReadersBySuffix("webp");

            if (iter.hasNext()) {
                ImageReader reader = iter.next();
                try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
                    reader.setInput(stream);
                    this.metaResolution = reader.getWidth(reader.getMinIndex()) + "x" + reader.getHeight(reader.getMinIndex());
                } finally {
                    reader.dispose();
                }
            } else {
                this.metaResolution = "Unknown";
            }
        } catch (Exception e) {
            this.metaResolution = "Error";
            this.metaSize = "Error";
        }
        this.metaLoaded = true;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (textureLocation == null) {
            textureLocation = TextureLoader.getOrLoadTexture(file);
        }

        boolean isHovered = isMouseOver(mouseX, mouseY);
        int bgColor = isHovered ? 0xFF3A3D40 : ModConfig.cardBackgroundColor;

        guiGraphics.fill(x, y, x + width, y + height, bgColor);
        guiGraphics.renderOutline(x, y, width, height, isHovered ? 0xFF00AAFF : 0xFF555555);

        if (textureLocation != null) {
            guiGraphics.blit(textureLocation, x + 4, y + 4, 0, 0, width - 8, height - 28, width - 8, height - 28);
        } else {
            guiGraphics.fill(x + 4, y + 4, x + width - 4, y + height - 24, 0xFF222222);
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable("gui.printsmc.loading").getString(), x + (width / 2), y + ((height - 24) / 2), 0xFFAAAAAA);
        }

        String name = file.getName();
        if (name.length() > 20) {
            name = name.substring(0, 17) + "...";
        }
        guiGraphics.drawString(Minecraft.getInstance().font, name, x + 6, y + height - 16, 0xFFE0E0E0, false);

        if (ModConfig.screenshotUploadEnabled && uploadState == UploadState.UPLOADED) {
            guiGraphics.fill(x + width - 18, y + height - 18, x + width - 6, y + height - 6, 0xAA00AA55);
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, "☁", x + width - 12, y + height - 17, 0xFFFFFFFF);
        }
    }

    public void unloadTexture() {
        if (this.textureLocation != null) {
            TextureLoader.unloadTexture(this.file);
            this.textureLocation = null;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        loadMetadata();
        selectCallback.accept(this);
        return true;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (mouseY < 40) return false;
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void startUpload(String duration) {
        if (uploadState == UploadState.UPLOADING) return;

        String cached = PrintsMcUploader.getCachedUrl(file);
        if (cached != null) {
            this.uploadUrl = cached;
            this.uploadState = UploadState.UPLOADED;
            return;
        }

        PrintsMcUploader.RejectReason reason = PrintsMcUploader.canUpload();
        if (reason != PrintsMcUploader.RejectReason.NONE) {
            statusCallback.accept(Component.translatable(rejectMessageKey(reason)).getString());
            return;
        }

        this.uploadState = UploadState.UPLOADING;
        statusCallback.accept(Component.translatable("gui.printsmc.upload.status.start").getString());

        PrintsMcUploader.uploadAsync(file, duration, (url, error) -> {
            if (error != null) {
                this.uploadState = UploadState.ERROR;
                this.uploadError = error;
                statusCallback.accept(Component.translatable("gui.printsmc.upload.status.fail", error).getString());
            } else {
                this.uploadState = UploadState.UPLOADED;
                this.uploadUrl = url;
                statusCallback.accept(Component.translatable("gui.printsmc.upload.status.success").getString());
            }
        });
    }

    public void copyUploadLink() {
        if (uploadUrl == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(uploadUrl);
        statusCallback.accept(Component.translatable("gui.printsmc.action.copy_link_success").getString());
    }

    public void copyImageToSystemClipboard() {
        ClipboardManager.sendImageToClipboard(file);
        statusCallback.accept(Component.translatable("gui.printsmc.action.copy_image_success").getString());
    }

    public boolean renameFile(String newName) {
        if (newName == null || newName.trim().isEmpty()) return false;
        try {
            String originalName = file.getName();
            String extension = originalName.substring(originalName.lastIndexOf("."));

            String baseName = newName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!baseName.endsWith(extension)) {
                baseName += extension;
            }

            File newFile = new File(file.getParentFile(), baseName);
            if (newFile.exists() && !newFile.equals(file)) return false;

            unloadTexture();
            if (file.renameTo(newFile)) {
                this.file = newFile;
                this.metaLoaded = false;
                statusCallback.accept(Component.translatable("gui.printsmc.action.rename_success").getString());
                refreshCallback.run();
                return true;
            }
        } catch (Exception e) {
            statusCallback.accept(Component.translatable("gui.printsmc.action.rename_fail").getString());
        }
        return false;
    }

    private String rejectMessageKey(PrintsMcUploader.RejectReason reason) {
        return switch (reason) {
            case COOLDOWN -> "gui.printsmc.upload.status.cooldown";
            case BUSY -> "gui.printsmc.upload.status.busy";
            default -> "";
        };
    }

    public int getY() { return y; }
    public int getHeight() { return height; }
    public File getFile() { return file; }
    public ResourceLocation getTextureLocation() { return textureLocation; }
    public String getMetaDate() { return metaDate; }
    public String getMetaSize() { return metaSize; }
    public String getMetaResolution() { return metaResolution; }
    public UploadState getUploadState() { return uploadState; }
    public String getUploadUrl() { return uploadUrl; }
    public String getUploadError() { return uploadError; }
}
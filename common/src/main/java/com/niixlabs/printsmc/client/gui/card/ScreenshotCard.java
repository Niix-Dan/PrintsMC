package com.niixlabs.printsmc.client.gui.card;

import com.niixlabs.printsmc.client.gui.util.ClipboardManager;
import com.niixlabs.printsmc.client.gui.util.PrintsMcUploader;
import com.niixlabs.printsmc.client.gui.util.TextureLoader;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.function.Consumer;

public class ScreenshotCard {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public enum UploadState { IDLE, UPLOADING, UPLOADED, ERROR }

    private File file;
    private final Consumer<String> statusCallback;
    private final Consumer<ScreenshotCard> selectCallback;

    private ResourceLocation textureLocation;
    private int x;
    private int y;
    private int width;
    private int height;

    private String metaDate = "";
    private String metaSize = "";
    private String metaResolution = "";
    private boolean metaLoaded;

    private UploadState uploadState = UploadState.IDLE;
    private String uploadUrl;
    private String uploadError;

    public ScreenshotCard(File file, Consumer<String> statusCallback, Consumer<ScreenshotCard> selectCallback) {
        this.file = file;
        this.statusCallback = statusCallback;
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

    public boolean isVisibleInViewport(int viewportTop, int viewportBottom) {
        return this.y + this.height >= viewportTop && this.y <= viewportBottom;
    }

    public void loadMetadata() {
        if (this.metaLoaded) {
            return;
        }
        try {
            this.metaDate = DATE_FORMATTER.format(Instant.ofEpochMilli(this.file.lastModified()));
            this.metaSize = (this.file.length() / 1024) + " KB";
            this.metaResolution = readImageDimensions(this.file);
        } catch (Exception e) {
            this.metaResolution = "Error";
            this.metaSize = "Error";
        }
        this.metaLoaded = true;
    }

    private static String readImageDimensions(File file) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            if (stream != null) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(stream);
                        int w = reader.getWidth(reader.getMinIndex());
                        int h = reader.getHeight(reader.getMinIndex());
                        return w + "x" + h;
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "Unknown";
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.textureLocation == null) {
            this.textureLocation = TextureLoader.getOrLoadTexture(this.file);
        }

        Font font = Minecraft.getInstance().font;
        boolean hovered = isMouseOver(mouseX, mouseY);
        int bgColor = hovered ? 0xFF3A3D40 : ModConfig.cardBackgroundColor;

        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);
        graphics.renderOutline(this.x, this.y, this.width, this.height, hovered ? 0xFF00AAFF : 0xFF555555);

        int thumbW = this.width - 8;
        int thumbH = this.height - 28;

        if (this.textureLocation != null) {
            graphics.blit(this.textureLocation, this.x + 4, this.y + 4, 0, 0, thumbW, thumbH, thumbW, thumbH);
        } else {
            graphics.fill(this.x + 4, this.y + 4, this.x + this.width - 4, this.y + this.height - 24, 0xFF222222);
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.printsmc.loading").getString(),
                    this.x + (this.width / 2),
                    this.y + ((this.height - 24) / 2),
                    0xFFAAAAAA
            );
        }

        String displayName = formatDisplayName(this.file.getName());
        graphics.drawString(font, displayName, this.x + 6, this.y + this.height - 16, 0xFFE0E0E0, false);

        if (ModConfig.screenshotUploadEnabled && this.uploadState == UploadState.UPLOADED) {
            graphics.fill(this.x + this.width - 18, this.y + this.height - 18, this.x + this.width - 6, this.y + this.height - 6, 0xAA00AA55);
            graphics.drawCenteredString(font, "☁", this.x + this.width - 12, this.y + this.height - 17, 0xFFFFFFFF);
        }
    }

    private static String formatDisplayName(String name) {
        return name.length() > 20 ? name.substring(0, 17) + "..." : name;
    }

    public void unloadTexture() {
        if (this.textureLocation != null) {
            TextureLoader.unloadTexture(this.file);
            this.textureLocation = null;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        loadMetadata();
        this.selectCallback.accept(this);
        return true;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (mouseY < 40) {
            return false;
        }
        return mouseX >= this.x && mouseX <= this.x + this.width
                && mouseY >= this.y && mouseY <= this.y + this.height;
    }

    public void startUpload(String duration) {
        if (this.uploadState == UploadState.UPLOADING) {
            return;
        }

        String cached = PrintsMcUploader.getCachedUrl(this.file);
        if (cached != null) {
            this.uploadUrl = cached;
            this.uploadState = UploadState.UPLOADED;
            return;
        }

        PrintsMcUploader.RejectReason reason = PrintsMcUploader.canUpload();
        if (reason != PrintsMcUploader.RejectReason.NONE) {
            this.statusCallback.accept(Component.translatable(rejectMessageKey(reason)).getString());
            return;
        }

        this.uploadState = UploadState.UPLOADING;
        this.statusCallback.accept(Component.translatable("gui.printsmc.upload.status.start").getString());

        PrintsMcUploader.uploadAsync(this.file, duration, (url, error) -> {
            if (error != null) {
                this.uploadState = UploadState.ERROR;
                this.uploadError = error;
                this.statusCallback.accept(Component.translatable("gui.printsmc.upload.status.fail", error).getString());
            } else {
                this.uploadState = UploadState.UPLOADED;
                this.uploadUrl = url;
                this.statusCallback.accept(Component.translatable("gui.printsmc.upload.status.success").getString());
            }
        });
    }

    public void copyUploadLink() {
        if (this.uploadUrl == null) {
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(this.uploadUrl);
        this.statusCallback.accept(Component.translatable("gui.printsmc.action.copy_link_success").getString());
    }

    public void copyImageToSystemClipboard() {
        ClipboardManager.sendImageToClipboard(this.file);
        this.statusCallback.accept(Component.translatable("gui.printsmc.action.copy_image_success").getString());
    }

    public boolean renameFile(String newName) {
        if (newName == null || newName.isBlank()) {
            return false;
        }
        try {
            String originalName = this.file.getName();
            int dotIndex = originalName.lastIndexOf('.');
            String extension = dotIndex >= 0 ? originalName.substring(dotIndex) : "";

            String sanitized = newName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!sanitized.toLowerCase().endsWith(extension.toLowerCase())) {
                sanitized += extension;
            }

            Path targetPath = this.file.toPath().resolveSibling(sanitized);
            if (Files.exists(targetPath) && !targetPath.equals(this.file.toPath())) {
                return false;
            }

            unloadTexture();
            Files.move(this.file.toPath(), targetPath);
            this.file = targetPath.toFile();
            this.metaLoaded = false;
            this.statusCallback.accept(Component.translatable("gui.printsmc.action.rename_success").getString());
            return true;
        } catch (Exception e) {
            this.statusCallback.accept(Component.translatable("gui.printsmc.action.rename_fail").getString());
            return false;
        }
    }

    private static String rejectMessageKey(PrintsMcUploader.RejectReason reason) {
        return switch (reason) {
            case COOLDOWN -> "gui.printsmc.upload.status.cooldown";
            case BUSY -> "gui.printsmc.upload.status.busy";
            default -> "";
        };
    }

    public File getFile() { return this.file; }
    public ResourceLocation getTextureLocation() { return this.textureLocation; }
    public String getMetaDate() { return this.metaDate; }
    public String getMetaSize() { return this.metaSize; }
    public String getMetaResolution() { return this.metaResolution; }
    public UploadState getUploadState() { return this.uploadState; }
    public String getUploadUrl() { return this.uploadUrl; }
    public String getUploadError() { return this.uploadError; }
}
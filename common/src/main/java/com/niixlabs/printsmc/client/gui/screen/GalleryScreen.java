package com.niixlabs.printsmc.client.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.niixlabs.printsmc.client.gui.card.ScreenshotCard;
import com.niixlabs.printsmc.client.gui.util.GuiScale;
import com.niixlabs.printsmc.client.gui.util.GuiScrollHandler;
import com.niixlabs.printsmc.client.gui.util.TextureLoader;
import com.niixlabs.printsmc.client.gui.widget.ScaleSlider;
import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GalleryScreen extends Screen {
    private static final int TOP_BAR_HEIGHT = 34;
    private static final int VIEWPORT_TOP = 40;
    private static final int GRID_MARGIN = 10;
    private static final int SIDE_PADDING = 20;
    private static final double SCROLL_SPEED = 25.0;
    private static final long STATUS_DURATION_MS = 3000L;

    private final List<ScreenshotCard> cards = new ArrayList<>();
    private final GuiScrollHandler scrollHandler = new GuiScrollHandler();

    private PreviewModal activeModal;
    private String statusMessage = "";
    private long statusExpiresAt;

    private final Screen parent;
    private File initialFile;

    public GalleryScreen() {
        this(null, null);
    }

    public GalleryScreen(Screen parent) {
        this(parent, null);
    }

    public GalleryScreen(Screen parent, File initialFile) {
        super(Component.translatable("gui.printsmc.title"));
        this.parent = parent;
        this.initialFile = initialFile;
    }


    @Override
    protected void init() {
        double targetScale = GuiScale.targetScale(this.minecraft);
        this.width = (int) (this.minecraft.getWindow().getScreenWidth() / targetScale);
        this.height = (int) (this.minecraft.getWindow().getScreenHeight() / targetScale);
        this.clearWidgets();

        int centerX = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.printsmc.open_folder_button"), b -> {
            File dir = new File(this.minecraft.gameDirectory, "screenshots");
            Util.getPlatform().openFile(dir);
        }).bounds(centerX - 155, 10, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.printsmc.refresh_button"), b -> reloadScreenshots())
                .bounds(centerX - 50, 10, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.printsmc.close_button"), b -> this.onClose())
                .bounds(centerX + 55, 10, 100, 20).build());

        this.addRenderableWidget(new ScaleSlider(this.width - 110, 10, 100, 20, () ->
                this.init(this.minecraft, this.minecraft.getWindow().getGuiScaledWidth(), this.minecraft.getWindow().getGuiScaledHeight())
        ));

        reloadScreenshots();
    }

    private void reloadScreenshots() {
        this.cards.clear();
        File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");

        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".webp");
            });

            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                for (File file : files) {
                    this.cards.add(new ScreenshotCard(file, this::showStatus, this::openModal));
                }
            }
        }

        if (this.activeModal != null) {
            this.activeModal = null;
        }

        recalculateLayout();

        if (this.initialFile != null) {
            for (ScreenshotCard card : this.cards) {
                if (card.getFile().getName().equalsIgnoreCase(this.initialFile.getName())) {
                    card.loadMetadata();
                    openModal(card);
                    break;
                }
            }
            this.initialFile = null;
        }
    }

    private void openModal(ScreenshotCard card) {
        this.activeModal = new PreviewModal(
                this.font,
                this.width,
                this.height,
                card,
                this::showStatus,
                this::reloadScreenshots,
                () -> this.activeModal = null
        );
    }

    private void recalculateLayout() {
        int cols = Math.max(1, ModConfig.gridColumns);
        int availableWidth = this.width - (SIDE_PADDING * 2);
        int cardWidth = (availableWidth - (cols - 1) * GRID_MARGIN) / cols;
        int cardHeight = (int) (cardWidth * 0.65);
        int scrollOffset = (int) this.scrollHandler.getScrollOffset();

        for (int i = 0; i < this.cards.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = SIDE_PADDING + col * (cardWidth + GRID_MARGIN);
            int y = VIEWPORT_TOP + row * (cardHeight + GRID_MARGIN) - scrollOffset;
            this.cards.get(i).setBounds(x, y, cardWidth, cardHeight);
        }

        int totalRows = (this.cards.size() + cols - 1) / cols;
        int totalHeight = totalRows * (cardHeight + GRID_MARGIN);
        double maxScroll = Math.max(0, totalHeight - (this.height - VIEWPORT_TOP - SIDE_PADDING));
        this.scrollHandler.updateMaxScroll(maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.activeModal != null) {
            return true;
        }
        if (this.scrollHandler.handleMouseScrolled(scrollY, SCROLL_SPEED)) {
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

        if (this.activeModal != null) {
            return this.activeModal.mouseClicked(scaledX, scaledY, button);
        }

        if (this.scrollHandler.handleMouseDown(scaledX, scaledY, this.width, VIEWPORT_TOP, this.height - VIEWPORT_TOP)) {
            return true;
        }

        for (ScreenshotCard card : this.cards) {
            if (card.isVisibleInViewport(VIEWPORT_TOP, this.height) && card.mouseClicked(scaledX, scaledY)) {
                return true;
            }
        }

        return super.mouseClicked(scaledX, scaledY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollHandler.setDragging(false);
        float scale = GuiScale.scaleModifier(this.minecraft);
        double scaledX = mouseX / scale;
        double scaledY = mouseY / scale;

        if (this.activeModal != null) {
            return this.activeModal.mouseReleased(scaledX, scaledY, button);
        }

        return super.mouseReleased(scaledX, scaledY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float scale = GuiScale.scaleModifier(this.minecraft);
        double scaledX = mouseX / scale;
        double scaledY = mouseY / scale;

        if (this.activeModal != null) {
            return this.activeModal.mouseDragged(scaledX, scaledY, button, dragX / scale, dragY / scale);
        }

        if (this.scrollHandler.handleMouseDragged(scaledY, VIEWPORT_TOP, this.height - VIEWPORT_TOP)) {
            recalculateLayout();
            return true;
        }

        return super.mouseDragged(scaledX, scaledY, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.activeModal != null && this.activeModal.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.activeModal != null) {
            if (this.activeModal.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                this.activeModal = null;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float scale = GuiScale.scaleModifier(this.minecraft);
        boolean focused = this.minecraft.isWindowActive();
        int scaledMouseX = focused ? (int) (mouseX / scale) : Integer.MIN_VALUE;
        int scaledMouseY = focused ? (int) (mouseY / scale) : Integer.MIN_VALUE;

        boolean modalOpen = this.activeModal != null;
        int gridMouseX = modalOpen ? Integer.MIN_VALUE : scaledMouseX;
        int gridMouseY = modalOpen ? Integer.MIN_VALUE : scaledMouseY;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);

        this.renderBackground(graphics, gridMouseX, gridMouseY, partialTick);

        for (ScreenshotCard card : this.cards) {
            if (card.isVisibleInViewport(VIEWPORT_TOP, this.height)) {
                card.render(graphics, gridMouseX, gridMouseY);
            } else {
                card.unloadTexture();
            }
        }

        graphics.fill(0, 0, this.width, TOP_BAR_HEIGHT, 0x66000000);
        this.scrollHandler.renderScrollbar(graphics, this.width, VIEWPORT_TOP, this.height - VIEWPORT_TOP);

        if (System.currentTimeMillis() < this.statusExpiresAt) {
            graphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, this.height - 25, 0x55FF55);
        }

        renderTopBar(graphics, gridMouseX, gridMouseY, partialTick);

        if (modalOpen) {
            this.activeModal.render(graphics, scaledMouseX, scaledMouseY, partialTick);
        }

        graphics.pose().popPose();
    }

    private void renderTopBar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);

        for (var child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        graphics.pose().popPose();
    }

    private void showStatus(String message) {
        this.statusMessage = message;
        this.statusExpiresAt = System.currentTimeMillis() + STATUS_DURATION_MS;
    }


    @Override
    public void onClose() {
        TextureLoader.clearCache();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
package com.niixlabs.printsmc.client.gui.util;

import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class GuiScrollHandler {
    private double scrollOffset;
    private double maxScroll;
    private boolean dragging;
    private double dragClickOffset;

    public double getScrollOffset() {
        return this.scrollOffset;
    }

    public void setScrollOffset(double offset) {
        this.scrollOffset = Mth.clamp(offset, 0.0, this.maxScroll);
    }

    public void updateMaxScroll(double maxScroll) {
        this.maxScroll = Math.max(0.0, maxScroll);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0.0, this.maxScroll);
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    public int getThumbHeight(int viewportHeight) {
        if (this.maxScroll <= 0) {
            return 0;
        }
        int proportionalHeight = (int) ((viewportHeight / (float) (viewportHeight + this.maxScroll)) * viewportHeight);
        return Math.max(ModConfig.screenMinScrollThumbHeight, proportionalHeight);
    }

    public int getThumbY(int viewportY, int viewportHeight) {
        if (this.maxScroll <= 0) {
            return viewportY;
        }
        int scrollableTrack = viewportHeight - getThumbHeight(viewportHeight);
        return viewportY + (int) ((this.scrollOffset / this.maxScroll) * scrollableTrack);
    }

    public void renderScrollbar(GuiGraphics graphics, int screenWidth, int viewportY, int viewportHeight) {
        if (this.maxScroll <= 0) {
            return;
        }

        int scrollbarX = screenWidth - ModConfig.screenScrollbarRightMargin;
        int scrollbarEndX = scrollbarX + ModConfig.screenScrollbarWidth;
        int thumbHeight = getThumbHeight(viewportHeight);
        int thumbY = getThumbY(viewportY, viewportHeight);
        int thumbColor = this.dragging ? ModConfig.screenScrollbarThumbActive : ModConfig.screenScrollbarThumbIdle;

        graphics.fill(scrollbarX, viewportY, scrollbarEndX, viewportY + viewportHeight, ModConfig.screenScrollbarTrackColor);
        graphics.fill(scrollbarX, thumbY, scrollbarEndX, thumbY + thumbHeight, thumbColor);
    }

    public boolean handleMouseDown(double mouseX, double mouseY, int screenWidth, int viewportY, int viewportHeight) {
        if (this.maxScroll <= 0) {
            return false;
        }

        int scrollbarX = screenWidth - ModConfig.screenScrollbarRightMargin;
        if (mouseX < scrollbarX - 2 || mouseX > scrollbarX + ModConfig.screenScrollbarWidth + 2) {
            return false;
        }

        int thumbHeight = getThumbHeight(viewportHeight);
        int thumbY = getThumbY(viewportY, viewportHeight);

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            this.dragging = true;
            this.dragClickOffset = mouseY - thumbY;
            return true;
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseY, int viewportY, int viewportHeight) {
        if (!this.dragging || this.maxScroll <= 0) {
            return false;
        }

        int trackHeight = viewportHeight - getThumbHeight(viewportHeight);
        if (trackHeight > 0) {
            double targetThumbY = mouseY - this.dragClickOffset;
            double progress = Mth.clamp((targetThumbY - viewportY) / trackHeight, 0.0, 1.0);
            this.scrollOffset = progress * this.maxScroll;
        }
        return true;
    }

    public boolean handleMouseScrolled(double scrollY, double scrollSpeed) {
        if (this.maxScroll <= 0) {
            return false;
        }
        setScrollOffset(this.scrollOffset - (scrollY * scrollSpeed));
        return true;
    }
}
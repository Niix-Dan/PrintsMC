package com.niixlabs.printsmc.client.gui.util;

import com.niixlabs.printsmc.PrintsMC;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ClipboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrintsMC.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("win");

    private static final int GMEM_MOVEABLE = 0x0002;
    private static final int CF_DIB = 8;
    private static final int BITMAP_HEADER_SIZE = 40;

    private ClipboardManager() {}

    public static void sendImageToClipboard(File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            if (IS_WINDOWS && copyViaWin32Clipboard(imageFile)) {
                return;
            }
            copyViaAwtClipboard(imageFile);
        });
    }

    private static boolean copyViaWin32Clipboard(File file) {
        try {
            BufferedImage source = ImageIO.read(file);
            if (source == null) {
                return false;
            }

            byte[] dibBytes = encodeToDib( flattenToRgb(source));
            WinUser32 user32 = WinUser32.INSTANCE;
            WinKernel32 kernel32 = WinKernel32.INSTANCE;

            if (!user32.OpenClipboard( null)) {
                return false;
            }

            try {
                if (!user32.EmptyClipboard()) {
                    return false;
                }

                Pointer hGlobal = kernel32.GlobalAlloc(GMEM_MOVEABLE, dibBytes.length);
                if (hGlobal == null) {
                    return false;
                }

                Pointer lockedPtr = kernel32.GlobalLock(hGlobal);
                if (lockedPtr == null) {
                    kernel32.GlobalFree(hGlobal);
                    return false;
                }

                try {
                    lockedPtr.write(0, dibBytes, 0, dibBytes.length);
                } finally {
                    kernel32.GlobalUnlock(hGlobal);
                }

                if (user32.SetClipboardData(CF_DIB, hGlobal) == null) {
                    kernel32.GlobalFree(hGlobal);
                    return false;
                }
                return true;
            } finally {
                user32.CloseClipboard();
            }
        } catch (Throwable t) {
            LOGGER.debug("Win32 clipboard copy unavailable, falling back to AWT: {}", t.getMessage());
            return false;
        }
    }

    private static BufferedImage flattenToRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] encodeToDib(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int payloadSize = width * height * 4;

        ByteBuffer buffer = ByteBuffer.allocate(BITMAP_HEADER_SIZE + payloadSize)
                .order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(BITMAP_HEADER_SIZE);
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.putShort((short) 1);
        buffer.putShort((short) 32);
        buffer.putInt(0);
        buffer.putInt(payloadSize);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);

        int[] rowPixels = new int[width];
        for (int y = height - 1; y >= 0; y--) {
            img.getRGB(0, y, width, 1, rowPixels, 0, width);
            for (int x = 0; x < width; x++) {
                buffer.putInt(rowPixels[x] | 0xFF000000);
            }
        }

        return buffer.array();
    }

    private static void copyViaAwtClipboard(File file) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return;
            }
            BufferedImage source = ImageIO.read(file);
            if (source == null) {
                return;
            }

            BufferedImage rgb = flattenToRgb(source);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(rgb), null);
        } catch (Exception e) {
            LOGGER.error("Failed to copy image to system clipboard", e);
        }
    }

    private record ImageTransferable(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return this.image;
        }
    }

    public interface WinUser32 extends StdCallLibrary {
        WinUser32 INSTANCE = Native.load("user32", WinUser32.class);
        boolean OpenClipboard(Pointer hWnd);
        boolean EmptyClipboard();
        Pointer SetClipboardData(int uFormat, Pointer hMem);
        boolean CloseClipboard();
    }

    public interface WinKernel32 extends StdCallLibrary {
        WinKernel32 INSTANCE = Native.load("kernel32", WinKernel32.class);
        Pointer GlobalAlloc(int uFlags, long dwBytes);
        Pointer GlobalLock(Pointer hMem);
        boolean GlobalUnlock(Pointer hMem);
        Pointer GlobalFree(Pointer hMem);
    }
}
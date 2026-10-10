package com.niixlabs.printsmc.client.gui.util;

import com.niixlabs.printsmc.PrintsMC;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ClipboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrintsMC.class);

    public static void sendImageToClipboard(File imageFile) {
        if (imageFile == null || !imageFile.exists()) return;

        CompletableFuture.runAsync(() -> {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                if (tryWinJnaCopy(imageFile)) {
                    return;
                }
            }
            fallbackAwtCopy(imageFile);
        });
    }

    private static boolean tryWinJnaCopy(File file) {
        try {
            BufferedImage source = ImageIO.read(file);
            if (source == null) return false;

            BufferedImage rgbImage = toRgbImage(source);
            byte[] dibData = createDIB(rgbImage);

            WinUser32 user32 = WinUser32.INSTANCE;
            WinKernel32 kernel32 = WinKernel32.INSTANCE;

            if (user32.OpenClipboard(null)) {
                try {
                    if (user32.EmptyClipboard()) {
                        Pointer memoryBlock = kernel32.GlobalAlloc(0x0002, dibData.length);
                        if (memoryBlock == null) return false;

                        Pointer lockedMem = kernel32.GlobalLock(memoryBlock);
                        if (lockedMem == null) return false;

                        try {
                            lockedMem.write(0, dibData, 0, dibData.length);
                        } finally {
                            kernel32.GlobalUnlock(memoryBlock);
                        }

                        Pointer result = user32.SetClipboardData(8, memoryBlock); // 8 = CF_DIB
                        if (result != null) {
                            return true;
                        } else {
                            kernel32.GlobalFree(memoryBlock);
                        }
                    }
                } finally {
                    user32.CloseClipboard();
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("Native Windows clipboard write skipped, using AWT fallback: {}", e.getMessage());
        }
        return false;
    }

    private static BufferedImage toRgbImage(BufferedImage img) {
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgb.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return rgb;
    }

    private static byte[] createDIB(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int imageSize = w * h * 4;
        byte[] dib = new byte[40 + imageSize];

        // BITMAPINFOHEADER size
        dib[0] = 40;
        // Width
        dib[4] = (byte) (w & 0xFF);
        dib[5] = (byte) ((w >> 8) & 0xFF);
        dib[6] = (byte) ((w >> 16) & 0xFF);
        dib[7] = (byte) ((w >> 24) & 0xFF);
        // Height
        dib[8] = (byte) (h & 0xFF);
        dib[9] = (byte) ((h >> 8) & 0xFF);
        dib[10] = (byte) ((h >> 16) & 0xFF);
        dib[11] = (byte) ((h >> 24) & 0xFF);
        // Planes & BitCount
        dib[12] = 1;
        dib[14] = 32;
        // Image Size
        dib[20] = (byte) (imageSize & 0xFF);
        dib[21] = (byte) ((imageSize >> 8) & 0xFF);
        dib[22] = (byte) ((imageSize >> 16) & 0xFF);
        dib[23] = (byte) ((imageSize >> 24) & 0xFF);

        int index = 40;
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getRGB(x, y);
                dib[index]     = (byte) (pixel & 0xFF);         // Blue
                dib[index + 1] = (byte) ((pixel >> 8) & 0xFF);  // Green
                dib[index + 2] = (byte) ((pixel >> 16) & 0xFF); // Red
                dib[index + 3] = (byte) 255;                    // Alpha
                index += 4;
            }
        }
        return dib;
    }

    private static void fallbackAwtCopy(File file) {
        try {
            if (GraphicsEnvironment.isHeadless()) return;
            BufferedImage img = ImageIO.read(file);
            if (img == null) return;

            BufferedImage rgb = toRgbImage(img);
            Transferable transferable = new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.imageFlavor};
                }
                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }
                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    if (DataFlavor.imageFlavor.equals(flavor)) return rgb;
                    throw new UnsupportedFlavorException(flavor);
                }
            };

            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
        } catch (Exception e) {
            LOGGER.error("Failed to copy image via AWT clipboard fallback", e);
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
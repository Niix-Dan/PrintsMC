package com.niixlabs.printsmc.client.gui.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.niixlabs.printsmc.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class TextureLoader {
    private static final Map<String, DynamicTexture> LOADED_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> LOCATIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private TextureLoader() {}

    public static ResourceLocation getOrLoadTexture(File file) {
        String path = file.getAbsolutePath();
        ResourceLocation existing = LOCATIONS.get(path);
        if (existing != null) {
            return existing;
        }

        if (!LOADING.add(path)) {
            return null;
        }

        CompletableFuture.runAsync(() -> {
            try (InputStream stream = new FileInputStream(file)) {
                NativeImage image = NativeImage.read(stream);
                RenderSystem.recordRenderCall(() -> registerTextureOnRenderThread(path, file.getName(), image));
            } catch (Exception e) {
                LOADING.remove(path);
            }
        });

        return null;
    }

    private static void registerTextureOnRenderThread(String path, String fileName, NativeImage image) {
        try {
            if (!LOADING.contains(path)) {
                image.close();
                return;
            }
            DynamicTexture texture = new DynamicTexture(image);
            String safeName = fileName.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "screenshot_" + safeName);

            Minecraft.getInstance().getTextureManager().register(location, texture);
            LOADED_TEXTURES.put(path, texture);
            LOCATIONS.put(path, location);
        } catch (Exception e) {
            image.close();
        } finally {
            LOADING.remove(path);
        }
    }

    public static void unloadTexture(File file) {
        String path = file.getAbsolutePath();
        LOADING.remove(path);

        ResourceLocation location = LOCATIONS.remove(path);
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        }

        DynamicTexture texture = LOADED_TEXTURES.remove(path);
        if (texture != null) {
            texture.close();
        }
    }

    public static void clearCache() {
        LOADING.clear();

        LOCATIONS.values().forEach(loc -> Minecraft.getInstance().getTextureManager().release(loc));
        LOCATIONS.clear();

        LOADED_TEXTURES.values().forEach(DynamicTexture::close);
        LOADED_TEXTURES.clear();
    }
}
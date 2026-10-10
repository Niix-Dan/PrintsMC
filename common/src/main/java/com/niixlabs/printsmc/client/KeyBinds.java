package com.niixlabs.printsmc.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    public static final KeyMapping OPEN_GALLERY = new KeyMapping(
            "key.printsmc.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.printsmc.main"
    );
}
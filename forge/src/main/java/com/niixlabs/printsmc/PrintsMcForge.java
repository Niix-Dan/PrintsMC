package com.niixlabs.printsmc;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class PrintsMcForge {
    public PrintsMcForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PrintsMC.init();
        }
    }
}

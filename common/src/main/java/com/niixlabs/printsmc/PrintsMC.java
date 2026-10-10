package com.niixlabs.printsmc;

import com.niixlabs.printsmc.config.ModConfig;

public class PrintsMC {
    public static void init() {
        ModConfig.load();
        if (ModConfig.useConfigWatcher) ModConfig.startWatcher();
    }
}

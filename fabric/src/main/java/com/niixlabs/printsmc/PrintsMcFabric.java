package com.niixlabs.printsmc;

import net.fabricmc.api.ClientModInitializer;

public class PrintsMcFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PrintsMC.init();
    }
}

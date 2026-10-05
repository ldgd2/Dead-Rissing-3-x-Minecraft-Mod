package com.crossover.core;

import com.crossover.player.item.ModItems;
import net.fabricmc.api.ModInitializer;

public class CrossoverMain implements ModInitializer {
    @Override
    public void onInitialize() {
        CrossoverMod.LOGGER.info("[Crossover] Registrando ecosistema base (Ítems/Bloques)...");
        ModItems.initialize();
    }
}

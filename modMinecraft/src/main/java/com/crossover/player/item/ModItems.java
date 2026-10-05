package com.crossover.player.item;

import com.crossover.core.CrossoverMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registro Universal de Ítems (Pilar 3: El Camaleón).
 * Un único ítem genérico que no requiere miles de JSONs estáticos.
 */
public class ModItems {

    // El Ítem Universal (Camaleón)
    public static ItemDeadRising DR3_ITEM;

    public static void register() {
        if (DR3_ITEM == null) {
            DR3_ITEM = new ItemDeadRising(new Item.Settings().maxCount(1));
            Registry.register(
                Registries.ITEM, 
                new Identifier(CrossoverMod.MOD_ID, "dr3_item"), 
                DR3_ITEM
            );
            CrossoverMod.LOGGER.info("[ModItems] Ítem Universal 'crossover_dr3:dr3_item' registrado exitosamente.");
        }
    }

    public static void initialize() {
        register();
    }
}

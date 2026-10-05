package com.crossover.player.item;

import com.crossover.core.CrossoverMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registro Universal de Ítems (Pilar 3: El Camaleón).
 * Un único ítem genérico que muta según NBT y se expone en las pestañas creativas.
 */
public class ModItems {

    // El Ítem Universal (Camaleón)
    public static ItemDeadRising DR3_ITEM;

    public static ItemStack createDr3ItemStack(String name, String icon, int weaponId, int curDur, int maxDur, int ammo) {
        ItemStack stack = new ItemStack(DR3_ITEM);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString(ItemDeadRising.NBT_NAME, name);
        nbt.putString(ItemDeadRising.NBT_ICON, icon);
        nbt.putInt(ItemDeadRising.NBT_WEAPON_ID, weaponId);
        nbt.putInt(ItemDeadRising.NBT_DURABILITY, curDur);
        nbt.putInt(ItemDeadRising.NBT_MAX_DURABILITY, maxDur);
        if (ammo > 0) {
            nbt.putInt(ItemDeadRising.NBT_AMMO, ammo);
        }
        return stack;
    }

    public static void register() {
        if (DR3_ITEM == null) {
            DR3_ITEM = new ItemDeadRising(new Item.Settings().maxCount(1));
            Registry.register(
                Registries.ITEM, 
                new Identifier(CrossoverMod.MOD_ID, "dr3_item"), 
                DR3_ITEM
            );
            CrossoverMod.LOGGER.info("[ModItems] Ítem Universal 'crossover_dr3:dr3_item' registrado exitosamente.");

            // Exponer arsenal de Dead Rising 3 directamente en la pestaña de Combate del menú creativo
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
                entries.add(createDr3ItemStack("Motosierra de Los Perdidos", "icon_wpn_chainsaw", 101, 250, 250, 0));
                entries.add(createDr3ItemStack("Maza Sledgehammer", "icon_wpn_sledgehammer", 102, 180, 180, 0));
                entries.add(createDr3ItemStack("Sable de Luz", "icon_wpn_lightsaber", 104, 500, 500, 0));
                entries.add(createDr3ItemStack("Defiler Sledge-Axe", "icon_wpn_defiler", 105, 300, 300, 0));
                entries.add(createDr3ItemStack("Freedom Bear Sentry", "icon_wpn_freedombear", 106, 150, 150, 1000));
                entries.add(createDr3ItemStack("Espada Dragón Llameante", "icon_wpn_dragonsword", 107, 400, 400, 0));
                entries.add(createDr3ItemStack("Super B.F.G. 9000", "icon_wpn_superbfg", 108, 200, 200, 50));
                entries.add(createDr3ItemStack("Bate con Clavos", "icon_wpn_spikedbat", 109, 150, 150, 0));
            });

            // Exponer herramientas DR3 en la pestaña de Herramientas
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
                entries.add(createDr3ItemStack("Motosierra de Los Perdidos", "icon_wpn_chainsaw", 101, 250, 250, 0));
                entries.add(createDr3ItemStack("Maza Sledgehammer", "icon_wpn_sledgehammer", 102, 180, 180, 0));
                entries.add(createDr3ItemStack("Defiler Sledge-Axe", "icon_wpn_defiler", 105, 300, 300, 0));
            });
        }
    }

    public static void initialize() {
        register();
    }
}

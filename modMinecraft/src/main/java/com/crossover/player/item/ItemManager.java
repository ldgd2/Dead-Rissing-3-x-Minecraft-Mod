package com.crossover.player.item;

import com.crossover.ipc.Win32SharedMemory;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Gestor del inventario dinámico (Pilar 3: El Camaleón).
 * Transfiere ítems universales con tags NBT y actualiza la durabilidad en tiempo real.
 */
public class ItemManager {

    private final Win32SharedMemory ipc;
    private int lastProcessedLootCounter = 0;

    public ItemManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
    }

    /**
     * Ciclo lógico a 20 TPS.
     */
    public void tick(ClientPlayerEntity player) {
        if (ipc == null || !ipc.isConnected() || player == null) return;

        // 1. Notificar a DR3 si el ítem en la mano activa es de Minecraft (vs arma de DR3 / puño)
        ItemStack activeStack = player.getMainHandStack();
        boolean isMcItem = !activeStack.isEmpty() && !(activeStack.getItem() instanceof ItemDeadRising);
        ipc.writeActiveHeldItemIsMc(isMcItem);

        // 2. Procesar orden de lanzamiento con Q disparada desde Dead Rising 3
        if (ipc.consumeActionQTrigger()) {
            if (isMcItem) {
                System.out.println("[ItemManager] Q presionado en DR3 con ítem Minecraft. Arrojando ítem...");
                player.dropSelectedItem(false);
            }
        }

        // 3. Procesar orden de Clic Izquierdo (Ataque con espada / herramienta de Minecraft)
        if (ipc.consumeActionLeftClick()) {
            if (isMcItem) {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client.interactionManager != null) {
                    player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    if (client.targetedEntity != null) {
                        client.interactionManager.attackEntity(player, client.targetedEntity);
                    }
                }
            }
        }

        // 4. Procesar orden de Clic Derecho nativo disparada desde Dead Rising 3 (Comida / Bloques / Escudo)
        if (ipc.readActionRightClick()) {
            ipc.consumeActionRightClick();
            if (isMcItem) {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client.interactionManager != null) {
                    client.interactionManager.interactItem(player, net.minecraft.util.Hand.MAIN_HAND);
                    player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
            }
        }

        // 4. Sincronización en tiempo real de la barrita de durabilidad vía NBT
        syncActiveItemDurability(player);

        // 5. Procesamiento de recolección de armas de Los Perdidos (GIVE_ITEM)
        processLootPickup(player);
    }

    /**
     * Sincronización de durabilidad:
     * Lee la firma 83 79 10 00 interceptada por la DLL en C++ y actualiza
     * el tag NBT_DURABILITY del ítem universal en la mano de Steve.
     */
    public void syncActiveItemDurability(ClientPlayerEntity player) {
        ItemStack activeStack = player.getMainHandStack();
        if (activeStack.isEmpty() || !(activeStack.getItem() instanceof ItemDeadRising)) return;

        int currentCondition = ipc.readWeaponDurability();
        NbtCompound nbt = activeStack.getOrCreateNbt();

        if (nbt.contains(ItemDeadRising.NBT_DURABILITY)) {
            int oldCondition = nbt.getInt(ItemDeadRising.NBT_DURABILITY);
            if (oldCondition != currentCondition && currentCondition >= 0) {
                nbt.putInt(ItemDeadRising.NBT_DURABILITY, currentCondition);
                // Si el arma se rompe en DR3 (condición 0), reproducir sonido y retirar de la mano
                if (currentCondition == 0 && oldCondition > 0) {
                    player.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    player.getInventory().setStack(player.getInventory().selectedSlot, ItemStack.EMPTY);
                    player.sendMessage(Text.literal("§c¡Tu arma de Los Perdidos se ha destruido!"), true);
                }
            }
        }
    }

    /**
     * Recolección dinámica (Loot):
     * La DLL manda {Nombre, Icono_ID, Durabilidad} por IPC.
     * Fabric crea una instancia de ItemDeadRising, le inyecta las etiquetas NBT
     * y el ítem se EQUIPA DIRECTAMENTE en la mano activa de Steve para sincronización 1:1 en tiempo real.
     */
    public void processLootPickup(ClientPlayerEntity player) {
        int currentLootCounter = ipc.readLootCounter();

        if (currentLootCounter > lastProcessedLootCounter) {
            lastProcessedLootCounter = currentLootCounter;

            int itemId = ipc.readLootItemId();
            int durability = ipc.readLootDurability();
            int maxDurability = ipc.readLootMaxDurability();
            int ammo = ipc.readLootAmmo();
            String displayName = ipc.readLootDisplayName();
            String iconId = ipc.readLootIconId();

            // Pura extracción de memoria en tiempo real (Cero mapeos estáticos)
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Objeto DR3 #" + itemId;
            }
            if (iconId == null || iconId.trim().isEmpty()) {
                iconId = "icon_wpn_" + itemId;
            }
            if (maxDurability <= 0) {
                maxDurability = (durability > 0) ? durability : 100;
            }

            // 1. Instanciar el Ítem Universal
            ItemStack lootStack = new ItemStack(ModItems.DR3_ITEM);

            // 2. Inyectar etiquetas NBT dinámicas
            NbtCompound nbt = lootStack.getOrCreateNbt();
            nbt.putString(ItemDeadRising.NBT_NAME, displayName);
            nbt.putString(ItemDeadRising.NBT_ICON, iconId);
            nbt.putInt(ItemDeadRising.NBT_DURABILITY, durability);
            nbt.putInt(ItemDeadRising.NBT_MAX_DURABILITY, maxDurability);
            nbt.putInt(ItemDeadRising.NBT_WEAPON_ID, itemId);
            if (ammo > 0) {
                nbt.putInt(ItemDeadRising.NBT_AMMO, ammo);
            }

            // 3. Sincronización Real: Equipar DIRECTAMENTE en la mano activa de Steve (selectedSlot)
            int selectedSlot = player.getInventory().selectedSlot;
            ItemStack oldStack = player.getInventory().getStack(selectedSlot);
            if (!oldStack.isEmpty()) {
                // Guardar el ítem anterior en un slot libre del inventario para conservarlo
                player.getInventory().setStack(selectedSlot, ItemStack.EMPTY);
                if (!player.getInventory().insertStack(oldStack)) {
                    player.dropItem(oldStack, false);
                }
            }
            player.getInventory().setStack(selectedSlot, lootStack);

            // 4. Feedback audiovisual
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            player.sendMessage(
                Text.literal("§6[Mano Sincronizada] §f¡Equipado: §e" + displayName + 
                             " §7(Condición: " + durability + "/" + maxDurability + ")!"), 
                true
            );

            System.out.println("[ItemManager] Arma sincronizada directamente en la mano de Steve -> " + displayName + 
                               " [Icon: " + iconId + "] (Dur: " + durability + "/" + maxDurability + ")");
        }
    }
}

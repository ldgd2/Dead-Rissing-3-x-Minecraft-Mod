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

        // 3. Procesar orden de Clic Izquierdo (Ataque con espada / herramienta / arma DR3 / puño / minar bloques)
        if (ipc.consumeActionLeftClick()) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (client.interactionManager != null && client.targetedEntity != null) {
                client.interactionManager.attackEntity(player, client.targetedEntity);
            } else if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bHit) {
                net.minecraft.util.math.BlockPos hitPos = bHit.getBlockPos();
                if (!player.getWorld().isAir(hitPos) && player.getWorld().getBlockState(hitPos).getBlock() != net.minecraft.block.Blocks.BARRIER) {
                    if (player.isCreative()) {
                        player.getWorld().breakBlock(hitPos, false);
                    } else if (client.interactionManager != null) {
                        client.interactionManager.attackBlock(hitPos, bHit.getSide());
                    }
                }
            }
        }

        // 4. Procesar orden de Clic Derecho (Comida / Colocación de Bloques / Uso de Armas)
        if (ipc.readActionRightClick()) {
            ipc.consumeActionRightClick();
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            ItemStack held = player.getMainHandStack();
            if (held.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
                net.minecraft.util.math.BlockPos placePos = null;
                if (client != null && client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bHit) {
                    net.minecraft.util.math.BlockPos hit = bHit.getBlockPos();
                    if (!player.getWorld().isAir(hit)) {
                        placePos = hit.offset(bHit.getSide());
                    }
                }
                if (placePos == null) {
                    net.minecraft.util.math.Vec3d eyePos = player.getEyePos();
                    net.minecraft.util.math.Vec3d lookVec = player.getRotationVector();
                    net.minecraft.util.math.Vec3d target = eyePos.add(lookVec.multiply(2.5));
                    placePos = new net.minecraft.util.math.BlockPos(
                        (int) Math.floor(target.x),
                        (int) Math.floor(target.y),
                        (int) Math.floor(target.z)
                    );
                }
                net.minecraft.block.Block block = blockItem.getBlock();
                player.getWorld().setBlockState(placePos, block.getDefaultState(), 3);
                if (com.crossover.core.CrossoverMod.worldSyncManager != null) {
                    com.crossover.core.CrossoverMod.worldSyncManager.onBlockPlaced(
                        placePos.getX(), placePos.getY(), placePos.getZ(),
                        net.minecraft.registry.Registries.BLOCK.getId(block).toString(),
                        block.getDefaultState()
                    );
                }
                player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                player.playSound(block.getDefaultState().getSoundGroup().getPlaceSound(), 1.0f, 1.0f);
                if (!player.isCreative()) {
                    held.decrement(1);
                }
            } else {
                if (client != null && client.interactionManager != null) {
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

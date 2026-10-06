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

        // 3. Procesar orden de Clic Izquierdo (Ataque con espada / puño / mechero / poción / minar bloques)
        if (ipc.consumeActionLeftClick()) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            ItemStack held = player.getMainHandStack();

            // A) Minar bloques colocados por el jugador
            if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bHit) {
                net.minecraft.util.math.BlockPos hitPos = bHit.getBlockPos();
                if (!player.getWorld().isAir(hitPos) && player.getWorld().getBlockState(hitPos).getBlock() != net.minecraft.block.Blocks.BARRIER) {
                    player.getWorld().breakBlock(hitPos, true);
                    if (client.getServer() != null) {
                        net.minecraft.server.world.ServerWorld sWorld = client.getServer().getWorld(player.getWorld().getRegistryKey());
                        if (sWorld != null) sWorld.breakBlock(hitPos, true);
                    }
                    if (com.crossover.core.CrossoverMod.worldSyncManager != null) {
                        com.crossover.core.CrossoverMod.worldSyncManager.onBlockBroken(hitPos.getX(), hitPos.getY(), hitPos.getZ());
                    }
                    return;
                }
            }

            // B) Ataque contra entidades o zombies de Dead Rising 3
            if (held.getItem() == net.minecraft.item.Items.FLINT_AND_STEEL) {
                // Prender fuego a los zombies con el Mechero
                player.playSound(net.minecraft.sound.SoundEvents.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
                ipc.sendZombieCombatAction(4, (float) player.getX(), (float) player.getY(), (float) player.getZ(), 50.0f);
            } else if (held.getItem() == net.minecraft.item.Items.SPLASH_POTION) {
                // Poción arrojadiza de daño
                player.playSound(net.minecraft.sound.SoundEvents.ENTITY_SPLASH_POTION_THROW, 1.0f, 1.0f);
                ipc.sendZombieCombatAction(3, (float) player.getX(), (float) player.getY(), (float) player.getZ(), 80.0f);
            } else {
                // Golpe melee (Espada, hacha o puños)
                float dmg = 25.0f;
                if (held.getItem() == net.minecraft.item.Items.DIAMOND_SWORD) dmg = 55.0f;
                player.playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                ipc.sendZombieCombatAction(1, (float) player.getX(), (float) player.getY(), (float) player.getZ(), dmg);
            }

            if (client.interactionManager != null && client.targetedEntity != null) {
                client.interactionManager.attackEntity(player, client.targetedEntity);
            }
        }

        // 4. Procesar orden de Clic Derecho (Comida / Colocación de Bloques / Mechero / Pociones / Encendido de TNT)
        if (ipc.readActionRightClick()) {
            ipc.consumeActionRightClick();
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            ItemStack held = player.getMainHandStack();

            // A) Mechero: Encendido de TNT o prender fuego a zombies
            if (held.getItem() == net.minecraft.item.Items.FLINT_AND_STEEL) {
                if (client != null && client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bHit) {
                    net.minecraft.util.math.BlockPos hit = bHit.getBlockPos();
                    if (player.getWorld().getBlockState(hit).getBlock() == net.minecraft.block.Blocks.TNT) {
                        player.getWorld().removeBlock(hit, false);
                        if (client.getServer() != null) {
                            net.minecraft.server.world.ServerWorld sWorld = client.getServer().getWorld(player.getWorld().getRegistryKey());
                            if (sWorld != null) sWorld.removeBlock(hit, false);
                        }
                        net.minecraft.entity.TntEntity tntEntity = new net.minecraft.entity.TntEntity(
                                player.getWorld(), (double) hit.getX() + 0.5, (double) hit.getY(), (double) hit.getZ() + 0.5, player
                        );
                        player.getWorld().spawnEntity(tntEntity);
                        player.getWorld().playSound(null, tntEntity.getX(), tntEntity.getY(), tntEntity.getZ(),
                                net.minecraft.sound.SoundEvents.ENTITY_TNT_PRIMED, net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
                        if (com.crossover.core.CrossoverMod.worldSyncManager != null) {
                            com.crossover.core.CrossoverMod.worldSyncManager.onBlockBroken(hit.getX(), hit.getY(), hit.getZ());
                        }
                        player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                        return;
                    }
                }
                // Prender fuego en el juego
                player.playSound(net.minecraft.sound.SoundEvents.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
                ipc.sendZombieCombatAction(4, (float) player.getX(), (float) player.getY(), (float) player.getZ(), 50.0f);
                player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                return;
            }

            // B) Poción de Daño Arrojadiza
            if (held.getItem() == net.minecraft.item.Items.SPLASH_POTION) {
                player.playSound(net.minecraft.sound.SoundEvents.ENTITY_SPLASH_POTION_THROW, 1.0f, 1.0f);
                ipc.sendZombieCombatAction(3, (float) player.getX(), (float) player.getY(), (float) player.getZ(), 85.0f);
                player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                return;
            }

            // C) Colocación de Bloques con Raycast Preciso sobre el Asfalto de Los Perdidos
            if (held.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
                net.minecraft.util.math.BlockPos placePos = null;

                // 1. Si apunta a un bloque previamente colocado
                if (client != null && client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bHit) {
                    net.minecraft.util.math.BlockPos hit = bHit.getBlockPos();
                    if (!player.getWorld().isAir(hit) && player.getWorld().getBlockState(hit).getBlock() != net.minecraft.block.Blocks.BARRIER) {
                        placePos = hit.offset(bHit.getSide());
                    }
                }

                // 2. Si mira hacia abajo mientras salta (Pillar Jump / Nerd Poling)
                if (placePos == null && player.getPitch() > 55.0f) {
                    placePos = new net.minecraft.util.math.BlockPos(
                            (int) Math.floor(player.getX()),
                            (int) Math.floor(player.getY()) - 1,
                            (int) Math.floor(player.getZ())
                    );
                }

                // 3. Raycast analítico de intersección entre la vista y el plano del asfalto (floorY)
                if (placePos == null) {
                    net.minecraft.util.math.Vec3d eye = player.getEyePos();
                    net.minecraft.util.math.Vec3d look = player.getRotationVector();
                    if (look.y < -0.05) {
                        double floorY = Math.floor(player.getY());
                        double t = (floorY - eye.y) / look.y;
                        if (t > 0 && t <= 6.5) {
                            double hitX = eye.x + look.x * t;
                            double hitZ = eye.z + look.z * t;
                            placePos = new net.minecraft.util.math.BlockPos((int) Math.floor(hitX), (int) floorY, (int) Math.floor(hitZ));
                        }
                    }
                }

                // 4. Fallback frontal directo
                if (placePos == null) {
                    net.minecraft.util.math.Vec3d eye = player.getEyePos();
                    net.minecraft.util.math.Vec3d look = player.getRotationVector();
                    net.minecraft.util.math.Vec3d target = eye.add(look.multiply(2.5));
                    placePos = new net.minecraft.util.math.BlockPos((int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z));
                }

                net.minecraft.block.Block block = blockItem.getBlock();
                net.minecraft.block.BlockState bState = block.getDefaultState();
                player.getWorld().setBlockState(placePos, bState, 3);
                if (client != null && client.getServer() != null) {
                    final net.minecraft.util.math.BlockPos targetPos = placePos;
                    client.getServer().execute(() -> {
                        net.minecraft.server.world.ServerWorld sWorld = client.getServer().getWorld(player.getWorld().getRegistryKey());
                        if (sWorld != null) {
                            sWorld.setBlockState(targetPos, bState, 3);
                        }
                    });
                }

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

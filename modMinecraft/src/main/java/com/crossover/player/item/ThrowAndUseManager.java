package com.crossover.player.item;

import com.crossover.ipc.Win32SharedMemory;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pilar 4: Lanzamiento de Armas y Uso de Ítems (Físicas Havok).
 * Gestiona el disparo con munición, consumo de alimentos y el lanzamiento de herramientas
 * como proyectiles físicos letales con rotación especializada en Dead Rising 3.
 */
public class ThrowAndUseManager {

    private final Win32SharedMemory ipc;
    private int lastProcessedLandedCounter = 0;

    // Ítems de Minecraft que actualmente vuelan con físicas Havok en DR3
    private static final Set<Integer> FLYING_HAVOK_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, ItemEntity> PENDING_LANDING_ENTITIES = new ConcurrentHashMap<>();

    public ThrowAndUseManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
    }

    /**
     * Ciclo lógico a 20 TPS.
     */
    public void tick(ClientPlayerEntity player) {
        if (ipc == null || !ipc.isConnected() || player == null) return;

        // Comprobar si DR3 reportó el aterrizaje o impacto de un proyectil arrojado
        processLandedEvent(player);
    }

    /**
     * Determina si la entidad de ítem está volando como proyectil Havok en DR3
     * para suprimir el renderizado flotante vanilla hasta que toque el suelo.
     */
    public static boolean isFlyingInHavok(int entityId) {
        return FLYING_HAVOK_ENTITIES.contains(entityId);
    }

    /**
     * Invocado cuando el jugador presiona Q en Minecraft para tirar un ítem.
     */
    public void onPlayerDropItem(ClientPlayerEntity player, ItemStack stack, ItemEntity spawnedEntity) {
        if (ipc == null || !ipc.isConnected() || stack.isEmpty()) return;

        Item item = stack.getItem();
        boolean isDr3Weapon = (item instanceof ItemDeadRising);
        boolean isMcItem = !isDr3Weapon;

        // 1. Clasificar el tipo de proyectil para que DR3 aplique los cuaterniones de giro correctos
        int throwType;
        if (isDr3Weapon) {
            throwType = 7; // THROW_DR3_WEAPON
        } else if (item instanceof SwordItem) {
            throwType = 1; // SWORD (vuelo en dardo o corte horizontal)
        } else if (item instanceof AxeItem) {
            throwType = 2; // AXE (giro vertical estilo tomahawk)
        } else if (item instanceof ShovelItem) {
            throwType = 3; // SHOVEL (tumbling pesado)
        } else if (item instanceof PickaxeItem || item instanceof HoeItem) {
            throwType = 4; // PICKAXE (rotación excéntrica)
        } else if (item.isFood()) {
            throwType = 5; // FOOD (lanzamiento ligero)
        } else if (item instanceof BlockItem) {
            throwType = 6; // BLOCK (objeto cúbico tumbling)
        } else {
            throwType = 6; // BLOCK / PROP GENÉRICO
        }

        // 2. Extraer origen visual (altura de los ojos) y vector de mirada (Yaw & Pitch)
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVector();

        String itemName = stack.getName().getString();

        // 3. Registrar entidad de Minecraft para sincronizar el aterrizaje
        if (spawnedEntity != null && isMcItem) {
            int entityId = spawnedEntity.getId();
            FLYING_HAVOK_ENTITIES.add(entityId);
            PENDING_LANDING_ENTITIES.put(entityId, spawnedEntity);
            // Suspender movimiento vanilla en el aire
            spawnedEntity.setVelocity(lookVec.x * 1.5, lookVec.y * 1.5, lookVec.z * 1.5);
        }

        // 4. Enviar orden balística por Memoria Compartida a DR3Hook
        ipc.sendThrowAction(
            throwType,
            eyePos.x, eyePos.y, eyePos.z,
            lookVec.x, lookVec.y, lookVec.z,
            isMcItem,
            itemName
        );

        // 5. Feedback auditivo inmediato
        player.getWorld().playSound(
            player, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS,
            0.9f, 0.7f + (float)(Math.random() * 0.4f)
        );

        System.out.println("[ThrowManager] Q arrojado -> '" + itemName + "' Tipo: " + throwType + " (isMcItem=" + isMcItem + ")");
    }

    /**
     * Sincroniza el momento exacto en que el proyectil golpea a un zombie o cae al suelo en DR3.
     */
    private void processLandedEvent(ClientPlayerEntity player) {
        int currentCounter = ipc.readLandedEventCounter();
        if (currentCounter > lastProcessedLandedCounter) {
            lastProcessedLandedCounter = currentCounter;

            float lx = ipc.readLandedX();
            float ly = ipc.readLandedY();
            float lz = ipc.readLandedZ();
            boolean hitZombie = ipc.readLandedHitZombie();
            float damage = ipc.readLandedDamage();

            // 1. Feedback de impacto si golpeó a un zombie
            if (hitZombie) {
                player.sendMessage(
                    Text.literal("§c[Impacto Havok] §f¡Proyectil golpeó a un zombie! Daño: §e" + String.format("%.1f", damage)),
                    true
                );
                player.getWorld().playSound(
                    player, lx, ly, lz,
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS,
                    1.2f, 0.9f
                );
                // Partículas de sangre / impacto crítico
                for (int i = 0; i < 8; i++) {
                    player.getWorld().addParticle(
                        ParticleTypes.CRIT,
                        lx + (Math.random() - 0.5) * 0.4,
                        ly + 0.5 + Math.random() * 0.4,
                        lz + (Math.random() - 0.5) * 0.4,
                        0.0, 0.1, 0.0
                    );
                }
            }

            // 2. Re-habilitar flotación/hover de los ítems de Minecraft una vez asentados
            for (Map.Entry<Integer, ItemEntity> entry : PENDING_LANDING_ENTITIES.entrySet()) {
                ItemEntity itemEntity = entry.getValue();
                if (itemEntity != null && itemEntity.isAlive()) {
                    // Reposicionar en la coordenada exacta de aterrizaje de DR3
                    itemEntity.setPosition(lx, ly, lz);
                    itemEntity.setVelocity(0.0, 0.05, 0.0);
                    FLYING_HAVOK_ENTITIES.remove(entry.getKey());
                }
            }
            PENDING_LANDING_ENTITIES.clear();
        }
    }

    /**
     * Invocado al usar un ítem (Clic Derecho).
     */
    public void onUseItem(ClientPlayerEntity player, ItemStack stack) {
        if (ipc == null || !ipc.isConnected() || stack.isEmpty()) return;

        // Disparo con Arma de Fuego
        if (stack.getItem() instanceof ItemDeadRising) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null && nbt.contains(ItemDeadRising.NBT_AMMO)) {
                int ammo = nbt.getInt(ItemDeadRising.NBT_AMMO);
                if (ammo > 0) {
                    // 1. Enviar orden de disparo a DR3
                    ipc.sendUseAction(1); // ACTION_FIRE

                    // 2. Restar munición y actualizar NBT
                    ammo--;
                    nbt.putInt(ItemDeadRising.NBT_AMMO, ammo);

                    // 3. Efectos de disparo
                    player.getWorld().playSound(
                        player, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                        0.7f, 1.8f
                    );
                    return;
                }
            }
        }

        // Consumo de Comida
        if (stack.isFood()) {
            ipc.sendUseAction(2); // ACTION_EAT
            player.getWorld().playSound(
                player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS,
                0.8f, 1.0f
            );
        }
    }
}

package com.crossover.combat;

import com.crossover.ipc.Win32SharedMemory;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pilar 5+: Gestor de Combate de Mobs y Hordas de Zombies LOD (Optimizado).
 * 
 * Permite que los Mobs de Minecraft (como Golems de Hierro, Lobos, etc.) luchen
 * activamente contra los zombies de Dead Rising 3 sin sobrecargar Minecraft:
 * 
 * 1. Mantiene una 'Burbuja de Combate LOD' con un límite estricto de máximo 6 a 8 zombies
 *    fantasma invisibles (proxy) en el radio cercano del Golem / Jugador.
 * 2. Los zombies proxy son invisibles (setInvisible(true)) para no generar artefactos visuales,
 *    pero tienen colisiones e IA física completa.
 * 3. Cuando un Golem de Hierro detecta, golpea y mata a un zombie proxy:
 *    Se envía inmediatamente un paquete de combate a DR3Hook para ejecutar la muerte,
 *    desmembramiento y sangre del zombie correspondiente en Dead Rising 3.
 * 4. Limpia instantáneamente los zombies lejanos para garantizar 60 FPS estables sin importar
 *    que Dead Rising 3 tenga miles de zombies en pantalla.
 */
public class CombatMobManager {

    private final Win32SharedMemory ipc;
    private final Set<ZombieEntity> activeProxyZombies = new HashSet<>();
    private final Random random = new Random();
    private int tickCounter = 0;

    // Límite estricto de entidades simultáneas en Minecraft (Anti-Lag LOD)
    private static final int MAX_PROXY_ZOMBIES = 8;
    private static final double COMBAT_RADIUS = 16.0;
    private static final double DESPAWN_RADIUS = 24.0;

    public CombatMobManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
    }

    /**
     * Llamado cada tick lógico del cliente (20 TPS).
     */
    public void tick(ClientPlayerEntity player) {
        if (player == null || !(player.getWorld() instanceof ClientWorld world) || ipc == null || !ipc.isConnected()) {
            return;
        }

        tickCounter++;

        // 1. Monitorear zombies proxy existentes: detectar daño o bajas infligidas por Golems
        Iterator<ZombieEntity> it = activeProxyZombies.iterator();
        while (it.hasNext()) {
            ZombieEntity zombie = it.next();

            // Si el zombie ha sido eliminado o ha muerto
            if (!zombie.isAlive() || zombie.isDead()) {
                // Notificar baja a Dead Rising 3 para causar decapitación / desmembramiento
                ipc.sendZombieCombatAction(
                        2, // 2 = KILL
                        (float) zombie.getX(),
                        (float) zombie.getY(),
                        (float) zombie.getZ(),
                        50.0f
                );
                it.remove();
                continue;
            }

            // Si el zombie está muy lejos del jugador, descartar limpiamente (Optimización LOD)
            double distSq = zombie.squaredDistanceTo(player);
            if (distSq > DESPAWN_RADIUS * DESPAWN_RADIUS) {
                zombie.discard();
                it.remove();
                continue;
            }

            // Si un Golem le ha bajado la vida recientemente
            if (zombie.hurtTime > 0 && zombie.hurtTime == zombie.maxHurtTime) {
                ipc.sendZombieCombatAction(
                        1, // 1 = HIT
                        (float) zombie.getX(),
                        (float) zombie.getY(),
                        (float) zombie.getZ(),
                        15.0f
                );
            }
        }

        // 2. Gestionar la población de la Burbuja LOD cada 15 ticks (~0.75 segundos)
        if (tickCounter % 15 == 0) {
            maintainProxyZombiePool(world, player);
        }
    }

    /**
     * Mantiene la cantidad adecuada de zombies proxy si hay un Golem de Hierro activo
     * o si Steve está explorando la horda.
     */
    private void maintainProxyZombiePool(ClientWorld world, ClientPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(COMBAT_RADIUS);
        List<IronGolemEntity> nearbyGolems = world.getEntitiesByClass(
                IronGolemEntity.class,
                searchBox,
                IronGolemEntity::isAlive
        );

        // Si hay al menos un Golem de Hierro cerca, asegurar que tenga zombies a los que enfrentarse
        boolean hasActiveGolem = !nearbyGolems.isEmpty();

        if (hasActiveGolem && activeProxyZombies.size() < MAX_PROXY_ZOMBIES) {
            IronGolemEntity focusGolem = nearbyGolems.get(0);
            spawnProxyZombieNear(world, focusGolem.getX(), focusGolem.getY(), focusGolem.getZ());
        }
    }

    /**
     * Spawnea un zombie proxy 'a escondidas' (invisible) sobre la superficie sólida.
     */
    private void spawnProxyZombieNear(ClientWorld world, double cx, double cy, double cz) {
        // Posicionar a una distancia de 5 a 10 metros del objetivo
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = 5.0 + (random.nextDouble() * 5.0);

        double spawnX = cx + Math.cos(angle) * dist;
        double spawnZ = cz + Math.sin(angle) * dist;
        int blockY = (int) Math.floor(cy);

        BlockPos spawnPos = new BlockPos((int) Math.floor(spawnX), blockY, (int) Math.floor(spawnZ));

        // Validar que el espacio esté libre
        if (world.isAir(spawnPos) && world.isAir(spawnPos.up())) {
            ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            if (zombie != null) {
                zombie.refreshPositionAndAngles(spawnX, cy, spawnZ, random.nextFloat() * 360.0f, 0.0f);

                // Configuración Invisible y Sigilosa (La técnica a escondidas pedida por el usuario)
                zombie.setInvisible(true); // 100% invisible para no superponer texturas sobre el renderizado de DR3
                zombie.setSilent(false);   // Conserva gruñidos para ambientación inmersiva
                zombie.setCustomName(Text.literal("DR3_Proxy_Zombie"));
                zombie.setCustomNameVisible(false);

                world.addEntity(zombie.getId(), zombie);
                activeProxyZombies.add(zombie);
            }
        }
    }

    /**
     * Limpia todas las entidades al cerrar sesión.
     */
    public void clearAll() {
        for (ZombieEntity z : activeProxyZombies) {
            if (z.isAlive()) {
                z.discard();
            }
        }
        activeProxyZombies.clear();
    }
}

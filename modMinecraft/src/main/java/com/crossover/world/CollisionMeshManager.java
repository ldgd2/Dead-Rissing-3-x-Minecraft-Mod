package com.crossover.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Malla de Colisión 1:1 para el Entorno de Dead Rising 3 en Minecraft.
 * 
 * Genera y mantiene dinámicamente una superficie física sólida e invisible (Barreras)
 * debajo de los pies de Steve siguiendo con precisión métrica la elevación y coordenadas
 * del asfalto/terreno de Los Perdidos.
 * 
 * Permite que:
 * 1. Steve camine libremente por todo el mapa de Dead Rising 3 sin quedar atrapado en un cubo 1x1 ni caer al vacío.
 * 2. Haya colisiones físicas y detección de suelo (isOnGround) 1:1 con el asfalto.
 * 3. Al apuntar al suelo y hacer clic derecho, el raycast detecte la calle y coloque
 *    los bloques exactamente sobre el asfalto.
 * 4. Los bloques colocados permanezcan fijados en sus coordenadas globales 3D del mundo,
 *    eliminando el efecto de "AR barato" pegado a la cámara.
 */
public class CollisionMeshManager {

    private final Set<BlockPos> activeMeshBlocks = new HashSet<>();
    private int lastBlockX = Integer.MIN_VALUE;
    private int lastFloorY = Integer.MIN_VALUE;
    private int lastBlockZ = Integer.MIN_VALUE;

    // Radio de la malla sólida alrededor de Steve (2 bloques = cuadrícula de 5x5 = 25 bloques estables)
    private static final int MESH_RADIUS = 2;
    // Radio a partir del cual se limpian barreras lejanas para optimizar memoria y FPS
    private static final int CLEANUP_RADIUS = 6;

    /**
     * Actualiza la malla de colisión 1:1 en la posición actual del jugador sobre el asfalto.
     */
    public void update(ClientWorld world, double playerX, int floorY, double playerZ, Map<BlockPos, BlockState> playerPlacedBlocks) {
        if (world == null) return;

        int centerX = (int) Math.floor(playerX);
        int centerZ = (int) Math.floor(playerZ);

        // Si el bloque de suelo no ha cambiado, evitar regeneración innecesaria
        if (centerX == lastBlockX && floorY == lastFloorY && centerZ == lastBlockZ) {
            return;
        }

        lastBlockX = centerX;
        lastFloorY = floorY;
        lastBlockZ = centerZ;

        BlockState barrier = Blocks.BARRIER.getDefaultState();

        // 1. Desplegar malla de colisión 1:1 en el radio MESH_RADIUS (5x5 = 25 bloques ultra rápido)
        for (int dx = -MESH_RADIUS; dx <= MESH_RADIUS; dx++) {
            for (int dz = -MESH_RADIUS; dz <= MESH_RADIUS; dz++) {
                BlockPos pos = new BlockPos(centerX + dx, floorY, centerZ + dz);

                // Nunca sobreescribir bloques colocados por el jugador
                if (playerPlacedBlocks != null && playerPlacedBlocks.containsKey(pos)) {
                    continue;
                }

                BlockState current = world.getBlockState(pos);
                if (current.isAir()) {
                    world.setBlockState(pos, barrier, 18);
                    activeMeshBlocks.add(pos);
                }
            }
        }

        // 2. Limpieza de barreras lejanas cuando la malla supera 40 bloques
        if (activeMeshBlocks.size() > 40) {
            Iterator<BlockPos> it = activeMeshBlocks.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                int distSq = (pos.getX() - centerX) * (pos.getX() - centerX) + (pos.getZ() - centerZ) * (pos.getZ() - centerZ);
                if (distSq > CLEANUP_RADIUS * CLEANUP_RADIUS || Math.abs(pos.getY() - floorY) > 4) {
                    if (playerPlacedBlocks == null || !playerPlacedBlocks.containsKey(pos)) {
                        if (world.getBlockState(pos).getBlock() == Blocks.BARRIER) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 18);
                        }
                    }
                    it.remove();
                }
            }
        }
    }

    /**
     * Limpia todas las barreras activas del mundo al desmontar.
     */
    public void clearAll(ClientWorld world) {
        if (world == null) return;
        for (BlockPos pos : activeMeshBlocks) {
            if (world.getBlockState(pos).getBlock() == Blocks.BARRIER) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            }
        }
        activeMeshBlocks.clear();
    }
}

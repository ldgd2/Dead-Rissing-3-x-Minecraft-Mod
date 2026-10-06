package com.crossover.world;

import com.crossover.ipc.Win32SharedMemory;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pilar 5: Construcción y el Entorno.
 * Mantiene la sincronización bidireccional entre el mundo de Dead Rising 3 y Minecraft:
 * 1. Sincronización del Reloj: Lee las horas de DR3 (vía firma AOB 8B 41 28) y aplica
 *    world.setTimeOfDay() dinámico para oscurecer el cielo y cambiar la iluminación sobre Steve.
 * 2. Capa Visual de Bloques (Building): Registra los bloques colocados por Steve y los dibuja
 *    en un layer visual dedicado sobre el canal alfa.
 * 3. Bloqueo de Hordas: Notifica a la DLL las coordenadas exactas para instanciar cubos de
 *    colisión estáticos Havok (1x1x1m), bloqueando el avance de los zombies.
 */
public class WorldSyncManager {

    private final Win32SharedMemory ipc;
    private final Map<BlockPos, BlockState> playerPlacedBlocks = new ConcurrentHashMap<>();
    private float lastSyncedHours = -1.0f;
    private long lastLogTimeMs = 0;

    public WorldSyncManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
    }

    /**
     * Llamado cada tick lógico del cliente (20 TPS) para mantener el entorno y el cielo sincronizados.
     */
    public void tick(ClientWorld world) {
        if (world == null || ipc == null) return;

        // 1. Sincronización del Reloj DR3 -> Minecraft (Firma AOB 8B 41 28)
        float dr3Hours = ipc.readWorldTimeHours();
        float dr3Minutes = ipc.readWorldTimeMinutes();
        float totalHours = dr3Hours + (dr3Minutes / 60.0f);

        // Conversión horaria de DR3 (0.0 - 24.0) a Ticks de Minecraft (0 - 24000)
        // 06:00 DR3 = 0 ticks (Amanecer MC)
        // 12:00 DR3 = 6000 ticks (Mediodía MC)
        // 18:00 DR3 = 12000 ticks (Atardecer / Ocaso MC)
        // 00:00 DR3 = 18000 ticks (Medianoche MC)
        long mcTicks;
        if (totalHours >= 6.0f) {
            mcTicks = (long) ((totalHours - 6.0f) * 1000.0f);
        } else {
            mcTicks = (long) ((totalHours + 18.0f) * 1000.0f);
        }

        // Aplicar la hora dinámicamente al mundo del cliente
        world.setTimeOfDay(mcTicks);

        long now = System.currentTimeMillis();
        if (Math.abs(totalHours - lastSyncedHours) > 0.05f || (now - lastLogTimeMs > 10000)) {
            lastSyncedHours = totalHours;
            lastLogTimeMs = now;
            int h = (int) dr3Hours;
            int m = (int) dr3Minutes;
            System.out.printf("[WorldSync] Reloj DR3 sincronizado: %02d:%02d -> MC Ticks: %d (Iluminación y cielo adaptados)\n",
                    h, m, mcTicks);
        }
    }

    /**
     * Notificación cuando Steve coloca un bloque en el mundo.
     */
    public void onBlockPlaced(int x, int y, int z, String blockId, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        playerPlacedBlocks.put(pos, state);

        System.out.println("[WorldSync] Bloque colocado: '" + blockId + "' en (" + x + ", " + y + ", " + z 
                + "). Transmitiendo a DR3 para instanciar Cubo de Colisión Havok 1x1x1m...");

        ipc.sendBlockPlace(x, y, z, blockId);
    }

    /**
     * Notificación cuando Steve destruye un bloque en el mundo.
     */
    public void onBlockBroken(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        playerPlacedBlocks.remove(pos);

        System.out.println("[WorldSync] Bloque destruido en (" + x + ", " + y + ", " + z 
                + "). Despawnando Cubo de Colisión de DR3...");

        ipc.sendBlockBreak(x, y, z);
    }

    /**
     * Notificación cuando detona dinamita TNT en Minecraft.
     * Transmite una explosión balística y de onda expansiva masiva hacia Dead Rising 3.
     */
    public void onTntExploded(double x, double y, double z, float power) {
        if (ipc == null) return;
        System.out.println("[WorldSync] ¡DETONACIÓN DE TNT! En (" + x + ", " + y + ", " + z 
                + ") con potencia " + power + ". Despachando onda expansiva Havok a Dead Rising 3...");

        ipc.sendTntExplosion((float) x, (float) y, (float) z, power);

        // Limpiar bloques destruidos por la explosión en el radio
        int radius = (int) Math.ceil(power);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                        BlockPos p = new BlockPos((int) Math.floor(x) + dx, (int) Math.floor(y) + dy, (int) Math.floor(z) + dz);
                        playerPlacedBlocks.remove(p);
                    }
                }
            }
        }
    }

    /**
     * Renderizado del Layer Visual propio de Bloques de Construcción de Steve (Pilar 5).
     * Dibuja los adoquines, maderas y barreras con iluminación y texturas completas de Minecraft
     * sobre la calle de Los Perdidos sin ser anulados por la máscara del canal alfa.
     */
    public void renderPlacedBlocksLayer(MatrixStack matrices, Camera camera, LightmapTextureManager lightmapTextureManager, VertexConsumerProvider vertexConsumers) {
        if (playerPlacedBlocks.isEmpty() || camera == null || matrices == null || vertexConsumers == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getBlockRenderManager() == null || client.world == null) return;

        Vec3d cameraPos = camera.getPos();

        for (Map.Entry<BlockPos, BlockState> entry : playerPlacedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            matrices.push();
            // Trasladar a la coordenada relativa a la cámara
            double relX = (double) pos.getX() - cameraPos.x;
            double relY = (double) pos.getY() - cameraPos.y;
            double relZ = (double) pos.getZ() - cameraPos.z;
            matrices.translate(relX, relY, relZ);

            int light = WorldRendererAccessor.getLight(client.world, pos);

            // Dibujar el bloque en el layer visual usando el BlockRenderManager de Minecraft
            client.getBlockRenderManager().renderBlockAsEntity(
                    state,
                    matrices,
                    vertexConsumers,
                    light,
                    OverlayTexture.DEFAULT_UV
            );

            matrices.pop();
        }
    }

    public Map<BlockPos, BlockState> getPlayerPlacedBlocks() {
        return playerPlacedBlocks;
    }

    /**
     * Helper de conveniencia para consultar nivel de luz.
     */
    private static class WorldRendererAccessor {
        public static int getLight(ClientWorld world, BlockPos pos) {
            if (world == null) return 15728880; // Máxima iluminación
            return LightmapTextureManager.pack(
                    world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos),
                    world.getLightLevel(net.minecraft.world.LightType.SKY, pos)
            );
        }
    }
}

package com.crossover.player.health;

import com.crossover.ipc.Win32SharedMemory;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Gestor de Salud y Muerte Cruzada (Pilar Vital).
 * Mantiene sincronizados los puntos de vida de DR3 (Nick Ramos) con los corazones de Steve.
 * Si Nick muere, Steve muere; si Steve muere, Nick muere (manteniendo siempre el inventario).
 */
public class HealthManager {

    private final Win32SharedMemory ipc;
    private float lastSyncedMcHealth = 20.0f;
    private boolean wasDead = false;

    public HealthManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
    }

    public void syncHealth(ClientPlayerEntity player) {
        if (ipc == null || !ipc.isConnected() || player == null) return;

        // Si el jugador está en modo creativo, jamás debe morir por sincronización
        if (player.isCreative()) {
            wasDead = false;
            ipc.clearNickDead();
            return;
        }

        // 1. Detección de Muerte de Steve en Minecraft
        if (player.isDead() || player.getHealth() <= 0.0f) {
            if (!wasDead) {
                wasDead = true;
                System.out.println("[HealthManager] Steve ha muerto en Minecraft -> Enviando señal de muerte a Nick en DR3.");
                ipc.writeSteveDead(true);
            }
            return;
        }

        // Si Steve acaba de revivir
        if (wasDead && !player.isDead() && player.getHealth() > 0.0f) {
            wasDead = false;
            ipc.writeSteveDead(false);
            ipc.clearNickDead();
            lastSyncedMcHealth = player.getHealth();
            System.out.println("[HealthManager] Steve ha reaparecido con vida completa e inventario intacto.");
        }

        // 2. Detección de Muerte de Nick en Dead Rising 3
        if (ipc.readNickDead()) {
            ipc.clearNickDead(); // Consumir de inmediato para evitar bucle continuo
            if (!player.isDead() && !player.isCreative()) {
                System.out.println("[HealthManager] Nick ha muerto en DR3 -> Eliminando a Steve en Minecraft.");
                player.kill();
                wasDead = true;
            }
            return;
        }

        // 3. Sincronización cuando Dead Rising 3 actualiza la vida (Nick recibe daño de zombies/jefes)
        if (ipc.readDr3HealthChanged()) {
            float dr3HealthPercent = ipc.readPlayerHealth();
            if (dr3HealthPercent <= 0.0f) {
                if (!player.isDead() && !player.isCreative()) {
                    player.kill();
                    wasDead = true;
                }
                return;
            }
            float targetMcHealth = Math.max(1.0f, (dr3HealthPercent / 100.0f) * player.getMaxHealth());
            player.setHealth(targetMcHealth);
            lastSyncedMcHealth = targetMcHealth;
            return;
        }

        // 4. Sincronización cuando Minecraft actualiza la vida (Steve recibe daño o regenera)
        float currentMcHealth = player.getHealth();
        if (Math.abs(currentMcHealth - lastSyncedMcHealth) >= 0.5f) {
            lastSyncedMcHealth = currentMcHealth;
            float mcHealthPercent = (currentMcHealth / player.getMaxHealth()) * 100.0f;
            ipc.writePlayerHealth(mcHealthPercent);
        }
    }
}

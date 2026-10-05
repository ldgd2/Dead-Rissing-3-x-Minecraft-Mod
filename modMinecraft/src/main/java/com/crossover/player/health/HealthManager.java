package com.crossover.player.health;

import com.crossover.ipc.Win32SharedMemory;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundEvents;

/**
 * Gestor de Salud, Corazones Dorados (Absorción) y Muerte Cruzada (Pilar Vital).
 * Mantiene sincronizados los puntos de vida de DR3 (Nick Ramos) con los corazones de Steve.
 * - 1000 HP base de Nick = 10 Corazones Rojos (20.0 HP de Steve).
 * - Mejoras de vida de Nick (> 1000 HP) = Corazones Dorados de Absorción (ej. 1500 HP = +5 corazones dorados).
 * - Si un zombie daña a Nick en DR3, Steve pierde corazones proporcionalmente y reproduce efecto de daño.
 * - Si Nick o Steve mueren, la muerte es bidireccional respetando el inventario.
 */
public class HealthManager {

    private final Win32SharedMemory ipc;
    private float lastSyncedTotalHealth = 20.0f;
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
            lastSyncedTotalHealth = player.getHealth() + player.getAbsorptionAmount();
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

        // 3. Sincronización cuando Dead Rising 3 actualiza la vida (Nick recibe daño de zombies o sube de nivel)
        if (ipc.readDr3HealthChanged()) {
            float dr3MaxHp = ipc.readDr3MaxHealth();
            if (dr3MaxHp < 100.0f) dr3MaxHp = 1000.0f;

            float dr3Hp = ipc.readDr3RawHealth();
            if (dr3Hp <= 0.0f) {
                float dr3HealthPercent = ipc.readPlayerHealth();
                dr3Hp = (dr3HealthPercent / 100.0f) * dr3MaxHp;
            }

            if (dr3Hp <= 0.0f) {
                if (!player.isDead() && !player.isCreative()) {
                    player.kill();
                    wasDead = true;
                }
                return;
            }

            // Cálculo de Corazones Rojos y Corazones Dorados (Absorción)
            // Base Nick = 1000 HP -> 20.0 HP Steve (10 corazones rojos)
            // Vida extra por mejoras (> 1000 HP) -> Corazones dorados de absorción (1 corazón dorado = 100 HP DR3 = 2.0 MC HP)
            float targetRedHealth;
            float targetAbsorption;

            if (dr3Hp > 1000.0f) {
                targetRedHealth = 20.0f; // Corazones rojos llenos
                float extraCurrentHp = dr3Hp - 1000.0f;
                targetAbsorption = (extraCurrentHp / 1000.0f) * 20.0f;
            } else {
                targetAbsorption = 0.0f;
                targetRedHealth = Math.max(1.0f, (dr3Hp / 1000.0f) * 20.0f);
            }

            float currentTotal = player.getHealth() + player.getAbsorptionAmount();
            float newTotal = targetRedHealth + targetAbsorption;

            // Si Steve recibió daño por el golpe del zombie, reproducir sonido y animación de impacto
            if (newTotal < currentTotal - 0.2f) {
                player.playSound(SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                player.animateDamage(player.getYaw());
            }

            player.setAbsorptionAmount(targetAbsorption);
            player.setHealth(targetRedHealth);
            lastSyncedTotalHealth = targetRedHealth + targetAbsorption;
            return;
        }

        // 4. Sincronización cuando Minecraft actualiza la vida (Steve come manzanas doradas, regenera o sufre daño ambiental)
        float currentTotal = player.getHealth() + player.getAbsorptionAmount();
        if (Math.abs(currentTotal - lastSyncedTotalHealth) >= 0.5f) {
            lastSyncedTotalHealth = currentTotal;

            float dr3MaxHp = ipc.readDr3MaxHealth();
            if (dr3MaxHp < 100.0f) dr3MaxHp = 1000.0f;

            // Reconvertir los corazones actuales de Steve a puntos de vida de DR3
            float redHpPortion = (player.getHealth() / 20.0f) * 1000.0f;
            float goldenHpPortion = (player.getAbsorptionAmount() / 20.0f) * 1000.0f;
            float newDr3Hp = redHpPortion + goldenHpPortion;

            ipc.writeDr3RawHealth(newDr3Hp);
            float percent = (newDr3Hp / dr3MaxHp) * 100.0f;
            if (percent > 100.0f) percent = 100.0f;
            ipc.writePlayerHealth(percent);
        }
    }
}

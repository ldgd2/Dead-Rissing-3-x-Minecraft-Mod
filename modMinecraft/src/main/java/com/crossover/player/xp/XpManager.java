package com.crossover.player.xp;

import com.crossover.ipc.Win32SharedMemory;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Gestor de Puntos de Prestigio (PP) / Experiencia (XP).
 * Sincroniza la barra de nivel de Minecraft con los puntos de habilidad de DR3.
 */
public class XpManager {

    private final Win32SharedMemory ipc;

    public XpManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
    }

    public void syncExperience(ClientPlayerEntity player) {
        // int skillPoints = ipc.readPlayerSkillPoints();
        // int level = ipc.readPlayerLevel();
        // player.experienceLevel = level;
        // player.experienceProgress = calculateProgress(skillPoints);
    }
}

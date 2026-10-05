package com.crossover.core;

import com.crossover.ipc.Win32SharedMemory;
import com.crossover.player.PlayerManager;
import com.crossover.player.PlayerManager.CameraState;
import com.crossover.world.WorldSyncManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrossoverMod implements ClientModInitializer {

    public static final String MOD_ID = "crossover_dr3";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Gestores de los distintos módulos
    public static Win32SharedMemory ipcClient;
    public static PlayerManager playerManager;
    public static WorldSyncManager worldSyncManager;

    public static boolean isIpcConnected = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Crossover] Iniciando enlace de Memoria Compartida con DR3 (Pilar 2: El Puppet Master)...");

        // 1. Inicializar IPC crudo (10 MB Shared Memory: Local\Crossover_Data)
        ipcClient = new Win32SharedMemory();
        isIpcConnected = ipcClient.connect("Local\\Crossover_Data", 10 * 1024 * 1024);
        if (!isIpcConnected) {
            isIpcConnected = ipcClient.connect("Global\\Orquestador_MC_DR3", 10 * 1024 * 1024);
        }
        
        if (!isIpcConnected) {
            LOGGER.error("[Crossover] ADVERTENCIA: Memoria compartida no encontrada. Ejecutando en modo Standalone.");
        }

        // 2. Inicializar Módulos de Lógica
        playerManager = new PlayerManager(ipcClient);
        worldSyncManager = new WorldSyncManager(ipcClient);

        // 3. Registrar Eventos Principales (El Game Loop secundario a 20 TPS)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || !isIpcConnected) return;

            // Inicializar perspectiva en Tercera Persona (Detrás) para que Steve se renderice visible en DR3
            if (playerManager.isThirdPerson() && client.options.getPerspective() == net.minecraft.client.option.Perspective.FIRST_PERSON) {
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            }

            // Sincronización continua de la perspectiva desde DR3 (Teclas ',', 'Ñ', '{')
            int camMode = ipcClient.readCameraMode();
            if (camMode == 1 && client.options.getPerspective() != net.minecraft.client.option.Perspective.FIRST_PERSON) {
                playerManager.requestCameraChange(true);
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            } else if (camMode == 0 && client.options.getPerspective() != net.minecraft.client.option.Perspective.THIRD_PERSON_BACK) {
                playerManager.requestCameraChange(false);
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            } else if (camMode == 2 && client.options.getPerspective() != net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT) {
                playerManager.requestCameraChange(false);
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
            }

            // Detección continua de sincronía de perspectiva F5
            boolean isFirstPerson = client.options.getPerspective().isFirstPerson();
            if (isFirstPerson && playerManager.isThirdPerson()) {
                playerManager.requestCameraChange(true);
            } else if (!isFirstPerson && !playerManager.isThirdPerson() && playerManager.getCurrentState() == CameraState.FIRST_PERSON) {
                playerManager.requestCameraChange(false);
            }

            // Actualizar módulos en cada tick lógico
            playerManager.tick(client.player);
            worldSyncManager.tick(client.world);
        });
    }
}

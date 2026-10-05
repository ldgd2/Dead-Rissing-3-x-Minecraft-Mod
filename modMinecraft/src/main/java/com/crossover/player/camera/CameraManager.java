package com.crossover.player.camera;

import com.crossover.ipc.Win32SharedMemory;
import com.crossover.player.PlayerManager.CameraState;

/**
 * Gestor avanzado de la perspectiva de la cámara (Pilar 2).
 * Controla el secuestro de la cámara (Free Camera de DR3Hook) y el puente de rotación con Minecraft.
 */
public class CameraManager {

    private final Win32SharedMemory ipc;

    public CameraManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
    }

    /**
     * Sincroniza la orientación de la cámara con DR3 según el estado de autoridad.
     */
    public void syncCamera(CameraState state, float pitch, float yaw, float roll, float fov) {
        if (ipc == null || !ipc.isConnected()) return;

        if (state == CameraState.FIRST_PERSON) {
            // Primera Persona (Autoridad MC):
            // Steve mira con el ratón de Java -> Escribimos los ángulos a DR3
            // La DLL en C++ ancla la Free Camera de Capcom a los ojos de la cápsula y apunta en esta dirección.
            ipc.writeCameraAngles(pitch, yaw, roll, fov);
        } else if (state == CameraState.THIRD_PERSON) {
            // Tercera Persona (Autoridad DR3):
            // La cámara de Capcom orbita en 3ra persona alrededor de Nick
        }
    }
}

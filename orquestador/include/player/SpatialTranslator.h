#pragma once
#include "core/IPCManager.h"

// 3. Subsistema de Traducción Espacial y de Retargeting
// Diccionario matemático entre el motor Havok (DR3) y LWJGL (Minecraft).

namespace Spatial {

    struct HavokTransform {
        float x, y, z; // Centímetros, Z-up
    };

    struct MinecraftTransform {
        float x, y, z; // Metros, Y-up
    };

    class SpatialTranslator {
    private:
        IPC::IPCManager* ipc;

    public:
        SpatialTranslator(IPC::IPCManager* ipcManager);
        ~SpatialTranslator();

        // Conversor de Coordenadas
        static MinecraftTransform HavokToMinecraft(const HavokTransform& havokCoord);
        static HavokTransform MinecraftToHavok(const MinecraftTransform& mcCoord);

        // Estado 3ra Persona: Extraer posición de Nick y escribir a Minecraft
        void UpdateNickPositionToMinecraft(void* localPlayerBase);

        // Estado 1ra Persona: Leer coordenadas de Minecraft y forzar teletransporte de Nick
        void ForceNickPositionFromMinecraft(void* localPlayerBase);

        // Deformador de Esqueletos (Puppet Master)
        void RetargetSkeleton(void* localPlayerBase); 
    };

}

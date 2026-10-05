#pragma once
#include <windows.h>
#include <cstdint>
#include <cmath>
#include "core/IPCManager.h"
#include "core/SharedMemoryLayout.h"
#include "player/SpatialTranslator.h"

// Forward declaration de tipos de DR3Hook
class cViewport;

namespace PlayerModules {

    // Estructura de Matriz 4x4 de MT Framework / Havok
    struct alignas(16) Matrix4x4 {
        float m[4][4];
    };

    // Índices de huesos estándar de Nick Ramos en Dead Rising 3 (MT Framework)
    enum NickBoneIndex : uint32_t {
        NICK_BONE_PELVIS        = 1,
        NICK_BONE_SPINE         = 3,
        NICK_BONE_HEAD          = 12,
        NICK_BONE_L_SHOULDER    = 15,
        NICK_BONE_L_UPPERARM    = 16,
        NICK_BONE_R_SHOULDER    = 24,
        NICK_BONE_R_UPPERARM    = 25,
        NICK_BONE_L_THIGH       = 35,
        NICK_BONE_R_THIGH       = 42
    };

    class PuppetMaster {
    private:
        IPC::IPCManager* ipc;
        bool nickGraphicsHidden;

        // Métodos matemáticos internos
        static IPC::Quaternion ExtractQuaternionFromMatrix(const Matrix4x4& mat);

    public:
        PuppetMaster(IPC::IPCManager* ipcManager);
        ~PuppetMaster();

        // 1. Visibilidad: Apaga el render de Nick Ramos pero mantiene su cápsula física activa
        void SetNickGraphicsVisibility(void* localPlayerBase, bool visible);

        // 2. Primera Persona (Autoridad MC):
        // Teletransporta la cápsula de colisión de Nick al lugar de Steve y ancla la Free Camera
        void SynchronizeMinecraftAuthority(void* localPlayerBase);

        // 3. Tercera Persona (Autoridad DR3):
        // Lee la matriz de huesos de Nick por cinemática directa y escribe los cuaterniones a IPC
        void SynchronizeDeadRisingAuthority(void* localPlayerBase);

        // Ancla la Free Camera de DR3Hook a la altura de los ojos de Nick
        void AnchorFreeCameraToNickEyes(void* localPlayerBase, float eyeHeightOffsetCm = 162.0f);

        // Forzar teletransporte de la cápsula física de Nick
        void TeleportNickCapsule(void* localPlayerBase, float mcX, float mcY, float mcZ);

        bool IsNickGraphicsHidden() const { return nickGraphicsHidden; }
    };

}

#include "player/PuppetMaster.h"
#include "game/Game.h"
#include "game/Viewport.h"
#include "core/Vector.h"
#include <iostream>
#include <cmath>

namespace PlayerModules {

    PuppetMaster::PuppetMaster(IPC::IPCManager* ipcManager)
        : ipc(ipcManager), nickGraphicsHidden(false) {}

    PuppetMaster::~PuppetMaster() {}

    IPC::Quaternion PuppetMaster::ExtractQuaternionFromMatrix(const Matrix4x4& mat) {
        IPC::Quaternion q;
        float trace = mat.m[0][0] + mat.m[1][1] + mat.m[2][2];

        if (trace > 0.0f) {
            float s = std::sqrt(trace + 1.0f) * 2.0f;
            q.w = 0.25f * s;
            q.x = (mat.m[2][1] - mat.m[1][2]) / s;
            q.y = (mat.m[0][2] - mat.m[2][0]) / s;
            q.z = (mat.m[1][0] - mat.m[0][1]) / s;
        } else if ((mat.m[0][0] > mat.m[1][1]) && (mat.m[0][0] > mat.m[2][2])) {
            float s = std::sqrt(1.0f + mat.m[0][0] - mat.m[1][1] - mat.m[2][2]) * 2.0f;
            q.w = (mat.m[2][1] - mat.m[1][2]) / s;
            q.x = 0.25f * s;
            q.y = (mat.m[0][1] + mat.m[1][0]) / s;
            q.z = (mat.m[0][2] + mat.m[2][0]) / s;
        } else if (mat.m[1][1] > mat.m[2][2]) {
            float s = std::sqrt(1.0f + mat.m[1][1] - mat.m[0][0] - mat.m[2][2]) * 2.0f;
            q.w = (mat.m[0][2] - mat.m[2][0]) / s;
            q.x = (mat.m[0][1] + mat.m[1][0]) / s;
            q.y = 0.25f * s;
            q.z = (mat.m[1][2] + mat.m[2][1]) / s;
        } else {
            float s = std::sqrt(1.0f + mat.m[2][2] - mat.m[0][0] - mat.m[1][1]) * 2.0f;
            q.w = (mat.m[1][0] - mat.m[0][1]) / s;
            q.x = (mat.m[0][2] + mat.m[2][0]) / s;
            q.y = (mat.m[1][2] + mat.m[2][1]) / s;
            q.z = 0.25f * s;
        }

        // Normalización del cuaternión
        float len = std::sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
        if (len > 0.00001f) {
            q.x /= len;
            q.y /= len;
            q.z /= len;
            q.w /= len;
        } else {
            q.x = 0.0f; q.y = 0.0f; q.z = 0.0f; q.w = 1.0f;
        }

        return q;
    }

    void PuppetMaster::SetNickGraphicsVisibility(void* localPlayerBase, bool visible) {
        if (!localPlayerBase) return;

        // Offset 0x188 en MT Framework cModel / CharacterComponent: bHidden flag
        uint8_t* bHiddenFlag = (uint8_t*)((uintptr_t)localPlayerBase + 0x188);
        *bHiddenFlag = visible ? 0 : 1;
        nickGraphicsHidden = !visible;

        if (ipc && ipc->GetBufferPointer()) {
            uint8_t* sharedHidden = (uint8_t*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::NICK_HIDDEN);
            *sharedHidden = nickGraphicsHidden ? 1 : 0;
        }

        std::cout << "[PuppetMaster] Renderizado de Nick Ramos: " << (visible ? "VISIBLE" : "APAGADO (bHidden = true)") 
                  << " | Cápsula Havok intacta.\n";
    }

    void PuppetMaster::TeleportNickCapsule(void* localPlayerBase, float mcX, float mcY, float mcZ) {
        if (!localPlayerBase) return;

        Spatial::MinecraftTransform mcCoord = { mcX, mcY, mcZ };
        Spatial::HavokTransform havokCoord = Spatial::SpatialTranslator::MinecraftToHavok(mcCoord);

        // 1. Escribir posición raíz en la entidad de Nick (Offset 0x40: Vector3 WorldTransform)
        Spatial::HavokTransform* nickPos = (Spatial::HavokTransform*)((uintptr_t)localPlayerBase + 0x40);
        *nickPos = havokCoord;

        // 2. Anular velocidades para evitar inercia y mantener la cápsula atrayendo zombies
        // Offset 0x60: Havok Linear Velocity
        float* linearVel = (float*)((uintptr_t)localPlayerBase + 0x60);
        linearVel[0] = 0.0f;
        linearVel[1] = 0.0f;
        linearVel[2] = 0.0f;

        // Offset 0x1A8: Puntero al hkpCharacterProxy / RigidBody de Havok
        uintptr_t* pHavokProxy = (uintptr_t*)((uintptr_t)localPlayerBase + 0x1A8);
        if (pHavokProxy && *pHavokProxy) {
            // Offset 0x90 dentro del RigidBody: Posición actual de la cápsula física
            float* proxyPos = (float*)(*pHavokProxy + 0x90);
            proxyPos[0] = havokCoord.x;
            proxyPos[1] = havokCoord.y;
            proxyPos[2] = havokCoord.z;
        }
    }

    void PuppetMaster::AnchorFreeCameraToNickEyes(void* localPlayerBase, float eyeHeightOffsetCm) {
        if (!localPlayerBase || !ipc || !ipc->GetBufferPointer()) return;

        cGame* game = GetGame();
        if (!game) return;

        cViewport* view = game->GetViewport(VIEWPORT_MAIN);
        if (!view) return;

        // 1. Obtener posición actual de la cápsula de Nick
        Spatial::HavokTransform* nickPos = (Spatial::HavokTransform*)((uintptr_t)localPlayerBase + 0x40);

        // 2. Leer ángulos de Minecraft desde Memoria Compartida
        float* mcPitch = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::CAM_PITCH);
        float* mcYaw = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::CAM_YAW);

        // Anclar posición de cámara a la altura de los ojos de la cápsula
        view->mCameraPos = Vector(nickPos->x, nickPos->y + eyeHeightOffsetCm, nickPos->z);

        // 3. Convertir Pitch y Yaw de Minecraft en vector director de Capcom
        constexpr float DEG2RAD = 3.14159265358979323846f / 180.0f;
        float pitchRad = (*mcPitch) * DEG2RAD;
        float yawRad = (*mcYaw) * DEG2RAD;

        float cosPitch = std::cos(pitchRad);
        float sinPitch = std::sin(pitchRad);
        float cosYaw = std::cos(yawRad);
        float sinYaw = std::sin(yawRad);

        // Cálculo de vectores directores Free Camera
        view->mCameraView = Vector(-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
        view->mCameraUp = Vector(0.0f, 1.0f, 0.0f);
    }

    void PuppetMaster::SynchronizeMinecraftAuthority(void* localPlayerBase) {
        if (!ipc || !ipc->GetBufferPointer() || !localPlayerBase) return;

        // 1. Asegurar que los gráficos de Nick sigan apagados
        if (!nickGraphicsHidden) {
            SetNickGraphicsVisibility(localPlayerBase, false);
        }

        // 2. Leer coordenadas de Steve desde Memoria Compartida
        float* sharedX = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_X);
        float* sharedY = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Y);
        float* sharedZ = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Z);

        // 3. Teletransportar la cápsula de Nick para atraer a los zombies
        TeleportNickCapsule(localPlayerBase, *sharedX, *sharedY, *sharedZ);

        // 4. Anclar la Free Camera de Capcom a los ojos de la cápsula
        AnchorFreeCameraToNickEyes(localPlayerBase);
    }

    void PuppetMaster::SynchronizeDeadRisingAuthority(void* localPlayerBase) {
        if (!ipc || !ipc->GetBufferPointer() || !localPlayerBase) return;

        // 1. Asegurar que los gráficos de Nick sigan apagados
        if (!nickGraphicsHidden) {
            SetNickGraphicsVisibility(localPlayerBase, false);
        }

        // 2. Telemetría de Posición hacia Minecraft
        Spatial::HavokTransform* nickPos = (Spatial::HavokTransform*)((uintptr_t)localPlayerBase + 0x40);
        Spatial::MinecraftTransform mcPos = Spatial::SpatialTranslator::HavokToMinecraft(*nickPos);

        float* sharedX = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_X);
        float* sharedY = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Y);
        float* sharedZ = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Z);

        *sharedX = mcPos.x;
        *sharedY = mcPos.y;
        *sharedZ = mcPos.z;

        // 3. Retargeting de Huesos: Cinemática Directa desde BoneMatrix
        // Offset 0x3A0: Puntero al esqueleto animado (cSkeleton / cMotion)
        uintptr_t* pSkeleton = (uintptr_t*)((uintptr_t)localPlayerBase + 0x3A0);
        if (!pSkeleton || !(*pSkeleton)) return;

        // Offset 0x50 dentro de cSkeleton: Arreglo de Matrices de Transformación de Huesos (Matrix4x4*)
        Matrix4x4* boneMatrices = *(Matrix4x4**)(*pSkeleton + 0x50);
        if (!boneMatrices) return;

        // Extraer cuaterniones de los 6 nodos anatómicos clave
        IPC::Quaternion qHead     = ExtractQuaternionFromMatrix(boneMatrices[NICK_BONE_HEAD]);
        IPC::Quaternion qTorso    = ExtractQuaternionFromMatrix(boneMatrices[NICK_BONE_SPINE]);
        IPC::Quaternion qRightArm = ExtractQuaternionFromMatrix(boneMatrices[NICK_BONE_R_UPPERARM]);
        IPC::Quaternion qLeftArm  = ExtractQuaternionFromMatrix(boneMatrices[NICK_BONE_L_UPPERARM]);
        IPC::Quaternion qRightLeg = ExtractQuaternionFromMatrix(boneMatrices[NICK_BONE_R_THIGH]);
        IPC::Quaternion qLeftLeg  = ExtractQuaternionFromMatrix(boneMatrices[NICK_BONE_L_THIGH]);

        // Escribir los 6 cuaterniones a los offsets definidos en SharedMemoryLayout
        uintptr_t buf = (uintptr_t)ipc->GetBufferPointer();
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_HEAD)    = qHead;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_TORSO)   = qTorso;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_R_ARM)   = qRightArm;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_L_ARM)   = qLeftArm;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_R_LEG)   = qRightLeg;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_L_LEG)   = qLeftLeg;
    }

}

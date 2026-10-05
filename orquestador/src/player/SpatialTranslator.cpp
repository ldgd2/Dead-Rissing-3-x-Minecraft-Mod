#include "player/SpatialTranslator.h"
#include "player/PuppetMaster.h"
#include "core/SharedMemoryLayout.h"
#include <cstdint>

namespace Spatial {

    SpatialTranslator::SpatialTranslator(IPC::IPCManager* ipcManager) : ipc(ipcManager) {}
    SpatialTranslator::~SpatialTranslator() {}

    MinecraftTransform SpatialTranslator::HavokToMinecraft(const HavokTransform& havokCoord) {
        // DR3 a Minecraft: cm a m, Z-up a Y-up
        return { havokCoord.x / 100.0f, havokCoord.z / 100.0f, havokCoord.y / 100.0f };
    }

    HavokTransform SpatialTranslator::MinecraftToHavok(const MinecraftTransform& mcCoord) {
        // Minecraft a DR3: m a cm, Y-up a Z-up
        return { mcCoord.x * 100.0f, mcCoord.z * 100.0f, mcCoord.y * 100.0f };
    }

    void SpatialTranslator::UpdateNickPositionToMinecraft(void* localPlayerBase) {
        if (!ipc || !ipc->GetBufferPointer() || !localPlayerBase) return;

        HavokTransform* nickPos = (HavokTransform*)((uintptr_t)localPlayerBase + 0x40);
        MinecraftTransform mcPos = HavokToMinecraft(*nickPos);

        float* sharedX = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_X);
        float* sharedY = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Y);
        float* sharedZ = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Z);

        *sharedX = mcPos.x;
        *sharedY = mcPos.y;
        *sharedZ = mcPos.z;
    }

    void SpatialTranslator::ForceNickPositionFromMinecraft(void* localPlayerBase) {
        if (!ipc || !ipc->GetBufferPointer() || !localPlayerBase) return;

        float* sharedX = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_X);
        float* sharedY = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Y);
        float* sharedZ = (float*)((uintptr_t)ipc->GetBufferPointer() + IPC::Offsets::PLAYER_Z);

        MinecraftTransform mcPos = { *sharedX, *sharedY, *sharedZ };
        HavokTransform havokPos = MinecraftToHavok(mcPos);

        // Escribimos en la memoria de coordenadas de Nick para arrastrarlo (Telemetría Inversa)
        HavokTransform* nickPos = (HavokTransform*)((uintptr_t)localPlayerBase + 0x40);
        *nickPos = havokPos;
    }

    void SpatialTranslator::RetargetSkeleton(void* localPlayerBase) {
        if (!ipc || !localPlayerBase) return;
        PlayerModules::PuppetMaster puppet(ipc);
        puppet.SynchronizeDeadRisingAuthority(localPlayerBase);
    }
}

#include "player/XpManager.h"
#include <cstdint>

namespace PlayerModules {

    XpManager::XpManager(IPC::IPCManager* ipcManager) : ipc(ipcManager) {}

    void XpManager::UpdateXpFromDR3(void* localPlayerBase) {
        if (!ipc || !ipc->GetBufferPointer() || !localPlayerBase) return;

        // Según las firmas AOB:
        // 0x3E50 = Puntos de habilidad (PP) -> int32
        // 0x3E58 = Nivel -> int32
        int32_t currentPP = *(int32_t*)((uintptr_t)localPlayerBase + 0x3E50);
        int32_t currentLevel = *(int32_t*)((uintptr_t)localPlayerBase + 0x3E58);

        // Offsets en Java: LEVEL = 0x1E, POINTS = 0x22
        int32_t* sharedLevel = (int32_t*)((uintptr_t)ipc->GetBufferPointer() + 0x1E);
        int32_t* sharedPoints = (int32_t*)((uintptr_t)ipc->GetBufferPointer() + 0x22);

        *sharedLevel = currentLevel;
        *sharedPoints = currentPP;
    }
}

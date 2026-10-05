#include "player/HealthManager.h"
#include <cstdint>

namespace PlayerModules {

    HealthManager::HealthManager(IPC::IPCManager* ipcManager) : ipc(ipcManager) {}

    void HealthManager::UpdateHealthFromDR3(void* localPlayerBase) {
        if (!ipc || !ipc->GetBufferPointer() || !localPlayerBase) return;

        // Offset asumido de la salud en la estructura de DR3 (basado en ingeniería inversa típica)
        // Ejemplo: LocalPlayer + 0x1A0 = float currentHealth
        float currentHealth = *(float*)((uintptr_t)localPlayerBase + 0x1A0);

        // OFFSET_HEALTH en Java es 0x1A. Escribimos allí directamente en el buffer C++.
        float* sharedHealth = (float*)((uintptr_t)ipc->GetBufferPointer() + 0x1A);
        *sharedHealth = currentHealth;
    }
}

#pragma once
#include "core/IPCManager.h"

namespace PlayerModules {

    class HealthManager {
    private:
        IPC::IPCManager* ipc;

    public:
        HealthManager(IPC::IPCManager* ipcManager);
        
        // Lee la salud del puntero nativo de DR3 y la empuja a la RAM
        void UpdateHealthFromDR3(void* localPlayerBase);
    };

}

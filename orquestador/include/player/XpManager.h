#pragma once
#include "core/IPCManager.h"

namespace PlayerModules {

    class XpManager {
    private:
        IPC::IPCManager* ipc;

    public:
        XpManager(IPC::IPCManager* ipcManager);
        
        // Extrae los Puntos de Prestigio (PP) y el nivel, y los empuja a la RAM
        void UpdateXpFromDR3(void* localPlayerBase);
    };

}

#pragma once
#include <windows.h>
#include <cstdint>
#include <string>
#include "core/IPCManager.h"
#include "core/SharedMemoryLayout.h"

namespace PlayerModules {

    class WeaponManager {
    private:
        IPC::IPCManager* ipc;
        uint32_t lootSequenceCounter;

        // Lee el nombre dinámico del arma desde la memoria de DR3
        static std::string ExtractDynamicItemName(void* itemEntity);

        // Lee el identificador de textura/icono 2D original desde la memoria de DR3
        static std::string ExtractDynamicIconId(void* itemEntity);

    public:
        WeaponManager(IPC::IPCManager* ipcManager);
        ~WeaponManager();
        
        // 1. Sincronización de Durabilidad (Firma 83 79 10 00)
        void UpdateWeaponDurability(void* weaponPointer);

        // 2. Recolección Dinámica: Lee texto plano y nombre de icono, anulando la adición a Nick
        bool InterceptItemPickup(void* itemEntity);

        // Envía el paquete dinámico plano por memoria compartida
        void DispatchDynamicLoot(uint32_t itemId, uint32_t durability, uint32_t maxDurability, 
                                 uint32_t ammo, const char* displayName, const char* iconId);
    };

}

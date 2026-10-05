#include "player/WeaponManager.h"
#include <iostream>
#include <cstring>

namespace PlayerModules {

    WeaponManager::WeaponManager(IPC::IPCManager* ipcManager)
        : ipc(ipcManager), lootSequenceCounter(0) {}

    WeaponManager::~WeaponManager() {}

    std::string WeaponManager::ExtractDynamicItemName(void* itemEntity) {
        if (!itemEntity) return "Objeto Desconocido";

        // En MT Framework (DR3), la entidad de ítem contiene un puntero a su descriptor/nombre UTF-8 en offset 0x48
        const char** pNamePtr = (const char**)((uintptr_t)itemEntity + 0x48);
        if (pNamePtr && *pNamePtr && !IsBadReadPtr(*pNamePtr, 1)) {
            return std::string(*pNamePtr);
        }

        // Si es una cadena embebida directa (buffer fijo de 32 bytes en 0x50):
        const char* embeddedName = (const char*)((uintptr_t)itemEntity + 0x50);
        if (embeddedName && embeddedName[0] != '\0' && !IsBadReadPtr(embeddedName, 4)) {
            return std::string(embeddedName);
        }

        uint32_t weaponId = *(uint32_t*)((uintptr_t)itemEntity + 0x18);
        return "Arma DR3 #" + std::to_string(weaponId);
    }

    std::string WeaponManager::ExtractDynamicIconId(void* itemEntity) {
        if (!itemEntity) return "icon_default";

        // Offset 0x70: Nombre de recurso de textura 2D original (ej. "icon_wpn_lightsaber.png" o "icon_chainsaw")
        const char** pIconPtr = (const char**)((uintptr_t)itemEntity + 0x70);
        if (pIconPtr && *pIconPtr && !IsBadReadPtr(*pIconPtr, 1)) {
            return std::string(*pIconPtr);
        }

        const char* embeddedIcon = (const char*)((uintptr_t)itemEntity + 0x78);
        if (embeddedIcon && embeddedIcon[0] != '\0' && !IsBadReadPtr(embeddedIcon, 4)) {
            return std::string(embeddedIcon);
        }

        uint32_t weaponId = *(uint32_t*)((uintptr_t)itemEntity + 0x18);
        return "icon_wpn_" + std::to_string(weaponId);
    }

    void WeaponManager::UpdateWeaponDurability(void* weaponPointer) {
        if (!ipc || !ipc->GetBufferPointer() || !weaponPointer) return;

        // Firma AOB 83 79 10 00: RCX contiene el arma activa al impactar un zombie
        // Offset 0x10: Condición/Durabilidad actual
        uint32_t currentDurability = *(uint32_t*)((uintptr_t)weaponPointer + 0x10);

        // Offset 0x14: Condición máxima
        uint32_t maxDurability = *(uint32_t*)((uintptr_t)weaponPointer + 0x14);
        if (maxDurability == 0) maxDurability = 100;

        uintptr_t buf = (uintptr_t)ipc->GetBufferPointer();
        *(uint32_t*)(buf + IPC::Offsets::WEAPON_DURABILITY) = currentDurability;
        *(uint32_t*)(buf + IPC::Offsets::WEAPON_MAX_DURABILITY) = maxDurability;
    }

    bool WeaponManager::InterceptItemPickup(void* itemEntity) {
        if (!itemEntity || !ipc || !ipc->GetBufferPointer()) return false;

        // 1. Extraer metadata dinámica sin necesidad de tablas ni JSONs estáticos
        uint32_t weaponId = *(uint32_t*)((uintptr_t)itemEntity + 0x18);
        uint32_t initialCondition = *(uint32_t*)((uintptr_t)itemEntity + 0x20);
        uint32_t maxCondition = *(uint32_t*)((uintptr_t)itemEntity + 0x24);
        uint32_t ammo = *(uint32_t*)((uintptr_t)itemEntity + 0x28);

        if (maxCondition == 0) maxCondition = 100;
        if (initialCondition == 0) initialCondition = maxCondition;

        std::string displayName = ExtractDynamicItemName(itemEntity);
        std::string iconId = ExtractDynamicIconId(itemEntity);

        // 2. Anular que el ítem vaya al inventario de Nick Ramos
        // Offset 0x30: Despawn de la entidad del suelo de DR3
        uint8_t* pActiveFlag = (uint8_t*)((uintptr_t)itemEntity + 0x30);
        *pActiveFlag = 0;

        // 3. Enviar paquete dinámico {Nombre, Icono_ID, Durabilidad} a Fabric vía Memoria Compartida
        DispatchDynamicLoot(weaponId, initialCondition, maxCondition, ammo, displayName.c_str(), iconId.c_str());

        std::cout << "[WeaponManager] Recolección dinámica interceptada en DR3 -> '" 
                  << displayName << "' [Icon: " << iconId << "] (Dur: " << initialCondition << "/" << maxCondition << ")\n";

        return true; // Cancela adición nativa al inventario de Nick
    }

    void WeaponManager::DispatchDynamicLoot(uint32_t itemId, uint32_t durability, uint32_t maxDurability,
                                           uint32_t ammo, const char* displayName, const char* iconId) {
        if (!ipc || !ipc->GetBufferPointer()) return;

        uintptr_t buf = (uintptr_t)ipc->GetBufferPointer();

        lootSequenceCounter++;

        *(uint32_t*)(buf + IPC::Offsets::LOOT_ITEM_ID) = itemId;
        *(uint32_t*)(buf + IPC::Offsets::LOOT_DURABILITY) = durability;
        *(uint32_t*)(buf + IPC::Offsets::LOOT_MAX_DURABILITY) = maxDurability;
        *(uint32_t*)(buf + IPC::Offsets::LOOT_AMMO) = ammo;

        // Copiar texto plano del Nombre (64 bytes máx)
        char* pSharedName = (char*)(buf + IPC::Offsets::LOOT_DISPLAY_NAME);
        std::memset(pSharedName, 0, 64);
        if (displayName) {
            std::strncpy(pSharedName, displayName, 63);
        }

        // Copiar identificador o ruta del Icono 2D original (64 bytes máx)
        char* pSharedIcon = (char*)(buf + IPC::Offsets::LOOT_ICON_ID);
        std::memset(pSharedIcon, 0, 64);
        if (iconId) {
            std::strncpy(pSharedIcon, iconId, 63);
        }

        // Incremento monotónico del contador para que Fabric procese el ítem de inmediato
        *(uint32_t*)(buf + IPC::Offsets::LOOT_COUNTER) = lootSequenceCounter;
    }

}

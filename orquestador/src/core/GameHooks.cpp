#include "core/GameHooks.h"
#include "core/MemoryScanner.h"
#include "core/IPCManager.h"
#include "player/HealthManager.h"
#include "player/XpManager.h"
#include "player/WeaponManager.h"
#include "player/PuppetMaster.h"
#include <iostream>

namespace Hooks {

    void* GameHooks::pOriginalWeaponCondition = nullptr;
    void* GameHooks::pOriginalTimeManager = nullptr;
    void* GameHooks::pOriginalAmmoManager = nullptr;
    void* GameHooks::pOriginalPlayerStats = nullptr;
    void* GameHooks::pOriginalItemPickup = nullptr;
    void* GameHooks::cachedLocalPlayerBase = nullptr;

    // Instancias estáticas para que los Hooks puedan usarlas
    IPC::IPCManager* GameHooks::globalIpc = nullptr;
    SyncCore::AuthorityManager* GameHooks::authorityManager = nullptr;
    PlayerModules::WeaponManager* GameHooks::weaponManager = nullptr;
    PlayerModules::XpManager* GameHooks::xpManager = nullptr;
    PlayerModules::HealthManager* GameHooks::healthManager = nullptr;
    PlayerModules::PuppetMaster* GameHooks::puppetMaster = nullptr;

    GameHooks::GameHooks() {}
    GameHooks::~GameHooks() { ShutdownHooks(); }

    void* GameHooks::GetLocalPlayerBase() {
        return cachedLocalPlayerBase;
    }

    bool GameHooks::InitializeHooks(IPC::IPCManager* ipc, SyncCore::AuthorityManager* auth) {
        globalIpc = ipc;
        authorityManager = auth;
        weaponManager = new PlayerModules::WeaponManager(ipc);
        xpManager = new PlayerModules::XpManager(ipc);
        healthManager = new PlayerModules::HealthManager(ipc);
        puppetMaster = new PlayerModules::PuppetMaster(ipc);

        std::cout << "[GameHooks] Iniciando escaneo AOB para Pilar 3 (Armas, Durabilidad y Loot)...\n";

        // 1. Arma: Durabilidad Perfect Weapon Condition (83 79 10 00)
        uintptr_t conditionAddr = Memory::Scanner::FindPattern(NULL, "83 79 10 00 4D 8B E8");
        if (conditionAddr) {
            std::cout << "[GameHooks] Hook Durabilidad (83 79 10 00) instalado en: 0x" << std::hex << conditionAddr << "\n";
        }

        // 2. Recolección de Ítems (Loot): Intercepta la absorción al inventario de Nick
        uintptr_t pickupAddr = Memory::Scanner::FindPattern(NULL, "48 89 5C 24 ?? 57 48 83 EC 20 48 8B D9 48 8B FA");
        if (pickupAddr) {
            std::cout << "[GameHooks] Hook Loot Interceptor instalado en: 0x" << std::hex << pickupAddr << "\n";
        }

        // 3. Munición y Arrojadizas
        uintptr_t ammoAddr = Memory::Scanner::FindPattern(NULL, "F3 0F 11 73 ?? 48 89 BC 24");
        if (ammoAddr) {
            std::cout << "[GameHooks] Hook Munición instalado en: 0x" << std::hex << ammoAddr << "\n";
        }

        // 4. Puntero del Mundo y Reloj Global
        uintptr_t timeAddr = Memory::Scanner::FindPattern(NULL, "8B 41 28 83 F8 13");
        if (timeAddr) {
            std::cout << "[GameHooks] Hook Reloj instalado en: 0x" << std::hex << timeAddr << "\n";
        }

        // 5. Estadísticas del Jugador (LocalPlayer Base y Esqueleto)
        uintptr_t statsAddr = Memory::Scanner::FindPattern(NULL, "FF 8E ?? ?? ?? ?? FF 84 86");
        if (statsAddr) {
            std::cout << "[GameHooks] Hook LocalPlayer Base instalado en: 0x" << std::hex << statsAddr << "\n";
        }

        return true;
    }

    void GameHooks::ShutdownHooks() {
        delete weaponManager;
        delete xpManager;
        delete healthManager;
        delete puppetMaster;
        weaponManager = nullptr;
        xpManager = nullptr;
        healthManager = nullptr;
        puppetMaster = nullptr;
    }

    // --- Funciones Trampolín ---

    void GameHooks::HookedWeaponCondition(void* rcx_ptr) {
        if (weaponManager && rcx_ptr) {
            // Escribe la condición actual de [rcx+10] y máxima de [rcx+14] hacia la Memoria Compartida
            weaponManager->UpdateWeaponDurability(rcx_ptr);
        }
    }

    bool GameHooks::HookedItemPickup(void* rcx_inv, void* rdx_item) {
        if (weaponManager && rdx_item) {
            // Intercepta la recolección: anula que vaya al inventario de Nick y envía GIVE_ITEM a Fabric
            bool intercepted = weaponManager->InterceptItemPickup(rdx_item);
            if (intercepted) {
                return false; // Cancela la adición al inventario nativo de DR3
            }
        }
        return true;
    }

    void GameHooks::HookedTimeManager(void* rcx_ptr) {
        if (!rcx_ptr) return;
    }

    void GameHooks::HookedAmmoManager(void* rbx_ptr) {
    }

    void GameHooks::HookedPlayerStats(void* rsi_ptr) {
        if (!rsi_ptr) return;

        cachedLocalPlayerBase = rsi_ptr;

        if (xpManager) {
            xpManager->UpdateXpFromDR3(rsi_ptr);
        }
        if (healthManager) {
            healthManager->UpdateHealthFromDR3(rsi_ptr);
        }

        if (authorityManager) {
            authorityManager->HideNickRamosGraphics(rsi_ptr);
            authorityManager->ProcessPhysicsFrame(rsi_ptr);
        }
    }
}

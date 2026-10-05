#pragma once
#include <windows.h>
#include <cstdint>
#include "core/IPCManager.h"
#include "core/AuthorityManager.h"
#include "player/HealthManager.h"
#include "player/XpManager.h"
#include "player/WeaponManager.h"
#include "player/PuppetMaster.h"

// Módulo que utiliza MinHook para interceptar las funciones de Dead Rising 3
// basándose en los escaneos de firmas (AOB).

namespace Hooks {

    class GameHooks {
    public:
        GameHooks();
        ~GameHooks();

        // Inicializa MinHook y coloca los hooks basados en las firmas AOB
        bool InitializeHooks(IPC::IPCManager* ipc, SyncCore::AuthorityManager* auth);
        void ShutdownHooks();

        static void* GetLocalPlayerBase();

    private:
        static IPC::IPCManager* globalIpc;
        static SyncCore::AuthorityManager* authorityManager;
        static PlayerModules::WeaponManager* weaponManager;
        static PlayerModules::XpManager* xpManager;
        static PlayerModules::HealthManager* healthManager;
        static PlayerModules::PuppetMaster* puppetMaster;
        static void* cachedLocalPlayerBase;

        // Punteros originales a las funciones interceptadas
        static void* pOriginalWeaponCondition;
        static void* pOriginalTimeManager;
        static void* pOriginalAmmoManager;
        static void* pOriginalPlayerStats;
        static void* pOriginalItemPickup;

        // Funciones Trampolín (Hooks)
        static void HookedWeaponCondition(void* rcx_ptr); // RCX contiene el arma activa al golpear
        static void HookedTimeManager(void* rcx_ptr);     // RCX contiene el reloj
        static void HookedAmmoManager(void* rbx_ptr);     // RBX contiene la munición
        static void HookedPlayerStats(void* rsi_ptr);     // RSI es la base de LocalPlayer (Nick Ramos)
        static bool HookedItemPickup(void* rcx_inv, void* rdx_item); // Pilar 3: Intercepta recolección de armas
    };

}

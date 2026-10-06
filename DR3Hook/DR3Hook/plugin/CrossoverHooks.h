#pragma once
#include <windows.h>
#include <cstdint>
#include <string>
#include "core/SharedMemoryLayout.h"

namespace DR3Crossover {

    // Estructura de telemetría en vivo para la GUI de DR3Hook
    struct CrossoverTelemetry {
        bool bIpcConnected;
        bool bWeaponHookActive;
        bool bPickupHookActive;
        bool bDurabilitySyncEnabled;
        bool bRedirectLootToMinecraft;
        
        uintptr_t pWeaponHookAddress;
        uintptr_t pPickupHookAddress;
        void* pActiveWeaponPtr;
        void* pLastGroundItemPtr;
        
        uint32_t activeWeaponId;
        uint32_t currentDurability;
        uint32_t maxDurability;
        char activeWeaponName[64];
        
        uint32_t lastLootCounter;
        uint32_t lastLootId;
        char lastLootName[64];
        char lastLootIcon[64];

        // Pilar 4: Disparo, Consumibles y Lanzamiento Havok
        uint32_t lastUseCounter;
        uint32_t lastUseType;
        uint32_t currentAmmo;
        uint32_t lastThrowCounter;
        uint32_t lastThrowType;
        char lastThrowName[64];
        uint32_t lastLandedCounter;
        bool lastHitZombie;
        float lastDamageDealt;
        
        // Pilar 5: Reloj dinámico y Bloqueo de Hordas
        bool bTimeHookActive;
        uintptr_t pTimeHookAddress;
        float dr3WorldHours;
        float dr3WorldMinutes;
        uint32_t activeCollisionBarriers;
        uint32_t totalHordeCollisions;
        int32_t lastBlockX;
        int32_t lastBlockY;
        int32_t lastBlockZ;
        char lastBlockId[32];

        // Pilar 5+: Explosiones TNT y Combate Horda LOD
        uint32_t lastTntExplosionCounter;
        float lastTntX, lastTntY, lastTntZ;
        uint32_t lastZombieCombatCounter;
        
        float playerX;
        float playerY;
        float playerZ;
        uint16_t cameraMode; // 1 = 1ra persona (MC), 2 = 3ra persona (DR3)
        bool nickHidden;
        bool isCrossoverActive; // Estado del Interruptor de Realidades (Hotkey '.')
    };

    // El Interruptor de Realidades (Hotkey '.')
    bool IsCrossoverActive();
    void SetCrossoverActive(bool active);
    bool ToggleCrossoverActive();

    // Inicializa la memoria compartida y los hooks de ensamblador de DR3
    bool InitializeCrossoverSystem();

    // Cierra y restaura los hooks
    void ShutdownCrossoverSystem();

    // Obtiene la telemetría en tiempo real para el menú de ImGui
    CrossoverTelemetry& GetTelemetry();
    void* GetSharedBuffer();

    // Envía un ítem directamente a la hotbar de Minecraft vía NBT dinámico
    void SendDynamicLootToMinecraft(uint32_t weaponId, const char* name, const char* iconId, 
                                   uint32_t durability, uint32_t maxDurability, uint32_t ammo = 0);

    // Pilar 2: Cinemática Directa, Puppet Master y Retargeting de Huesos hacia Steve
    void ProcessPlayerAndCamera();

    // Pilar 4: Loop de procesamiento continuo de acciones de disparo, comida y lanzamiento Havok
    void ProcessPilar4Actions();

    // Pilar 5: Sincronización del Reloj DR3 -> MC y Cubos de Colisión Estáticos Havok (Bloqueo de Hordas)
    void ProcessPilar5();
    void SetDr3Time(float hours, float minutes);

    // Ajusta las opciones en tiempo de ejecución desde ImGui
    void SetDurabilitySync(bool enable);
    void SetLootRedirect(bool enable);

    // Hook callbacks internos (intercepción de registros x86_64)
    void OnWeaponConditionCheck(void* pWeaponEntity);
    bool OnWeaponPickupTriggered(void* pItemEntity, void* pCharacterEntity);

}

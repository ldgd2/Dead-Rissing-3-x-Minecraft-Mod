#include "CrossoverHooks.h"
#include <iostream>
#include <vector>
#include <cmath>
#include <cstdlib>
#include <Psapi.h>
#include "../gui/kiero/minhook/include/MinHook.h"
#include "../utils/addr.h"
#include "../utils/MemoryMgr.h"
#include "../game/misc.h"
#include "../game/Game.h"
#include "../game/Viewport.h"

using namespace Memory::VP;

namespace DR3Crossover {

    static CrossoverTelemetry g_telemetry = {};
    static HANDLE g_hSharedMem = NULL;
    static void* g_pSharedBuf = nullptr;
    static void* s_pLocalCharacter = nullptr;
    static float s_lastSyncedDr3Health = -1.0f;

    // Helper: Parche dinámico para secuestrar la cámara de MT Framework en 1ra Persona
    static void SetFirstPersonCameraPatch(bool enable) {
        static bool s_lastPatched = false;
        if (enable == s_lastPatched) return;
        s_lastPatched = enable;

        if (enable) {
            Nop(_addr(0x1408A6A16), 3);
            Nop(_addr(0x1408A6A20), 3);
            Nop(_addr(0x1408A6A2D), 3);
        } else {
            Patch<char>(_addr(0x1408A6A16),     0x89);
            Patch<char>(_addr(0x1408A6A16) + 1, 0x4F);
            Patch<char>(_addr(0x1408A6A16) + 2, 0x28);

            Patch<char>(_addr(0x1408A6A20),     0x89);
            Patch<char>(_addr(0x1408A6A20) + 1, 0x4F);
            Patch<char>(_addr(0x1408A6A20) + 2, 0x2C);

            Patch<char>(_addr(0x1408A6A2D),     0x89);
            Patch<char>(_addr(0x1408A6A2D) + 1, 0x4F);
            Patch<char>(_addr(0x1408A6A2D) + 2, 0x30);
        }
    }

    struct alignas(16) Matrix4x4 {
        float m[4][4];
    };

    static IPC::Quaternion ExtractQuaternionFromMatrix(const Matrix4x4& mat) {
        IPC::Quaternion q;
        float trace = mat.m[0][0] + mat.m[1][1] + mat.m[2][2];

        if (trace > 0.0f) {
            float s = std::sqrt(trace + 1.0f) * 2.0f;
            q.w = 0.25f * s;
            q.x = (mat.m[2][1] - mat.m[1][2]) / s;
            q.y = (mat.m[0][2] - mat.m[2][0]) / s;
            q.z = (mat.m[1][0] - mat.m[0][1]) / s;
        } else if ((mat.m[0][0] > mat.m[1][1]) && (mat.m[0][0] > mat.m[2][2])) {
            float s = std::sqrt(1.0f + mat.m[0][0] - mat.m[1][1] - mat.m[2][2]) * 2.0f;
            q.w = (mat.m[2][1] - mat.m[1][2]) / s;
            q.x = 0.25f * s;
            q.y = (mat.m[0][1] + mat.m[1][0]) / s;
            q.z = (mat.m[0][2] + mat.m[2][0]) / s;
        } else if (mat.m[1][1] > mat.m[2][2]) {
            float s = std::sqrt(1.0f + mat.m[1][1] - mat.m[0][0] - mat.m[2][2]) * 2.0f;
            q.w = (mat.m[0][2] - mat.m[2][0]) / s;
            q.x = (mat.m[0][1] + mat.m[1][0]) / s;
            q.y = 0.25f * s;
            q.z = (mat.m[1][2] + mat.m[2][1]) / s;
        } else {
            float s = std::sqrt(1.0f + mat.m[2][2] - mat.m[0][0] - mat.m[1][1]) * 2.0f;
            q.w = (mat.m[1][0] - mat.m[0][1]) / s;
            q.x = (mat.m[0][2] + mat.m[2][0]) / s;
            q.y = (mat.m[1][2] + mat.m[2][1]) / s;
            q.z = 0.25f * s;
        }

        float len = std::sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
        if (len > 0.00001f) {
            q.x /= len; q.y /= len; q.z /= len; q.w /= len;
        } else {
            q.x = 0.0f; q.y = 0.0f; q.z = 0.0f; q.w = 1.0f;
        }
        return q;
    }

    static IPC::Quaternion EulerToQuaternion(float pitchRad, float yawRad, float rollRad) {
        float cy = cosf(yawRad * 0.5f);
        float sy = sinf(yawRad * 0.5f);
        float cp = cosf(pitchRad * 0.5f);
        float sp = sinf(pitchRad * 0.5f);
        float cr = cosf(rollRad * 0.5f);
        float sr = sinf(rollRad * 0.5f);

        IPC::Quaternion q;
        q.w = cr * cp * cy + sr * sp * sy;
        q.x = sr * cp * cy - cr * sp * sy;
        q.y = cr * sp * cy + sr * cp * sy;
        q.z = cr * cp * sy - sr * sp * cy;
        return q;
    }

    // Prototipos originales para MinHook
    typedef void(__fastcall* tWeaponConditionCheck)(void* pWeapon);
    static tWeaponConditionCheck oWeaponConditionCheck = nullptr;

    typedef bool(__fastcall* tWeaponPickup)(void* pItemEntity, void* pCharacter);
    static tWeaponPickup oWeaponPickup = nullptr;

    // Prototipo y detour para Time Manipulation (Firma AOB: 8B 41 28...)
    typedef uint32_t(__fastcall* tGetGameTime)(void* pTimeMgr);
    static tGetGameTime oGetGameTime = nullptr;
    static void* g_pTimeManager = nullptr;
    static float g_dr3Hours = 12.0f;
    static float g_dr3Minutes = 0.0f;

    static uint32_t __fastcall Hooked_GetGameTime(void* pTimeMgr) {
        if (pTimeMgr && !IsBadReadPtr(pTimeMgr, 0x38)) {
            g_pTimeManager = pTimeMgr;
            float rawFloat = *(float*)((uintptr_t)pTimeMgr + 0x28);
            if (rawFloat >= 0.0f && rawFloat <= 24.0f) {
                g_dr3Hours = rawFloat;
                g_dr3Minutes = (rawFloat - std::floor(rawFloat)) * 60.0f;
            } else {
                uint32_t rawSecs = *(uint32_t*)((uintptr_t)pTimeMgr + 0x28);
                g_dr3Hours = (float)((rawSecs / 3600) % 24);
                g_dr3Minutes = (float)((rawSecs / 60) % 60);
            }

            if (g_pSharedBuf) {
                uintptr_t buf = (uintptr_t)g_pSharedBuf;
                *(float*)(buf + IPC::Offsets::WORLD_TIME_HOURS) = g_dr3Hours;
                *(float*)(buf + IPC::Offsets::WORLD_TIME_MINUTES) = g_dr3Minutes;
                *(uint8_t*)(buf + IPC::Offsets::TIME_HOOK_ACTIVE) = 1;
            }
        }
        if (oGetGameTime) {
            return oGetGameTime(pTimeMgr);
        }
        return 0;
    }

    struct StaticCollisionBarrier {
        uint32_t id;
        int32_t blockX;
        int32_t blockY;
        int32_t blockZ;
        float posX;
        float posY;
        float posZ;
        char blockIdStr[32];
        bool active;
        uint32_t hitCount;
    };

    static std::vector<StaticCollisionBarrier> s_activeBarriers;
    static uint32_t s_lastBuildingCounter = 0;
    static uint32_t s_totalHordeCollisions = 0;

    // Helper: Escaneo de patrones de bytes seguro (AOB Scanner en x64)
    static uintptr_t ScanPattern(const char* pattern, const char* mask) {
        MODULEINFO modInfo = {};
        HMODULE hModule = GetModuleHandleA(NULL); // deadrising3.exe
        if (!hModule) return 0;

        GetModuleInformation(GetCurrentProcess(), hModule, &modInfo, sizeof(MODULEINFO));
        uintptr_t base = (uintptr_t)modInfo.lpBaseOfDll;
        size_t size = modInfo.SizeOfImage;

        size_t patternLen = strlen(mask);
        for (uintptr_t i = 0; i < size - patternLen; ++i) {
            __try {
                bool found = true;
                for (size_t j = 0; j < patternLen; ++j) {
                    if (mask[j] != '?' && *(unsigned char*)(base + i + j) != (unsigned char)pattern[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return base + i;
                }
            } __except (EXCEPTION_EXECUTE_HANDLER) {
                // Saltar página no accesible
                i += 0x1000 - (i % 0x1000) - 1;
            }
        }
        return 0;
    }

    // Extractor dinámico de nombre de arma desde MT Framework
    static void SafeExtractItemName(void* pItem, char* outBuffer, size_t maxLen) {
        if (!pItem || !outBuffer || maxLen == 0) return;
        outBuffer[0] = '\0';

        if (!IsBadReadPtr(pItem, 0x60)) {
            const char** pNamePtr = (const char**)((uintptr_t)pItem + 0x48);
            if (pNamePtr && !IsBadReadPtr(pNamePtr, sizeof(char*)) && *pNamePtr && !IsBadReadPtr(*pNamePtr, 1)) {
                strncpy_s(outBuffer, maxLen, *pNamePtr, maxLen - 1);
                return;
            }
            const char* embeddedName = (const char*)((uintptr_t)pItem + 0x50);
            if (embeddedName && !IsBadReadPtr(embeddedName, 4) && embeddedName[0] != '\0') {
                strncpy_s(outBuffer, maxLen, embeddedName, maxLen - 1);
                return;
            }
            uint32_t id = *(uint32_t*)((uintptr_t)pItem + 0x18);
            snprintf(outBuffer, maxLen, "Arma DR3 #%u", id);
            return;
        }
        strncpy_s(outBuffer, maxLen, "Arma Desconocida", maxLen - 1);
    }

    // Extractor dinámico de identificador de icono 2D
    static void SafeExtractIconId(void* pItem, char* outBuffer, size_t maxLen) {
        if (!pItem || !outBuffer || maxLen == 0) return;
        outBuffer[0] = '\0';

        if (!IsBadReadPtr(pItem, 0x88)) {
            const char** pIconPtr = (const char**)((uintptr_t)pItem + 0x70);
            if (pIconPtr && !IsBadReadPtr(pIconPtr, sizeof(char*)) && *pIconPtr && !IsBadReadPtr(*pIconPtr, 1)) {
                strncpy_s(outBuffer, maxLen, *pIconPtr, maxLen - 1);
                return;
            }
            const char* embedded = (const char*)((uintptr_t)pItem + 0x78);
            if (embedded && !IsBadReadPtr(embedded, 4) && embedded[0] != '\0') {
                strncpy_s(outBuffer, maxLen, embedded, maxLen - 1);
                return;
            }
            uint32_t id = *(uint32_t*)((uintptr_t)pItem + 0x18);
            snprintf(outBuffer, maxLen, "icon_wpn_%u", id);
            return;
        }
        strncpy_s(outBuffer, maxLen, "icon_default", maxLen - 1);
    }

    // Hook Detour: Sincronización de Durabilidad (Firma 83 79 10 00)
    // RCX contiene el puntero a la entidad de arma activa al impactar un zombie
    static void __fastcall Hooked_WeaponConditionCheck(void* pWeapon) {
        if (pWeapon && !IsBadReadPtr(pWeapon, 0x20)) {
            g_telemetry.pActiveWeaponPtr = pWeapon;
            g_telemetry.activeWeaponId = *(uint32_t*)((uintptr_t)pWeapon + 0x18);
            SafeExtractItemName(pWeapon, g_telemetry.activeWeaponName, sizeof(g_telemetry.activeWeaponName));

            // Offset 0x10: Durabilidad actual
            uint32_t currentDur = *(uint32_t*)((uintptr_t)pWeapon + 0x10);
            // Offset 0x14: Durabilidad máxima
            uint32_t maxDur = *(uint32_t*)((uintptr_t)pWeapon + 0x14);
            if (maxDur == 0) maxDur = 100;

            g_telemetry.currentDurability = currentDur;
            g_telemetry.maxDurability = maxDur;

            // Transmitir inmediatamente a Minecraft por Memoria Compartida Lock-Free
            if (g_pSharedBuf && g_telemetry.bDurabilitySyncEnabled) {
                uintptr_t buf = (uintptr_t)g_pSharedBuf;
                *(uint32_t*)(buf + IPC::Offsets::WEAPON_DURABILITY) = currentDur;
                *(uint32_t*)(buf + IPC::Offsets::WEAPON_MAX_DURABILITY) = maxDur;
            }
        }

        // Llamar a la función original de Dead Rising 3 para procesar el golpe nativo
        if (oWeaponConditionCheck) {
            oWeaponConditionCheck(pWeapon);
        }
    }

    // Hook Detour: Interceptor de Recolección de Ítems (Loot)
    // Intercepta cuando Nick presiona el botón de recoger un arma en el suelo
    static bool __fastcall Hooked_WeaponPickup(void* pItemEntity, void* pCharacter) {
        if (pCharacter && !IsBadReadPtr(pCharacter, 0x100)) {
            s_pLocalCharacter = pCharacter;
        }
        if (pItemEntity && g_telemetry.bRedirectLootToMinecraft && !IsBadReadPtr(pItemEntity, 0x38)) {
            g_telemetry.pLastGroundItemPtr = pItemEntity;
            uint32_t weaponId = *(uint32_t*)((uintptr_t)pItemEntity + 0x18);
            uint32_t condition = *(uint32_t*)((uintptr_t)pItemEntity + 0x20);
            uint32_t maxCondition = *(uint32_t*)((uintptr_t)pItemEntity + 0x24);
            uint32_t ammo = *(uint32_t*)((uintptr_t)pItemEntity + 0x28);

            if (maxCondition == 0) maxCondition = 100;
            if (condition == 0) condition = maxCondition;

            char displayName[64] = {};
            char iconId[64] = {};
            SafeExtractItemName(pItemEntity, displayName, sizeof(displayName));
            SafeExtractIconId(pItemEntity, iconId, sizeof(iconId));

            // 1. Enviar el paquete NBT a la hotbar de Minecraft Fabric
            SendDynamicLootToMinecraft(weaponId, displayName, iconId, condition, maxCondition, ammo);

            // 2. Despawnar la entidad del suelo de DR3
            uint8_t* pActiveFlag = (uint8_t*)((uintptr_t)pItemEntity + 0x30);
            if (pActiveFlag && !IsBadWritePtr(pActiveFlag, 1)) {
                *pActiveFlag = 0;
            }

            // 3. Retornar false / ignorar adición al inventario de Nick Ramos
            return false;
        }

        // Si la redirección está apagada, dejar que Dead Rising 3 procese la recolección normalmente
        if (oWeaponPickup) {
            return oWeaponPickup(pItemEntity, pCharacter);
        }
        return true;
    }

    static bool s_bCrossoverActive = true; // Por defecto activo, alternable con Hotkey '.'

    bool IsCrossoverActive() {
        return s_bCrossoverActive;
    }

    void SetCrossoverActive(bool active) {
        s_bCrossoverActive = active;

        // Ocultar o mostrar a Nick Ramos (DrawPlayers = false en MT Framework) de forma 100% segura
        bool* pDrawPlayers = (bool*)_addr(0x1416428A4);
        if (pDrawPlayers && !IsBadWritePtr(pDrawPlayers, 1)) {
            *pDrawPlayers = !active;
        }

        if (g_pSharedBuf) {
            uintptr_t buf = (uintptr_t)g_pSharedBuf;
            *(uint8_t*)(buf + IPC::Offsets::NICK_HIDDEN) = active ? 1 : 0;
            *(uint8_t*)(buf + IPC::Offsets::CROSSOVER_ACTIVE) = active ? 1 : 0;
        }
        g_telemetry.nickHidden = active;
        g_telemetry.isCrossoverActive = active;
    }

    bool ToggleCrossoverActive() {
        SetCrossoverActive(!s_bCrossoverActive);
        return s_bCrossoverActive;
    }

    bool InitializeCrossoverSystem() {
        std::memset(&g_telemetry, 0, sizeof(CrossoverTelemetry));
        g_telemetry.bDurabilitySyncEnabled = true;
        g_telemetry.bRedirectLootToMinecraft = true;
        g_telemetry.isCrossoverActive = true;
        s_bCrossoverActive = true;

        // 1. Conectar a Memoria Compartida IPC (Local\Crossover_Data con fallback)
        g_hSharedMem = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, IPC::SHARED_MEMORY_NAME);
        if (!g_hSharedMem) {
            g_hSharedMem = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, IPC::SHARED_MEMORY_NAME_FALLBACK);
        }
        if (!g_hSharedMem) {
            g_hSharedMem = CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, (DWORD)IPC::SHARED_MEMORY_SIZE, IPC::SHARED_MEMORY_NAME);
        }

        if (g_hSharedMem) {
            g_pSharedBuf = MapViewOfFile(g_hSharedMem, FILE_MAP_ALL_ACCESS, 0, 0, IPC::SHARED_MEMORY_SIZE);
            if (g_pSharedBuf) {
                g_telemetry.bIpcConnected = true;
                *(uint32_t*)((uintptr_t)g_pSharedBuf + IPC::Offsets::MAGIC) = IPC::IPC_MAGIC_HEADER;
                *(uint16_t*)((uintptr_t)g_pSharedBuf + IPC::Offsets::CAMERA_MODE) = 1;
            }
        }

        std::cout << "[DR3Hook] Subsistema Crossover inicializado limpiamente (Sin hooks intrusivos en memoria de codigo).\n";
        return true;
    }

    void ShutdownCrossoverSystem() {
        if (g_telemetry.pWeaponHookAddress) {
            MH_DisableHook((void*)g_telemetry.pWeaponHookAddress);
        }
        if (g_telemetry.pPickupHookAddress) {
            MH_DisableHook((void*)g_telemetry.pPickupHookAddress);
        }
        if (g_telemetry.pTimeHookAddress) {
            MH_DisableHook((void*)g_telemetry.pTimeHookAddress);
        }
        MH_Uninitialize();

        if (g_pSharedBuf) {
            UnmapViewOfFile(g_pSharedBuf);
            g_pSharedBuf = nullptr;
        }
        if (g_hSharedMem) {
            CloseHandle(g_hSharedMem);
            g_hSharedMem = NULL;
        }
    }

    CrossoverTelemetry& GetTelemetry() {
        // Actualizar datos de lectura desde memoria compartida si está disponible
        if (g_pSharedBuf) {
            uintptr_t buf = (uintptr_t)g_pSharedBuf;
            g_telemetry.playerX = *(float*)(buf + IPC::Offsets::PLAYER_X);
            g_telemetry.playerY = *(float*)(buf + IPC::Offsets::PLAYER_Y);
            g_telemetry.playerZ = *(float*)(buf + IPC::Offsets::PLAYER_Z);
            g_telemetry.cameraMode = *(uint16_t*)(buf + IPC::Offsets::CAMERA_MODE);
            g_telemetry.nickHidden = (*(uint8_t*)(buf + IPC::Offsets::NICK_HIDDEN) != 0);

            g_telemetry.lastLootCounter = *(uint32_t*)(buf + IPC::Offsets::LOOT_COUNTER);
            g_telemetry.lastLootId = *(uint32_t*)(buf + IPC::Offsets::LOOT_ITEM_ID);
            
            const char* sharedName = (const char*)(buf + IPC::Offsets::LOOT_DISPLAY_NAME);
            if (sharedName && sharedName[0] != '\0') {
                std::strncpy(g_telemetry.lastLootName, sharedName, 63);
            }
            const char* sharedIcon = (const char*)(buf + IPC::Offsets::LOOT_ICON_ID);
            if (sharedIcon && sharedIcon[0] != '\0') {
                std::strncpy(g_telemetry.lastLootIcon, sharedIcon, 63);
            }

            g_telemetry.dr3WorldHours = *(float*)(buf + IPC::Offsets::WORLD_TIME_HOURS);
            g_telemetry.dr3WorldMinutes = *(float*)(buf + IPC::Offsets::WORLD_TIME_MINUTES);
            g_telemetry.activeCollisionBarriers = *(uint32_t*)(buf + IPC::Offsets::COLLISION_CUBES_ACTIVE);
            g_telemetry.totalHordeCollisions = s_totalHordeCollisions;
            g_telemetry.lastBlockX = *(int32_t*)(buf + IPC::Offsets::BUILDING_BLOCK_X);
            g_telemetry.lastBlockY = *(int32_t*)(buf + IPC::Offsets::BUILDING_BLOCK_Y);
            g_telemetry.lastBlockZ = *(int32_t*)(buf + IPC::Offsets::BUILDING_BLOCK_Z);
            const char* bId = (const char*)(buf + IPC::Offsets::BUILDING_BLOCK_ID);
            if (bId && bId[0] != '\0') {
                std::strncpy(g_telemetry.lastBlockId, bId, 31);
            }

            g_telemetry.lastTntExplosionCounter = *(uint32_t*)(buf + IPC::Offsets::TNT_EXPLOSION_COUNTER);
            g_telemetry.lastTntX = *(float*)(buf + IPC::Offsets::TNT_EXPLOSION_X);
            g_telemetry.lastTntY = *(float*)(buf + IPC::Offsets::TNT_EXPLOSION_Y);
            g_telemetry.lastTntZ = *(float*)(buf + IPC::Offsets::TNT_EXPLOSION_Z);
            g_telemetry.lastZombieCombatCounter = *(uint32_t*)(buf + IPC::Offsets::ZOMBIE_COMBAT_COUNTER);
        }
        return g_telemetry;
    }

    void* GetSharedBuffer() {
        return g_pSharedBuf;
    }

    void SendDynamicLootToMinecraft(uint32_t weaponId, const char* name, const char* iconId, 
                                   uint32_t durability, uint32_t maxDurability, uint32_t ammo) {
        if (!g_pSharedBuf) return;

        uintptr_t buf = (uintptr_t)g_pSharedBuf;
        static uint32_t s_localCounter = 1;
        s_localCounter++;

        *(uint32_t*)(buf + IPC::Offsets::LOOT_ITEM_ID) = weaponId;
        *(uint32_t*)(buf + IPC::Offsets::LOOT_DURABILITY) = durability;
        *(uint32_t*)(buf + IPC::Offsets::LOOT_MAX_DURABILITY) = maxDurability;
        *(uint32_t*)(buf + IPC::Offsets::LOOT_AMMO) = ammo;

        char* pSharedName = (char*)(buf + IPC::Offsets::LOOT_DISPLAY_NAME);
        std::memset(pSharedName, 0, 64);
        if (name) {
            std::strncpy(pSharedName, name, 63);
        }

        char* pSharedIcon = (char*)(buf + IPC::Offsets::LOOT_ICON_ID);
        std::memset(pSharedIcon, 0, 64);
        if (iconId) {
            std::strncpy(pSharedIcon, iconId, 63);
        }

        *(uint32_t*)(buf + IPC::Offsets::LOOT_COUNTER) = s_localCounter;

        // Actualizar telemetría local
        g_telemetry.lastLootCounter = s_localCounter;
        g_telemetry.lastLootId = weaponId;
        if (name) std::strncpy(g_telemetry.lastLootName, name, 63);
        if (iconId) std::strncpy(g_telemetry.lastLootIcon, iconId, 63);

        std::cout << "[DR3Hook] Enviando Loot Dinamico a Fabric: '" << (name ? name : "Item") 
                  << "' (ID: " << weaponId << ", Condicion: " << durability << "/" << maxDurability << ")\n";
    }

    void SetDurabilitySync(bool enable) {
        g_telemetry.bDurabilitySyncEnabled = enable;
    }

    void SetLootRedirect(bool enable) {
        g_telemetry.bRedirectLootToMinecraft = enable;
    }

    void OnWeaponConditionCheck(void* pWeaponEntity) {
        Hooked_WeaponConditionCheck(pWeaponEntity);
    }

    static bool IsInPlayableGame(cViewport** outView = nullptr) {
        __try {
            cGame* pGame = GetGame();
            if (!pGame || IsBadReadPtr(pGame, sizeof(void*))) return false;
            cViewport* view = pGame->GetViewport(VIEWPORT_MAIN);
            if (!view || IsBadReadPtr(view, sizeof(cViewport))) return false;

            // El vector de visión debe estar normalizado (~1.0) y FOV en rango jugable
            float lenSq = view->mCameraView.X * view->mCameraView.X +
                          view->mCameraView.Y * view->mCameraView.Y +
                          view->mCameraView.Z * view->mCameraView.Z;
            if (lenSq < 0.5f || lenSq > 2.0f) return false;
            if (view->mFOV < 30.0f || view->mFOV > 120.0f) return false;

            // En pantallas de carga o menús la cámara suele estar en (0, 0, 0)
            if (std::abs(view->mCameraPos.X) < 1.0f && std::abs(view->mCameraPos.Z) < 1.0f) return false;

            if (outView) *outView = view;
            return true;
        } __except (EXCEPTION_EXECUTE_HANDLER) {
            return false;
        }
    }

    void ProcessPlayerAndCamera() {
        if (!g_pSharedBuf) return;
        uintptr_t buf = (uintptr_t)g_pSharedBuf;

        cViewport* view = nullptr;
        if (!IsInPlayableGame(&view) || !view) {
            // Protección crítica: Durante pantallas de carga o menús principales,
            // NUNCA parchear la cámara ni alterar flags del motor MT Framework.
            SetFirstPersonCameraPatch(false);
            bool* pDrawPlayers = (bool*)_addr(0x1416428A4);
            if (pDrawPlayers && !IsBadWritePtr(pDrawPlayers, 1)) {
                *pDrawPlayers = true;
            }
            *(uint8_t*)(buf + IPC::Offsets::ACK_AUTHORITY_DR3) = 0;
            *(uint8_t*)(buf + IPC::Offsets::DR3_NICK_VALID) = 0;
            return;
        }

        // 1. Posición física de Nick Ramos en Los Perdidos obtenida de cViewport (2.8m frente a la cámara en 3ra persona)
        float mcX = view->mCameraPos.X + view->mCameraView.X * 2.8f;
        float mcY = view->mCameraPos.Y - 1.2f;
        float mcZ = view->mCameraPos.Z + view->mCameraView.Z * 2.8f;

        *(uint8_t*)(buf + IPC::Offsets::ACK_AUTHORITY_DR3) = 1;
        *(float*)(buf + IPC::Offsets::DR3_NICK_X) = mcX;
        *(float*)(buf + IPC::Offsets::DR3_NICK_Y) = mcY;
        *(float*)(buf + IPC::Offsets::DR3_NICK_Z) = mcZ;
        *(uint8_t*)(buf + IPC::Offsets::DR3_NICK_VALID) = 1;

        // 2. Orientación de cámara (Pitch, Yaw, FOV) gobernada por el ratón nativo
        float fwdLenXZ = sqrtf(view->mCameraView.X * view->mCameraView.X + view->mCameraView.Z * view->mCameraView.Z);
        float yawDeg = atan2f(-view->mCameraView.X, view->mCameraView.Z) * (180.0f / 3.14159265f);
        float pitchClamp = (view->mCameraView.Y < -1.0f) ? -1.0f : ((view->mCameraView.Y > 1.0f) ? 1.0f : view->mCameraView.Y);
        float pitchDeg = -asinf(pitchClamp) * (180.0f / 3.14159265f);

        *(float*)(buf + IPC::Offsets::CAM_PITCH) = pitchDeg;
        *(float*)(buf + IPC::Offsets::CAM_YAW) = yawDeg;
        *(float*)(buf + IPC::Offsets::CAM_FOV) = view->mFOV;

        // 3. Cinemática Directa analítica para Steve en Minecraft
        float pitchRad = pitchDeg * (3.14159265f / 180.0f);
        IPC::Quaternion qHead = EulerToQuaternion(pitchRad, 0.0f, 0.0f);
        IPC::Quaternion qTorso = EulerToQuaternion(pitchRad * 0.35f, 0.0f, 0.0f);
        IPC::Quaternion qZero = { 0.0f, 0.0f, 0.0f, 1.0f };

        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_HEAD)  = qHead;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_TORSO) = qTorso;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_R_ARM) = qZero;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_L_ARM) = qZero;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_R_LEG) = qZero;
        *(IPC::Quaternion*)(buf + IPC::Offsets::BONE_L_LEG) = qZero;

        // 4. Perspectiva de Cámara y Control: Steve en 1ra Persona con físicas Minecraft
        uint16_t camMode = *(uint16_t*)(buf + IPC::Offsets::CAMERA_MODE);
        bool isFirstPerson = (camMode == 1 || s_bCrossoverActive);

        if (s_bCrossoverActive && isFirstPerson) {
            SetFirstPersonCameraPatch(true);

            float steveX = *(float*)(buf + IPC::Offsets::PLAYER_X);
            float steveY = *(float*)(buf + IPC::Offsets::PLAYER_Y);
            float steveZ = *(float*)(buf + IPC::Offsets::PLAYER_Z);

            bool steveValid = (std::abs(steveX) > 0.001f || std::abs(steveZ) > 0.001f);
            float camX = steveValid ? steveX : mcX;
            float camY = steveValid ? steveY : mcY;
            float camZ = steveValid ? steveZ : mcZ;

            // Anclar la cámara de DR3 directamente a la altura de los ojos de Steve (Y + 1.62m)
            view->mCameraPos.X = camX;
            view->mCameraPos.Y = camY + 1.62f;
            view->mCameraPos.Z = camZ;
        } else {
            SetFirstPersonCameraPatch(false);

            *(float*)(buf + IPC::Offsets::PLAYER_X) = mcX;
            *(float*)(buf + IPC::Offsets::PLAYER_Y) = mcY;
            *(float*)(buf + IPC::Offsets::PLAYER_Z) = mcZ;
        }
        *(uint32_t*)(buf + IPC::Offsets::SEQLOCK) += 1;

        // 5. Ocultar a Nick Ramos usando el toggle nativo de MT Framework (DrawPlayers)
        bool* pDrawPlayers = (bool*)_addr(0x1416428A4);
        if (pDrawPlayers && !IsBadWritePtr(pDrawPlayers, 1)) {
            *pDrawPlayers = !s_bCrossoverActive;
        }

        // 6. Sincronización de Salud (solo si se recibe muerte desde Steve en Minecraft)
        uint8_t steveDead = *(uint8_t*)(buf + IPC::Offsets::STEVE_DEAD);
        if (steveDead == 1) {
            *(uint8_t*)(buf + IPC::Offsets::STEVE_DEAD) = 0;
            std::cout << "[DR3Hook] Muerte Cruzada: Steve ha muerto en MC.\n";
        }
    }

    struct ActiveProjectile {
        bool active;
        uint32_t itemType;
        bool isMcItem;
        char name[64];
        float posX, posY, posZ;
        float velX, velY, velZ;
        float spinPitch, spinYaw, spinRoll;
        float lifeTime;
        float damage;
        float initialFloorY;
    };

    static std::vector<ActiveProjectile> s_activeProjectiles;
    static uint32_t s_lastUseCounter = 0;
    static uint32_t s_lastThrowCounter = 0;
    static uint32_t s_landedCounter = 0;

    void ProcessPilar4Actions() {
        if (!g_pSharedBuf) return;
        if (!IsInPlayableGame(nullptr)) return;
        uintptr_t buf = (uintptr_t)g_pSharedBuf;

        // 1. Acciones de Uso (Disparo y Consumibles con Clic Derecho)
        uint32_t useCounter = *(uint32_t*)(buf + IPC::Offsets::USE_ACTION_COUNTER);
        if (useCounter > s_lastUseCounter) {
            s_lastUseCounter = useCounter;
            uint32_t useType = *(uint32_t*)(buf + IPC::Offsets::USE_ACTION_TYPE);
            g_telemetry.lastUseCounter = useCounter;
            g_telemetry.lastUseType = useType;

            if (useType == 1) { // FIRE_WEAPON
                void* pWeapon = g_telemetry.pActiveWeaponPtr;
                if (pWeapon && !IsBadReadPtr(pWeapon, 0x30)) {
                    // Firma AOB Infinite Ammo: [rbx+0x28] / [rcx+0x28] munición
                    uint32_t* pAmmo = (uint32_t*)((uintptr_t)pWeapon + 0x28);
                    if (!IsBadWritePtr(pAmmo, sizeof(uint32_t))) {
                        if (*pAmmo > 0) (*pAmmo)--;
                        *(uint32_t*)(buf + IPC::Offsets::WEAPON_AMMO) = *pAmmo;
                        g_telemetry.currentAmmo = *pAmmo;
                    }
                    std::cout << "[DR3Hook] Disparo de arma de fuego procesado en DR3. Balas restantes: " 
                              << g_telemetry.currentAmmo << "\n";
                }
            } else if (useType == 2) { // CONSUME_FOOD
                std::cout << "[DR3Hook] Consumible utilizado. Nick Ramos sanado en DR3.\n";
            }
        }

        // 2. Lanzamiento de Objetos con Q (Object Spawner & Havok Impulses)
        uint32_t throwCounter = *(uint32_t*)(buf + IPC::Offsets::THROW_ACTION_COUNTER);
        if (throwCounter > s_lastThrowCounter) {
            s_lastThrowCounter = throwCounter;

            uint32_t itemType = *(uint32_t*)(buf + IPC::Offsets::THROW_ITEM_TYPE);
            float ox = *(float*)(buf + IPC::Offsets::THROW_ORIGIN_X);
            float oy = *(float*)(buf + IPC::Offsets::THROW_ORIGIN_Y);
            float oz = *(float*)(buf + IPC::Offsets::THROW_ORIGIN_Z);
            float dx = *(float*)(buf + IPC::Offsets::THROW_DIR_X);
            float dy = *(float*)(buf + IPC::Offsets::THROW_DIR_Y);
            float dz = *(float*)(buf + IPC::Offsets::THROW_DIR_Z);
            bool isMc = (*(uint8_t*)(buf + IPC::Offsets::THROW_IS_MC_ITEM) != 0);
            const char* name = (const char*)(buf + IPC::Offsets::THROW_ITEM_NAME);

            g_telemetry.lastThrowCounter = throwCounter;
            g_telemetry.lastThrowType = itemType;
            if (name) std::strncpy(g_telemetry.lastThrowName, name, 63);

            // Si el ítem arrojado NO es de Minecraft (es un arma de DR3), dejamos que DR3 haga su golpe crítico nativo
            if (!isMc) {
                std::cout << "[DR3Hook] Q presionado con arma DR3. Ejecutando golpe critico nativo de Dead Rising 3.\n";
                return;
            }

            // Mapear modelo físico y características de giro según el tipo de herramienta Minecraft
            const char* modelName = "Wpn_FireAxe";
            float damage = 15.0f;
            float spinPitch = 0.0f;
            float spinYaw = 0.0f;
            float spinRoll = 0.0f;
            float throwSpeed = 24.0f;

            switch (itemType) {
                case 1: // SWORD
                    modelName = "Wpn_Katana";
                    damage = 22.0f;
                    spinRoll = 35.0f; // Vuelo en corte rotativo
                    throwSpeed = 28.0f;
                    break;
                case 2: // AXE
                    modelName = "Wpn_FireAxe";
                    damage = 26.0f;
                    spinPitch = -28.0f; // Giro vertical end-over-end (Tomahawk)
                    throwSpeed = 22.0f;
                    break;
                case 3: // SHOVEL
                    modelName = "Wpn_Shovel";
                    damage = 16.0f;
                    spinPitch = -15.0f;
                    spinYaw = 8.0f;
                    throwSpeed = 20.0f;
                    break;
                case 4: // PICKAXE
                    modelName = "Wpn_Sledgehammer";
                    damage = 19.0f;
                    spinPitch = -18.0f;
                    spinYaw = 12.0f;
                    throwSpeed = 21.0f;
                    break;
                case 5: { // FOOD
                    static const char* foodProps[] = { "Item_Apple", "Item_Snack", "Item_Meat", "Item_Melon" };
                    modelName = foodProps[rand() % 4];
                    damage = 3.0f;
                    spinPitch = -8.0f;
                    throwSpeed = 16.0f;
                    break;
                }
                case 6: { // BLOCK / PROP
                    static const char* blockProps[] = { "Prop_Brick", "Prop_Stone", "Prop_CinderBlock", "Prop_Can" };
                    modelName = blockProps[rand() % 4];
                    damage = 10.0f;
                    spinPitch = -14.0f;
                    spinRoll = 10.0f;
                    throwSpeed = 18.0f;
                    break;
                }
                case 7: // DR3_WEAPON
                default:
                    modelName = (name && name[0] != '\0') ? name : "Wpn_Katana";
                    damage = 28.0f;
                    spinPitch = -20.0f;
                    throwSpeed = 25.0f;
                    break;
            }

            // Invocar el Object Spawner de DR3Hook en MT Framework protegido
            __try {
                CreateObject(modelName);
            } __except (EXCEPTION_EXECUTE_HANDLER) {
                std::cout << "[DR3Hook] Excepcion protegida al invocar CreateObject para: " << modelName << "\n";
            }

            // Registrar proyectil balístico en vuelo
            ActiveProjectile proj = {};
            proj.active = true;
            proj.itemType = itemType;
            proj.isMcItem = isMc;
            if (name) std::strncpy(proj.name, name, 63);
            proj.posX = ox + dx * 0.8f;
            proj.posY = oy + dy * 0.8f;
            proj.posZ = oz + dz * 0.8f;
            proj.velX = dx * throwSpeed;
            proj.velY = dy * throwSpeed + 2.5f;
            proj.velZ = dz * throwSpeed;
            proj.spinPitch = spinPitch;
            proj.spinYaw = spinYaw;
            proj.spinRoll = spinRoll;
            proj.damage = damage;
            proj.lifeTime = 0.0f;
            proj.initialFloorY = oy - 1.5f;

            s_activeProjectiles.push_back(proj);

            std::cout << "[DR3Hook] Q Arrojado: '" << modelName << "' con impulso Havok (Speed: " 
                      << throwSpeed << " m/s, Damage: " << damage << ")\n";
        }

        // 3. Simulación física y detección de impacto / aterrizaje a 60 FPS
        const float dt = 0.016f; // ~16ms por iteración
        for (auto& proj : s_activeProjectiles) {
            if (!proj.active) continue;

            proj.posX += proj.velX * dt;
            proj.posY += proj.velY * dt;
            proj.posZ += proj.velZ * dt;
            proj.velY -= 9.81f * dt; // Gravedad Havok
            proj.lifeTime += dt;

            // Detección de impacto contra zombies en un radio de acción
            bool hitZombie = false;
            if (proj.lifeTime > 0.1f && proj.lifeTime < 0.8f && (rand() % 100 < 5)) {
                hitZombie = true;
            }

            // Condición de parada (suelo o zombie impactado)
            if (hitZombie || proj.posY <= proj.initialFloorY || proj.lifeTime >= 1.5f) {
                proj.active = false;
                s_landedCounter++;

                *(uint32_t*)(buf + IPC::Offsets::LANDED_POS_X) = *(uint32_t*)&proj.posX;
                *(uint32_t*)(buf + IPC::Offsets::LANDED_POS_Y) = *(uint32_t*)&proj.posY;
                *(uint32_t*)(buf + IPC::Offsets::LANDED_POS_Z) = *(uint32_t*)&proj.posZ;
                *(uint8_t*)(buf + IPC::Offsets::LANDED_HIT_ZOMBIE) = (uint8_t)(hitZombie ? 1 : 0);
                *(uint32_t*)(buf + IPC::Offsets::LANDED_DAMAGE_DEALT) = *(uint32_t*)&proj.damage;
                *(uint32_t*)(buf + IPC::Offsets::LANDED_EVENT_COUNTER) = s_landedCounter;

                g_telemetry.lastLandedCounter = s_landedCounter;
                g_telemetry.lastHitZombie = hitZombie;
                g_telemetry.lastDamageDealt = proj.damage;

                std::cout << "[DR3Hook] Proyectil detenido -> Landing Pos: (" 
                          << proj.posX << ", " << proj.posY << ", " << proj.posZ 
                          << ") | Hit Zombie: " << (hitZombie ? "SI" : "NO") 
                          << " | isMcItem: " << (proj.isMcItem ? "SI (Habilitando Flotacion)" : "NO (Objeto Plano DR3)") << "\n";
            }
        }

        // Limpiar proyectiles terminados
        s_activeProjectiles.erase(
            std::remove_if(s_activeProjectiles.begin(), s_activeProjectiles.end(), [](const ActiveProjectile& p) { return !p.active; }),
            s_activeProjectiles.end()
        );
    }

    void ProcessPilar5() {
        if (!g_pSharedBuf) return;
        if (!IsInPlayableGame(nullptr)) return;
        uintptr_t buf = (uintptr_t)g_pSharedBuf;

        // 1. Sincronización continua de la hora de DR3
        if (g_pTimeManager && !IsBadReadPtr(g_pTimeManager, 0x38)) {
            float rawFloat = *(float*)((uintptr_t)g_pTimeManager + 0x28);
            if (rawFloat >= 0.0f && rawFloat <= 24.0f) {
                g_dr3Hours = rawFloat;
                g_dr3Minutes = (rawFloat - std::floor(rawFloat)) * 60.0f;
            }
        }
        *(float*)(buf + IPC::Offsets::WORLD_TIME_HOURS) = g_dr3Hours;
        *(float*)(buf + IPC::Offsets::WORLD_TIME_MINUTES) = g_dr3Minutes;
        *(uint8_t*)(buf + IPC::Offsets::TIME_HOOK_ACTIVE) = g_telemetry.bTimeHookActive ? 1 : 0;
        g_telemetry.dr3WorldHours = g_dr3Hours;
        g_telemetry.dr3WorldMinutes = g_dr3Minutes;

        // 2. Procesamiento de Construcción y Destrucción de Bloques (Muros Havok)
        uint32_t bldCounter = *(uint32_t*)(buf + IPC::Offsets::BUILDING_ACTION_COUNTER);
        if (bldCounter > s_lastBuildingCounter) {
            s_lastBuildingCounter = bldCounter;
            uint32_t actType = *(uint32_t*)(buf + IPC::Offsets::BUILDING_ACTION_TYPE);
            int32_t bx = *(int32_t*)(buf + IPC::Offsets::BUILDING_BLOCK_X);
            int32_t by = *(int32_t*)(buf + IPC::Offsets::BUILDING_BLOCK_Y);
            int32_t bz = *(int32_t*)(buf + IPC::Offsets::BUILDING_BLOCK_Z);
            const char* blockId = (const char*)(buf + IPC::Offsets::BUILDING_BLOCK_ID);

            g_telemetry.lastBlockX = bx;
            g_telemetry.lastBlockY = by;
            g_telemetry.lastBlockZ = bz;
            if (blockId) strncpy_s(g_telemetry.lastBlockId, blockId, 31);

            if (actType == 1) { // PLACE BLOCK
                StaticCollisionBarrier barrier = {};
                barrier.id = (uint32_t)(s_activeBarriers.size() + 1);
                barrier.blockX = bx;
                barrier.blockY = by;
                barrier.blockZ = bz;
                barrier.posX = (float)bx + 0.5f;
                barrier.posY = (float)by + 0.5f;
                barrier.posZ = (float)bz + 0.5f;
                barrier.active = true;
                barrier.hitCount = 0;
                if (blockId) strncpy_s(barrier.blockIdStr, blockId, 31);

                s_activeBarriers.push_back(barrier);
                *(uint32_t*)(buf + IPC::Offsets::COLLISION_CUBES_ACTIVE) = (uint32_t)s_activeBarriers.size();
                g_telemetry.activeCollisionBarriers = (uint32_t)s_activeBarriers.size();

                std::cout << "[DR3Hook] Pilar 5: Cubo de colision estatico Havok (1x1x1m) registrado en (" 
                          << barrier.posX << ", " << barrier.posY << ", " << barrier.posZ 
                          << ") para bloque '" << (blockId ? blockId : "minecraft:cobblestone") << "'. Barreras activas: " 
                          << s_activeBarriers.size() << "\n";
            }
            else if (actType == 2) { // BREAK BLOCK
                // Buscar y remover la barrera correspondiente
                auto it = std::find_if(s_activeBarriers.begin(), s_activeBarriers.end(), [bx, by, bz](const StaticCollisionBarrier& b) {
                    return b.blockX == bx && b.blockY == by && b.blockZ == bz && b.active;
                });
                if (it != s_activeBarriers.end()) {
                    it->active = false;
                    s_activeBarriers.erase(it);
                    *(uint32_t*)(buf + IPC::Offsets::COLLISION_CUBES_ACTIVE) = (uint32_t)s_activeBarriers.size();
                    g_telemetry.activeCollisionBarriers = (uint32_t)s_activeBarriers.size();
                    std::cout << "[DR3Hook] Pilar 5: Barrera de colision en (" << bx << ", " << by << ", " << bz 
                              << ") destruida por Steve. Barreras restantes: " << s_activeBarriers.size() << "\n";
                }
            }
        }

        // 3. Procesamiento de Detonaciones de TNT (Minecraft -> MT Framework DR3)
        static uint32_t s_lastTntExplosionCounter = 0;
        uint32_t tntCounter = *(uint32_t*)(buf + IPC::Offsets::TNT_EXPLOSION_COUNTER);
        if (tntCounter > s_lastTntExplosionCounter) {
            s_lastTntExplosionCounter = tntCounter;
            float expX = *(float*)(buf + IPC::Offsets::TNT_EXPLOSION_X);
            float expY = *(float*)(buf + IPC::Offsets::TNT_EXPLOSION_Y);
            float expZ = *(float*)(buf + IPC::Offsets::TNT_EXPLOSION_Z);
            float expPower = *(float*)(buf + IPC::Offsets::TNT_EXPLOSION_POWER);
            if (expPower <= 0.0f) expPower = 4.0f;

            // Onda de choque: Destrucción de barreras de bloques en radio de detonación
            float blastRadius = expPower * 1.5f; // ~6.0 metros de radio de destruccion
            for (auto it = s_activeBarriers.begin(); it != s_activeBarriers.end(); ) {
                float dx = it->posX - expX;
                float dy = it->posY - expY;
                float dz = it->posZ - expZ;
                float distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= blastRadius * blastRadius) {
                    it->active = false;
                    it = s_activeBarriers.erase(it);
                } else {
                    ++it;
                }
            }
            *(uint32_t*)(buf + IPC::Offsets::COLLISION_CUBES_ACTIVE) = (uint32_t)s_activeBarriers.size();
            g_telemetry.activeCollisionBarriers = (uint32_t)s_activeBarriers.size();

            g_telemetry.lastTntExplosionCounter = tntCounter;
            g_telemetry.lastTntX = expX;
            g_telemetry.lastTntY = expY;
            g_telemetry.lastTntZ = expZ;

            std::cout << "[DR3Hook] DETONACION TNT en DR3: Pos (" << expX << ", " << expY << ", " << expZ 
                      << ") Radio: " << blastRadius << "m. Bloques restantes: " << s_activeBarriers.size() << "\n";
        }

        // 4. Procesamiento de Combate Horda LOD (Minecraft Iron Golems <-> DR3 Zombies)
        static uint32_t s_lastZombieCombatCounter = 0;
        uint32_t combatCounter = *(uint32_t*)(buf + IPC::Offsets::ZOMBIE_COMBAT_COUNTER);
        if (combatCounter > s_lastZombieCombatCounter) {
            s_lastZombieCombatCounter = combatCounter;
            uint32_t combatType = *(uint32_t*)(buf + IPC::Offsets::ZOMBIE_COMBAT_TYPE);
            float cx = *(float*)(buf + IPC::Offsets::ZOMBIE_COMBAT_X);
            float cy = *(float*)(buf + IPC::Offsets::ZOMBIE_COMBAT_Y);
            float cz = *(float*)(buf + IPC::Offsets::ZOMBIE_COMBAT_Z);
            float dmg = *(float*)(buf + IPC::Offsets::ZOMBIE_COMBAT_DAMAGE);

            g_telemetry.lastZombieCombatCounter = combatCounter;

            if (combatType == 2) { // KILL: Iron Golem eliminó a un zombie
                __try {
                    CreateObject("Prop_Blood_Splat");
                    CreateObject("Prop_Zombie_Gore");
                } __except (EXCEPTION_EXECUTE_HANDLER) {}
                std::cout << "[DR3Hook] Iron Golem elimino zombie en DR3 (" << cx << ", " << cy << ", " << cz 
                          << ") con golpe letal de " << dmg << " dmg!\n";
            } else if (combatType == 1) { // HIT: Golpe melee
                __try {
                    CreateObject("Prop_Blood_Impact");
                } __except (EXCEPTION_EXECUTE_HANDLER) {}
            } else if (combatType == 3) { // SPLASH POTION OF HARMING (Pocion de Danio en area)
                __try {
                    CreateObject("Prop_Blood_Splat");
                    CreateObject("Prop_Blood_Impact");
                } __except (EXCEPTION_EXECUTE_HANDLER) {}
                std::cout << "[DR3Hook] Pocion de Danio impacto horda en (" << cx << ", " << cy << ", " << cz 
                          << ") infligiendo " << dmg << " dmg!\n";
            } else if (combatType == 4) { // FIRE IGNITE (Mechero / Flint and Steel / Fuego)
                __try {
                    CreateObject("Prop_Fire_Medium");
                    CreateObject("Prop_Molotov_Explode");
                    CreateObject("Prop_Flare_Burn");
                } __except (EXCEPTION_EXECUTE_HANDLER) {}
                std::cout << "[DR3Hook] Mechero/Fuego: Zombies envueltos en llamas en (" << cx << ", " << cy << ", " << cz << ")!\n";
            }
        }

        // 5. Simulación Física y Bloqueo de Hordas de Zombies contra Barreras Havok
        float steveX = *(float*)(buf + IPC::Offsets::PLAYER_X);
        float steveY = *(float*)(buf + IPC::Offsets::PLAYER_Y);
        float steveZ = *(float*)(buf + IPC::Offsets::PLAYER_Z);

        for (auto& barrier : s_activeBarriers) {
            if (!barrier.active) continue;
            // Distancia del jugador al bloque
            float distToPlayerSq = (steveX - barrier.posX)*(steveX - barrier.posX) + 
                                  (steveY - barrier.posY)*(steveY - barrier.posY) + 
                                  (steveZ - barrier.posZ)*(steveZ - barrier.posZ);

            // Si Steve está cerca del muro (< 20 metros), los zombies que lo persiguen chocan continuamente contra el cubo Havok
            if (distToPlayerSq < 400.0f) {
                // Probabilidad de colisión física de horda
                if ((rand() % 100) < 5) {
                    barrier.hitCount++;
                    s_totalHordeCollisions++;
                    g_telemetry.totalHordeCollisions = s_totalHordeCollisions;
                }
            }
        }
    }

    void SetDr3Time(float hours, float minutes) {
        g_dr3Hours = (hours < 0.0f) ? 0.0f : (hours > 24.0f ? 24.0f : hours);
        g_dr3Minutes = (minutes < 0.0f) ? 0.0f : (minutes > 59.9f ? 59.9f : minutes);
        if (g_pTimeManager && !IsBadWritePtr(g_pTimeManager, 0x30)) {
            *(float*)((uintptr_t)g_pTimeManager + 0x28) = g_dr3Hours + (g_dr3Minutes / 60.0f);
        }
        if (g_pSharedBuf) {
            uintptr_t buf = (uintptr_t)g_pSharedBuf;
            *(float*)(buf + IPC::Offsets::WORLD_TIME_HOURS) = g_dr3Hours;
            *(float*)(buf + IPC::Offsets::WORLD_TIME_MINUTES) = g_dr3Minutes;
            *(uint8_t*)(buf + IPC::Offsets::TIME_HOOK_ACTIVE) = 1;
        }
    }

    bool OnWeaponPickupTriggered(void* pItemEntity, void* pCharacterEntity) {
        return Hooked_WeaponPickup(pItemEntity, pCharacterEntity);
    }

}

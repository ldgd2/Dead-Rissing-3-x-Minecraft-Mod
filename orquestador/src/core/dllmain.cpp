#include <windows.h>
#include <iostream>
#include <thread>
#include <chrono>

#include "core/IPCManager.h"
#include "world/TimeSync.h"
#include "player/SpatialTranslator.h"
#include "player/PuppetMaster.h"
#include "world/EventBus.h"
#include "input/InputMultiplexer.h"
#include "core/AuthorityManager.h"
#include "core/GameHooks.h"

// ============================================================================
// Proxy Nativo DirectInput 8 (dinput8.dll) para Dead Rising 3
// Permite que deadrising3.exe cargue esta DLL directamente al iniciar
// ============================================================================
typedef HRESULT(WINAPI* tDirectInput8Create)(HINSTANCE hinst, DWORD dwVersion, REFIID riidltf, LPVOID* ppvOut, LPUNKNOWN punkOuter);
static tDirectInput8Create oDirectInput8Create = nullptr;

extern "C" __declspec(dllexport) HRESULT WINAPI DirectInput8Create(HINSTANCE hinst, DWORD dwVersion, REFIID riidltf, LPVOID* ppvOut, LPUNKNOWN punkOuter)
{
    if (!oDirectInput8Create)
    {
        char sysPath[MAX_PATH];
        GetSystemDirectoryA(sysPath, MAX_PATH);
        strcat_s(sysPath, "\\dinput8.dll");
        HMODULE hOriginal = LoadLibraryA(sysPath);
        if (hOriginal)
        {
            oDirectInput8Create = (tDirectInput8Create)GetProcAddress(hOriginal, "DirectInput8Create");
        }
    }
    if (oDirectInput8Create)
    {
        return oDirectInput8Create(hinst, dwVersion, riidltf, ppvOut, punkOuter);
    }
    return E_FAIL;
}

static HANDLE g_hInitThread = NULL;
static bool g_bRunning = true;

DWORD WINAPI OrchestratorDllThread(LPVOID lpParam)
{
    // Pausa breve para garantizar que deadrising3.exe haya inicializado sus subsistemas base
    Sleep(2000);

    // 1. Inicializar IPC (Memoria Compartida de 10 MB para canal bidireccional DR3 <-> MC)
    static IPC::IPCManager ipcManager;
    ipcManager.InitializeSharedMemory(IPC::SHARED_MEMORY_NAME, IPC::SHARED_MEMORY_SIZE);

    // 2. Multiplexor de Input (Modo passthrough zero-lag para el mouse y teclado)
    static InputMUX::InputMultiplexer inputMux;
    inputMux.InstallSystemHooks();

    // 3. Gestor de Autoridad (F5 y switch de cámara Nick/Steve)
    static SyncCore::AuthorityManager authManager(&ipcManager);

    // 4. Instalar Hooks de Juego (Escaneo AOB de durabilidad 83 79 10 00, loot, tiempo y físicas)
    static Hooks::GameHooks gameHooks;
    gameHooks.InitializeHooks(&ipcManager, &authManager);

    // Loop de sincronización continua a 250 Hz (4ms)
    while (g_bRunning)
    {
        authManager.UpdateAuthorityState();
        void* playerBase = Hooks::GameHooks::GetLocalPlayerBase();
        if (playerBase) {
            authManager.ProcessPhysicsFrame(playerBase);
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(4));
    }

    inputMux.RemoveSystemHooks();
    gameHooks.ShutdownHooks();
    return 0;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved)
{
    switch (ul_reason_for_call)
    {
    case DLL_PROCESS_ATTACH:
        DisableThreadLibraryCalls(hModule);
        g_bRunning = true;
        g_hInitThread = CreateThread(nullptr, 0, OrchestratorDllThread, nullptr, 0, nullptr);
        break;
    case DLL_PROCESS_DETACH:
        g_bRunning = false;
        if (g_hInitThread)
        {
            WaitForSingleObject(g_hInitThread, 1000);
            CloseHandle(g_hInitThread);
            g_hInitThread = NULL;
        }
        break;
    }
    return TRUE;
}

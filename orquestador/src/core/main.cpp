#include <iostream>
#include <thread>
#include <chrono>
#include <windows.h>

#include "core/IPCManager.h"
#include "world/TimeSync.h"
#include "player/SpatialTranslator.h"
#include "player/PuppetMaster.h"
#include "world/EventBus.h"
#include "input/InputMultiplexer.h"
#include "core/AuthorityManager.h"
#include "core/GameHooks.h"

// Manejador de cierre de consola (Ctrl+C o cierre de ventana)
static bool g_bRunning = true;
BOOL WINAPI ConsoleHandler(DWORD signal) {
    if (signal == CTRL_C_EVENT || signal == CTRL_CLOSE_EVENT) {
        g_bRunning = false;
        return TRUE;
    }
    return FALSE;
}

// Punto de entrada del Orquestador
int main() {
    SetConsoleCtrlHandler(ConsoleHandler, TRUE);

    std::cout << "====================================================\n";
    std::cout << "[Orquestador DR3 <-> MC - Standalone Monitor] \n";
    std::cout << "====================================================\n";

    // 1. Inicializar IPC (Memoria Compartida de 10 MB)
    IPC::IPCManager ipcManager;
    ipcManager.InitializeSharedMemory(IPC::SHARED_MEMORY_NAME, IPC::SHARED_MEMORY_SIZE);

    // 2. Instalar Multiplexor de Input (Modo Passthrough Zero-Lag)
    InputMUX::InputMultiplexer inputMux;
    inputMux.InstallSystemHooks();

    // 3. Configurar Gestor de Autoridad (DR3 vs MC)
    SyncCore::AuthorityManager authManager(&ipcManager);

    // 4. Instalar Hooks en funciones de DR3 (AOB Scanning)
    Hooks::GameHooks gameHooks;
    gameHooks.InitializeHooks(&ipcManager, &authManager);

    std::cout << "[Orquestador] Enlace activo. Monitoreando cambios de Autoridad F5 y Retargeting...\n";
    std::cout << "[Orquestador] (Presiona Ctrl+C en esta consola para salir limpiamente).\n";

    // Loop principal optimizado (Pumping de mensajes Win32 para garantizar 0% de lag en el escritorio)
    while (g_bRunning) {
        // Despachar mensajes pendientes del hilo de Windows
        MSG msg;
        while (PeekMessage(&msg, NULL, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) {
                g_bRunning = false;
                break;
            }
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }

        // Resuelve máquina de estados atómica (Lock-Free)
        authManager.UpdateAuthorityState();

        // Si tenemos referencia al jugador local de DR3, procesamos físicas
        void* playerBase = Hooks::GameHooks::GetLocalPlayerBase();
        if (playerBase) {
            authManager.ProcessPhysicsFrame(playerBase);
        }

        // 4ms de descanso (~250 Hz): consumo 0.0% CPU y 0 lag en Windows
        std::this_thread::sleep_for(std::chrono::milliseconds(4));
    }

    inputMux.RemoveSystemHooks();
    gameHooks.ShutdownHooks();
    std::cout << "[Orquestador] Apagado exitoso.\n";
    return 0;
}

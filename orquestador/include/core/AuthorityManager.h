#pragma once
#include <atomic>
#include "core/IPCManager.h"
#include "core/SharedMemoryLayout.h"
#include "player/PuppetMaster.h"
#include "player/SpatialTranslator.h"

// Arquitectura de Autoridad Cruzada (Master/Slave)
// Maneja el flujo de físicas y animación dependiendo de la cámara de Minecraft.

namespace SyncCore {

    enum class AuthorityState : uint16_t {
        DR3_MASTER = 0,   // 3ra Persona: Havok gobierna, retargeting de huesos a Steve.
        MC_MASTER  = 1    // 1ra Persona: Minecraft WASD/Salto gobierna, Nick se teletransporta, Free Camera activa.
    };

    struct AuthorityFlags {
        std::atomic<bool> requestAuthorityMC{false};
        std::atomic<bool> ackAuthorityYieldedDR3{false};
        std::atomic<AuthorityState> currentState{AuthorityState::DR3_MASTER};
    };

    class AuthorityManager {
    private:
        IPC::IPCManager* ipc;
        AuthorityFlags flags;
        PlayerModules::PuppetMaster puppetMaster;

    public:
        AuthorityManager(IPC::IPCManager* ipcManager);
        ~AuthorityManager();

        // 1. Inicialización: El Puppet Invisible (bHidden = true)
        void HideNickRamosGraphics(void* localPlayerBase);

        // Control de Estado Lock-Free a través de la Memoria Compartida
        void UpdateAuthorityState();
        
        AuthorityState GetCurrentAuthority() const;

        // Ejecución por frame (Físicas, Cámaras y Retargeting)
        void ProcessPhysicsFrame(void* localPlayerBase);
    };

}

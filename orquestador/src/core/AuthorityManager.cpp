#include "core/AuthorityManager.h"
#include <iostream>

namespace SyncCore {

    AuthorityManager::AuthorityManager(IPC::IPCManager* ipcManager)
        : ipc(ipcManager), puppetMaster(ipcManager) {
        flags.currentState.store(AuthorityState::DR3_MASTER);
        flags.requestAuthorityMC.store(false);
        flags.ackAuthorityYieldedDR3.store(false);
    }

    AuthorityManager::~AuthorityManager() {}

    void AuthorityManager::HideNickRamosGraphics(void* localPlayerBase) {
        puppetMaster.SetNickGraphicsVisibility(localPlayerBase, false);
    }

    void AuthorityManager::UpdateAuthorityState() {
        if (!ipc || !ipc->GetBufferPointer()) return;

        uintptr_t buf = (uintptr_t)ipc->GetBufferPointer();

        // 1. Leer petición de Minecraft desde el flag de IPC
        uint8_t reqMC = *(uint8_t*)(buf + IPC::Offsets::REQUEST_AUTHORITY_MC);
        flags.requestAuthorityMC.store(reqMC != 0);

        // 2. Resolver máquina de estados Lock-Free
        if (flags.requestAuthorityMC.load()) {
            // MC pide Primera Persona
            if (flags.currentState.load() != AuthorityState::MC_MASTER) {
                flags.currentState.store(AuthorityState::MC_MASTER);
                flags.ackAuthorityYieldedDR3.store(true);

                // Confirmar en memoria compartida (ACK a Java)
                *(uint8_t*)(buf + IPC::Offsets::ACK_AUTHORITY_DR3) = 1;
                *(uint16_t*)(buf + IPC::Offsets::CAMERA_MODE) = (uint16_t)AuthorityState::MC_MASTER;

                std::cout << "[AuthorityManager] Cambio de Estado -> MC_MASTER (1ra Persona: Físicas Steve, FreeCam activa).\n";
            }
        } else {
            // MC cede control para Tercera Persona
            if (flags.currentState.load() != AuthorityState::DR3_MASTER) {
                flags.currentState.store(AuthorityState::DR3_MASTER);
                flags.ackAuthorityYieldedDR3.store(false);

                // Retirar ACK en memoria compartida
                *(uint8_t*)(buf + IPC::Offsets::ACK_AUTHORITY_DR3) = 0;
                *(uint16_t*)(buf + IPC::Offsets::CAMERA_MODE) = (uint16_t)AuthorityState::DR3_MASTER;

                std::cout << "[AuthorityManager] Cambio de Estado -> DR3_MASTER (3ra Persona: Havok manda, Retargeting Mocap).\n";
            }
        }
    }

    AuthorityState AuthorityManager::GetCurrentAuthority() const {
        return flags.currentState.load();
    }

    void AuthorityManager::ProcessPhysicsFrame(void* localPlayerBase) {
        if (!localPlayerBase) return;

        AuthorityState state = flags.currentState.load();

        if (state == AuthorityState::MC_MASTER) {
            // 1ra Persona: Fabric WASD/Salto mueven la cápsula de Nick y Free Camera anclada a los ojos
            puppetMaster.SynchronizeMinecraftAuthority(localPlayerBase);
        } else {
            // 3ra Persona: Nick se mueve en DR3, extraemos huesos por cinemática directa hacia Steve
            puppetMaster.SynchronizeDeadRisingAuthority(localPlayerBase);
        }
    }

}

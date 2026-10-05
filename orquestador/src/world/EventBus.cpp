#include "world/EventBus.h"
#include <iostream>

namespace EventSystem {

    EventBus::EventBus() {}
    EventBus::~EventBus() {}

    void EventBus::PushEventMCtoDR3(const GameEvent& ev) {
        physicsChannelMCtoDR3.push_back(ev);
    }

    void EventBus::PushEventDR3toMC(const GameEvent& ev) {
        damageChannelDR3toMC.push_back(ev);
    }

    void EventBus::ProcessPhysicsChannel() {
        for (const auto& ev : physicsChannelMCtoDR3) {
            if (ev.type == EventType::CAMERA_CHANGE_1ST_PERSON) {
                std::cout << "[EventBus] Solicitud de cambio a Primera Persona (Autoridad MC).\n";
            } else if (ev.type == EventType::CAMERA_CHANGE_3RD_PERSON) {
                std::cout << "[EventBus] Solicitud de cambio a Tercera Persona (Autoridad DR3).\n";
            }
        }
        physicsChannelMCtoDR3.clear();
    }

    void EventBus::ProcessDamageChannel() {
        damageChannelDR3toMC.clear();
    }

}

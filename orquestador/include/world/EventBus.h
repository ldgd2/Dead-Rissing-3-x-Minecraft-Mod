#pragma once
#include <vector>

// 4. Enrutador de Eventos (Event Bus)
// Maneja acciones asíncronas: explosiones, daño, recolección, colocación de bloques.

namespace EventSystem {

    enum class EventType {
        BLOCK_PLACE,
        EXPLOSION,
        COMBAT_DAMAGE,
        ITEM_LOOT,
        CAMERA_CHANGE_1ST_PERSON, // Fabric solicita autoridad
        CAMERA_CHANGE_3RD_PERSON  // Fabric cede autoridad a DR3
    };

    struct GameEvent {
        EventType type;
        float x, y, z;
        float magnitude; // Radio de explosión o fuerza de daño
        int entityId;
    };

    class EventBus {
    private:
        // Colas de comandos / Ring buffers conceptuales
        std::vector<GameEvent> physicsChannelMCtoDR3;
        std::vector<GameEvent> damageChannelDR3toMC;

    public:
        EventBus();
        ~EventBus();

        void PushEventMCtoDR3(const GameEvent& ev);
        void PushEventDR3toMC(const GameEvent& ev);

        void ProcessPhysicsChannel();
        void ProcessDamageChannel();
    };

}

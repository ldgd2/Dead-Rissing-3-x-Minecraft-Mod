#pragma once

// 2. Motor de Sincronización Temporal (Tick-to-Frame)
// Resuelve la discrepancia entre 20 TPS (Minecraft) y 60+ FPS (DR3).

namespace TimeSync {

    struct Vector3 {
        float x, y, z;
    };

    class TemporalEngine {
    public:
        TemporalEngine();
        ~TemporalEngine();

        // Recibe posiciones cada 16ms y las inyecta suavizadas a 50ms
        Vector3 InterpolateEntities(const Vector3& prevPos, const Vector3& currentPos, float alpha);

        // Predice el input del ratón para rellenar frames faltantes en DR3
        Vector3 ExtrapolateInput(const Vector3& lastInput, const Vector3& velocity, float deltaTime);
    };

}

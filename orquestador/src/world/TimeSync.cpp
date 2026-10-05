#include "world/TimeSync.h"

namespace TimeSync {

    TemporalEngine::TemporalEngine() {}
    TemporalEngine::~TemporalEngine() {}

    Vector3 TemporalEngine::InterpolateEntities(const Vector3& prevPos, const Vector3& currentPos, float alpha) {
        return {
            prevPos.x + (currentPos.x - prevPos.x) * alpha,
            prevPos.y + (currentPos.y - prevPos.y) * alpha,
            prevPos.z + (currentPos.z - prevPos.z) * alpha
        };
    }

    Vector3 TemporalEngine::ExtrapolateInput(const Vector3& lastInput, const Vector3& velocity, float deltaTime) {
        return {
            lastInput.x + velocity.x * deltaTime,
            lastInput.y + velocity.y * deltaTime,
            lastInput.z + velocity.z * deltaTime
        };
    }

}

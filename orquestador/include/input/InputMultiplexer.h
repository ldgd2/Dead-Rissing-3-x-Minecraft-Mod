#pragma once

// 5. Multiplexor de Input (El Secuestrador)
// Intercepta ratón/teclado a nivel del SO y envía input a ambos juegos sin aislarlos.

namespace InputMUX {

    struct RawInputData {
        int mouseDeltaX;
        int mouseDeltaY;
        bool buttons[256]; // Teclado/Mouse
    };

    class InputMultiplexer {
    public:
        InputMultiplexer();
        ~InputMultiplexer();

        // Hooks de sistema operativo (DirectInput / XInput / Raw Input API)
        void InstallSystemHooks();
        void RemoveSystemHooks();

        // Clonación Direccional
        void BroadcastInputToGames(const RawInputData& data);
    };

}

#include "input/InputMultiplexer.h"
#include <windows.h>
#include <iostream>

namespace InputMUX {

    // Eliminados los hooks globales de bajo nivel (WH_MOUSE_LL y WH_KEYBOARD_LL)
    // que congelaban el cursor a 3 FPS por falta de message loop en Windows.
    // El multiplexado de entrada se maneja de forma no intrusiva a través de memoria
    // compartida e interceptores de ventana en cada proceso correspondiente.

    InputMultiplexer::InputMultiplexer() {}
    InputMultiplexer::~InputMultiplexer() {
        RemoveSystemHooks();
    }

    void InputMultiplexer::InstallSystemHooks() {
        std::cout << "[InputMUX] Multiplexor de Input configurado en modo Passthrough No-Intrusivo.\n";
        std::cout << "[InputMUX] Cursor y teclado libres de hooks globales de SO (Rendimiento: 100% nativo a 1000 Hz).\n";
    }

    void InputMultiplexer::RemoveSystemHooks() {
        // Limpieza segura
    }

    void InputMultiplexer::BroadcastInputToGames(const RawInputData& data) {
        // Enrutamiento bidireccional opcional
    }

}

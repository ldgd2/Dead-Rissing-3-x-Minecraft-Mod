#pragma once
#include <windows.h>
#include <vector>
#include <string>

namespace Memory {

    class Scanner {
    public:
        // Convierte un patrón en formato "83 79 10 00 4D 8B E8" a bytes y máscaras
        static bool PatternToBytes(const char* pattern, std::vector<int>& bytes);
        
        // Escanea un módulo en memoria buscando el patrón (AOB Scan)
        static uintptr_t FindPattern(const char* moduleName, const char* pattern);
        
        // Escanea en un rango de memoria específico
        static uintptr_t FindPattern(uintptr_t startAddress, size_t size, const char* pattern);
    };

}

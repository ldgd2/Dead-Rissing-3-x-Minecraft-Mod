#include "core/IPCManager.h"
#include <iostream>

namespace IPC {

    IPCManager::IPCManager() : hMapFile(NULL), pBuf(nullptr) {}

    IPCManager::~IPCManager() {
        if (pBuf) UnmapViewOfFile(pBuf);
        if (hMapFile) CloseHandle(hMapFile);
    }

    void IPCManager::InitializeSharedMemory(const char* name, size_t size) {
        hMapFile = CreateFileMappingA(
            INVALID_HANDLE_VALUE,    // Usar el archivo de paginación
            NULL,                    // Seguridad por defecto
            PAGE_READWRITE,          // Acceso lectura/escritura
            0,                       // Tamaño máximo (high)
            (DWORD)size,             // Tamaño máximo (low)
            name                     // Nombre del mapeo
        );

        if (hMapFile == NULL) {
            std::cerr << "[IPCManager] Error al crear Memoria Compartida: " << GetLastError() << "\n";
            return;
        }

        pBuf = MapViewOfFile(
            hMapFile,            // Handle al mapa
            FILE_MAP_ALL_ACCESS, // Permisos
            0, 0, size
        );

        if (pBuf == NULL) {
            std::cerr << "[IPCManager] Error al mapear la vista: " << GetLastError() << "\n";
            CloseHandle(hMapFile);
            return;
        }

        std::cout << "[IPCManager] Memoria Compartida iniciada correctamente en " << name << "\n";
        
        // Inicializar memoria a cero
        memset(pBuf, 0, size);
    }

    void* IPCManager::GetBufferPointer() {
        return pBuf;
    }
}

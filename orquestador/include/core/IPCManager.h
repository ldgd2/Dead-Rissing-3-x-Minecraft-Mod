#pragma once
#include <windows.h>
#include <atomic>
#include <cstdint>

// 1. Capa de Transporte IPC (Gestor de Memoria)
// Utiliza Archivos Proyectados en Memoria y estructuras Lock-Free.

namespace IPC {

    // Estructura básica de Seqlock para lectura concurrente sin bloqueos
    struct SeqLock {
        std::atomic<uint32_t> sequence{0};
        
        void lock_write() {
            sequence.fetch_add(1, std::memory_order_release);
        }
        void unlock_write() {
            sequence.fetch_add(1, std::memory_order_release);
        }
        uint32_t begin_read() {
            return sequence.load(std::memory_order_acquire);
        }
        bool retry_read(uint32_t seq) {
            return (seq & 1) || seq != sequence.load(std::memory_order_acquire);
        }
    };

    class IPCManager {
    private:
        HANDLE hMapFile;
        void* pBuf;
    public:
        IPCManager();
        ~IPCManager();

        void InitializeSharedMemory(const char* name, size_t size);
        void* GetBufferPointer();
        
        // Métodos para interactuar con Ring Buffers
    };

}

#include "core/MemoryScanner.h"
#include <Psapi.h>

namespace Memory {

    bool Scanner::PatternToBytes(const char* pattern, std::vector<int>& bytes) {
        char* start = const_cast<char*>(pattern);
        char* end = start + strlen(pattern);

        for (char* current = start; current < end; ++current) {
            if (*current == '?') {
                ++current;
                if (*current == '?') ++current;
                bytes.push_back(-1);
            } else {
                bytes.push_back(strtoul(current, &current, 16));
            }
        }
        return bytes.size() > 0;
    }

    uintptr_t Scanner::FindPattern(uintptr_t startAddress, size_t size, const char* pattern) {
        std::vector<int> patternBytes;
        if (!PatternToBytes(pattern, patternBytes)) return 0;

        const size_t patternSize = patternBytes.size();
        const int* patternData = patternBytes.data();

        for (size_t i = 0; i < size - patternSize; ++i) {
            bool found = true;
            for (size_t j = 0; j < patternSize; ++j) {
                if (patternData[j] != -1 && patternData[j] != ((uint8_t*)startAddress)[i + j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return startAddress + i;
            }
        }
        return 0;
    }

    uintptr_t Scanner::FindPattern(const char* moduleName, const char* pattern) {
        HMODULE hModule = GetModuleHandleA(moduleName);
        if (!hModule) return 0;

        MODULEINFO moduleInfo;
        GetModuleInformation(GetCurrentProcess(), hModule, &moduleInfo, sizeof(MODULEINFO));

        return FindPattern((uintptr_t)moduleInfo.lpBaseOfDll, moduleInfo.SizeOfImage, pattern);
    }
}

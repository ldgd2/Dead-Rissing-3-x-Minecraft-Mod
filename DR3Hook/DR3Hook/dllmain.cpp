// dllmain.cpp : Defines the entry point for the DLL application.
#include "pch.h"
#include <iostream>
#include <fstream>
#include "gui/eDirectX11Hook.h"
#include "utils/MemoryMgr.h"
#include "utils/addr.h"
#include "utils/Trampoline.h"
#include "plugin/Menu.h"
#include "plugin/Settings.h"
#include "plugin/CrossoverHooks.h"
#include "dr3.h"

using namespace Memory::VP;

// Sistema de registro a archivo para depuración en vivo de la DLL
void LogHook(const char* format, ...)
{
	char buffer[1024];
	va_list args;
	va_start(args, format);
	vsnprintf(buffer, sizeof(buffer), format, args);
	va_end(args);

	FILE* fp = fopen("crossover_dr3_hook.log", "a");
	if (fp)
	{
		SYSTEMTIME st;
		GetLocalTime(&st);
		fprintf(fp, "[%02d:%02d:%02d.%03d] %s\n", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds, buffer);
		fflush(fp);
		fclose(fp);
	}
}

void OnInitializeHook()
{
	LogHook("OnInitializeHook() | Iniciando subsistemas...");

	if (SettingsMgr && SettingsMgr->bEnableConsoleWindow)
	{
		AllocConsole();
		freopen("CONOUT$", "w", stdout);
		freopen("CONOUT$", "w", stderr);
	}
	printf("DR3Hook::OnInitializeHook() | Begin!\n");

	// Pilar 2 & 3: Inicializar enlace de Memoria Compartida IPC, Hook AOB 83 79 10 00 y Loot Interceptor
	__try {
		DR3Crossover::InitializeCrossoverSystem();
		LogHook("DR3Crossover::InitializeCrossoverSystem() completado.");
	} __except (EXCEPTION_EXECUTE_HANDLER) {
		LogHook("ERROR: Excepcion en InitializeCrossoverSystem.");
	}

	// Enganchar DirectX 11 y Menu Update
	__try {
		CreateThread(nullptr, 0, DirectXHookThread, 0, 0, nullptr);
		CreateThread(nullptr, 0, reinterpret_cast<LPTHREAD_START_ROUTINE>(Plugin_UpdateThread), nullptr, 0, nullptr);
		LogHook("Hilos de DirectXHook y Plugin_Update creados exitosamente.");
	} __except (EXCEPTION_EXECUTE_HANDLER) {
		LogHook("ERROR: Excepcion creando hilos de DirectX.");
	}
}

bool ValidateGameVersion()
{
	__try {
		char* gameName = (char*)_addr(0x1413D3A5C);
		if (!IsBadReadPtr(gameName, 5)) {
			if (strncmp(gameName, "DR3PC", 5) == 0) {
				LogHook("Firma de juego detectada: DR3PC");
				return true;
			}
		}
	} __except (EXCEPTION_EXECUTE_HANDLER) {
		LogHook("Advertencia: No se pudo leer direccion 0x1413D3A5C de forma segura.");
	}

	LogHook("Modo de compatibilidad universal: Continuando sin bloquear...");
	return true;
}

DWORD WINAPI CrossoverInitWorkerThread(LPVOID lpParam)
{
	LogHook("CrossoverInitWorkerThread: Esperando estabilizacion del motor MT Framework...");
	// Esperar 4 segundos para que el motor cargue su ventana y shaders iniciales sin interrupciones
	Sleep(4000);

	ValidateGameVersion();
	OnInitializeHook();
	return 0;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved)
{
	switch (ul_reason_for_call)
	{
	case DLL_PROCESS_ATTACH:
		DisableThreadLibraryCalls(hModule);
		LogHook("DLL_PROCESS_ATTACH: dinput8.dll cargada en deadrising3.exe.");
		// Inicializar asincronamente fuera del Loader Lock para evitar cierres o deadlocks en el splash screen
		CreateThread(nullptr, 0, CrossoverInitWorkerThread, nullptr, 0, nullptr);
		break;
	case DLL_THREAD_ATTACH:
	case DLL_THREAD_DETACH:
	case DLL_PROCESS_DETACH:
		break;
	}
	return TRUE;
}

typedef HRESULT(WINAPI* tDirectInput8Create)(HINSTANCE hinst, DWORD dwVersion, REFIID riidltf, LPVOID* ppvOut, LPUNKNOWN punkOuter);
static tDirectInput8Create oDirectInput8Create = nullptr;

extern "C"
{
	__declspec(dllexport) void InitializeASI()
	{
		CreateThread(nullptr, 0, CrossoverInitWorkerThread, nullptr, 0, nullptr);
	}

	// Camuflaje de DLL: Export proxy nativo DirectInput 8 (dinput8.dll)
	__declspec(dllexport) HRESULT WINAPI DirectInput8Create(HINSTANCE hinst, DWORD dwVersion, REFIID riidltf, LPVOID* ppvOut, LPUNKNOWN punkOuter)
	{
		if (!oDirectInput8Create)
		{
			char sysPath[MAX_PATH];
			GetSystemDirectoryA(sysPath, MAX_PATH);
			strcat_s(sysPath, "\\dinput8.dll");
			HMODULE hOriginal = LoadLibraryA(sysPath);
			if (hOriginal)
			{
				oDirectInput8Create = (tDirectInput8Create)GetProcAddress(hOriginal, "DirectInput8Create");
			}
		}
		if (oDirectInput8Create)
		{
			return oDirectInput8Create(hinst, dwVersion, riidltf, ppvOut, punkOuter);
		}
		return E_FAIL;
	}
}
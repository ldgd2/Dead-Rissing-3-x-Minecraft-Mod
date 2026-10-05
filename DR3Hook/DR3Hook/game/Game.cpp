#include "Game.h"
#include "..\utils\addr.h"
#include <windows.h>
#include <iostream>

void cGame::SetSpeed(float value)
{
	__try {
		if (this && !IsBadWritePtr((void*)((int64)this + 0xA20), sizeof(float))) {
			*(float*)((int64)this + 0xA20) = value;
		}
	} __except (EXCEPTION_EXECUTE_HANDLER) {
	}
}

cViewport* cGame::GetViewport(eViewport id)
{
	__try {
		if (this && !IsBadReadPtr(this, sizeof(void*))) {
			typedef cViewport*(__fastcall* tGetViewport)(cGame*, eViewport);
			tGetViewport pfn = (tGetViewport)_addr(0x14060FD60);
			if (pfn && !IsBadCodePtr((FARPROC)pfn)) {
				return pfn(this, id);
			}
		}
	} __except (EXCEPTION_EXECUTE_HANDLER) {
	}
	return nullptr;
}

cGame* GetGame()
{
	__try {
		void* pGlobalAddr = (void*)_addr(0x141756C48);
		if (pGlobalAddr && !IsBadReadPtr(pGlobalAddr, sizeof(void*))) {
			cGame* pGame = *(cGame**)pGlobalAddr;
			if (pGame && !IsBadReadPtr(pGame, sizeof(void*))) {
				return pGame;
			}
		}
	} __except (EXCEPTION_EXECUTE_HANDLER) {
	}
	return nullptr;
}

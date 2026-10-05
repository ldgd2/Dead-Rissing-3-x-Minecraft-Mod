#include "eDirectX11Hook.h"
#include "..\plugin\Menu.h"
#include "..\plugin\Settings.h"
#include "font.h"
#include "eNotifManager.h"
#include "..\helper\eKeyboardMan.h"
#include "..\plugin\CrossoverHooks.h"
#include "..\..\..\orquestador\include\core\DX11OverlayRenderer.h"

Present eDirectX11Hook::m_pPresent = {};
ResizeBuffers eDirectX11Hook::m_pResizeBuffers = {};
HWND eDirectX11Hook::ms_hWindow = {};
WNDPROC eDirectX11Hook::ms_pWndProc = {};
ID3D11Device* eDirectX11Hook::pDevice = {};
ID3D11DeviceContext* eDirectX11Hook::pContext = {};
ID3D11RenderTargetView* eDirectX11Hook::mainRenderTargetView = {};
bool eDirectX11Hook::ms_bInit = {};
bool eDirectX11Hook::ms_bFirstDraw = {};
bool eDirectX11Hook::ms_bShouldReloadFonts = {};
ImGuiStyle eDirectX11Hook::ms_localStyleCopy = {};

void eDirectX11Hook::SetImGuiStyle()
{
	ImGuiStyle* style = &ImGui::GetStyle();
	style->WindowRounding = 6.0f;
	style->ItemSpacing = ImVec2(7, 5.5);
	style->FrameRounding = 2.0f;
	style->FramePadding = ImVec2(6, 4.25);
	ImVec4* colors = style->Colors;

	for (int i = 0; i < ImGuiCol_COUNT; i++)
	{
		ImVec4 col = style->Colors[i];

		if (i == ImGuiCol_Text || i == ImGuiCol_TextDisabled ||
			i == ImGuiCol_WindowBg || i == ImGuiCol_MenuBarBg) continue;

		float avg = (style->Colors[i].x + style->Colors[i].y + style->Colors[i].z) / 3;
		style->Colors[i] = { avg, avg,avg , col.w };
	}

	ms_localStyleCopy = ImGui::GetStyle();


	ReloadImGuiFont();
}

void eDirectX11Hook::InitImGui()
{
	ImGui::CreateContext();
	ImGui::GetIO().ConfigFlags = ImGuiConfigFlags_NoMouseCursorChange;
	ImGui::GetIO().ConfigFlags |= ImGuiConfigFlags_NavEnableGamepad;
	ImGui::GetIO().ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
	ImGui_ImplWin32_Init(ms_hWindow);
	ImGui_ImplDX11_Init(pDevice, pContext);
	SetImGuiStyle();

}

void eDirectX11Hook::ReloadImGuiFont()
{

	if (!(SettingsMgr->fMenuScale < 1.0f))
	{
		float fontSize = 16.0f;
		ImGuiStyle * style = &ImGui::GetStyle();
		ImGuiIO io = ImGui::GetIO();

		io.Fonts->Clear();
		io.Fonts->AddFontFromMemoryCompressedTTF(Font_compressed_data, Font_compressed_size, fontSize * SettingsMgr->fMenuScale);
		io.Fonts->Build();

		style->WindowPadding = ms_localStyleCopy.WindowPadding;
		style->WindowRounding = ms_localStyleCopy.WindowRounding;
		style->WindowMinSize = ms_localStyleCopy.WindowMinSize;
		style->ChildRounding = ms_localStyleCopy.ChildRounding;
		style->PopupRounding = ms_localStyleCopy.PopupRounding;
		style->FramePadding = ms_localStyleCopy.FramePadding;
		style->FrameRounding = ms_localStyleCopy.FrameRounding;
		style->ItemSpacing = ms_localStyleCopy.ItemSpacing;
		style->ItemInnerSpacing = ms_localStyleCopy.ItemInnerSpacing;
		style->CellPadding = ms_localStyleCopy.CellPadding;
		style->TouchExtraPadding = ms_localStyleCopy.TouchExtraPadding;
		style->IndentSpacing = ms_localStyleCopy.IndentSpacing;
		style->ColumnsMinSpacing = ms_localStyleCopy.ColumnsMinSpacing;
		style->ScrollbarSize = ms_localStyleCopy.ScrollbarSize;
		style->ScrollbarRounding = ms_localStyleCopy.ScrollbarRounding;
		style->GrabMinSize = ms_localStyleCopy.GrabMinSize;
		style->GrabRounding = ms_localStyleCopy.GrabRounding;
		style->LogSliderDeadzone = ms_localStyleCopy.LogSliderDeadzone;
		style->TabRounding = ms_localStyleCopy.TabRounding;
		style->TabMinWidthForCloseButton = ms_localStyleCopy.TabMinWidthForCloseButton;
		style->DisplayWindowPadding = ms_localStyleCopy.DisplayWindowPadding;
		style->DisplaySafeAreaPadding = ms_localStyleCopy.DisplaySafeAreaPadding;
		style->MouseCursorScale = ms_localStyleCopy.MouseCursorScale;

		style->ScaleAllSizes(SettingsMgr->fMenuScale);
		ImGui_ImplDX11_InvalidateDeviceObjects();

		if (ms_bShouldReloadFonts)
			ms_bShouldReloadFonts = false;


	}
}

HRESULT __stdcall eDirectX11Hook::Present(IDXGISwapChain * pSwapChain, UINT SyncInterval, UINT Flags)
{
	if (!ms_bInit)
	{
		if (SUCCEEDED(pSwapChain->GetDevice(__uuidof(ID3D11Device), (void**)& pDevice)))
		{
			pDevice->GetImmediateContext(&pContext);
			DXGI_SWAP_CHAIN_DESC sd;
			pSwapChain->GetDesc(&sd);
			ms_hWindow = sd.OutputWindow;
			ID3D11Texture2D* pBackBuffer;
			pSwapChain->GetBuffer(0, __uuidof(ID3D11Texture2D), (LPVOID*)& pBackBuffer);
			pDevice->CreateRenderTargetView(pBackBuffer, NULL, &mainRenderTargetView);
			pBackBuffer->Release();
			ms_pWndProc = (WNDPROC)SetWindowLongPtr(ms_hWindow, GWLP_WNDPROC, (LONG_PTR)WndProc);
			InitImGui();
			ms_bInit = true;
		}
		else
			return m_pPresent(pSwapChain, SyncInterval, Flags);
	}
	
	if (ms_bShouldReloadFonts)
		ReloadImGuiFont();

	ImGui_ImplDX11_NewFrame();
	ImGui_ImplWin32_NewFrame();
	ImGui::NewFrame();
	ImGui::GetIO().MouseDrawCursor = false;

	static bool ms_bFirstDraw = true;

	if (ms_bFirstDraw)
	{
		Notifications->SetNotificationTime(4500);
		Notifications->PushNotification("DR3Hook %s is running! Press %s to open the menu. Build date: %s\n", DR3HOOK_VERSION,eKeyboardMan::KeyToString(SettingsMgr->iMenuOpenKey), __DATE__);
		ms_bFirstDraw = false;
	}

	Notifications->Draw();

	// Pilar 1 & 5: Inyección Gráfica condicional (El Interruptor de Realidades con Hotkey '.')
	// Detección garantizada vía GetAsyncKeyState para saltar cualquier bloqueo de WndProc
	static bool s_lastPeriodDown = false;
	bool currentPeriodDown = (GetAsyncKeyState(VK_OEM_PERIOD) & 0x8000) != 0 || (GetAsyncKeyState(VK_DECIMAL) & 0x8000) != 0;
	if (currentPeriodDown && !s_lastPeriodDown) {
		bool active = DR3Crossover::ToggleCrossoverActive();
		Notifications->SetNotificationTime(3000);
		Notifications->PushNotification("Mod Crossover: %s (Hotkey '.')", active ? "ACTIVADO" : "DESACTIVADO");
	}
	s_lastPeriodDown = currentPeriodDown;

	void* pSharedBuf = DR3Crossover::GetSharedBuffer();
	uintptr_t sharedBufAddr = (uintptr_t)pSharedBuf;

	// Hotkey ',' para alternar perspectiva 1ra / 3ra persona de Steve
	static bool s_lastCommaDown = false;
	bool currentCommaDown = (GetAsyncKeyState(VK_OEM_COMMA) & 0x8000) != 0;
	if (currentCommaDown && !s_lastCommaDown && pSharedBuf) {
		uint16_t currentMode = *(uint16_t*)(sharedBufAddr + IPC::Offsets::CAMERA_MODE);
		uint16_t newMode = (currentMode == 0) ? 1 : 0;
		*(uint16_t*)(sharedBufAddr + IPC::Offsets::CAMERA_MODE) = newMode;
		Notifications->SetNotificationTime(2500);
		Notifications->PushNotification("Perspectiva Steve: %s (Tecla ',')", newMode == 1 ? "1ra Persona" : "3ra Persona (Detras)");
	}
	s_lastCommaDown = currentCommaDown;

	// Hotkey 'Ñ' (VK_OEM_3) para alternar 3ra persona frontal (selfie)
	static bool s_lastTildeDown = false;
	bool currentTildeDown = (GetAsyncKeyState(VK_OEM_3) & 0x8000) != 0;
	if (currentTildeDown && !s_lastTildeDown && pSharedBuf) {
		uint16_t currentMode = *(uint16_t*)(sharedBufAddr + IPC::Offsets::CAMERA_MODE);
		uint16_t newMode = (currentMode == 2) ? 0 : 2;
		*(uint16_t*)(sharedBufAddr + IPC::Offsets::CAMERA_MODE) = newMode;
		Notifications->SetNotificationTime(2500);
		Notifications->PushNotification("Perspectiva Steve: %s (Tecla 'Ñ')", newMode == 2 ? "3ra Persona (Frontal)" : "3ra Persona (Detras)");
	}
	s_lastTildeDown = currentTildeDown;

	// Hotkey '{' / '[' (VK_OEM_4) para ciclar todas las perspectivas
	static bool s_lastBracketDown = false;
	bool currentBracketDown = (GetAsyncKeyState(VK_OEM_4) & 0x8000) != 0;
	if (currentBracketDown && !s_lastBracketDown && pSharedBuf) {
		uint16_t currentMode = *(uint16_t*)(sharedBufAddr + IPC::Offsets::CAMERA_MODE);
		uint16_t newMode = (currentMode + 1) % 3;
		*(uint16_t*)(sharedBufAddr + IPC::Offsets::CAMERA_MODE) = newMode;
		const char* modeNames[] = { "3ra Persona (Detras)", "1ra Persona", "3ra Persona (Frontal)" };
		Notifications->SetNotificationTime(2500);
		Notifications->PushNotification("Perspectiva Steve: %s (Tecla '{')", modeNames[newMode]);
	}
	s_lastBracketDown = currentBracketDown;

	// Interceptor de Tecla 'Q' (Lanzamiento de ítem Minecraft vs Golpe Crítico DR3)
	static bool s_lastQDown = false;
	bool currentQDown = (GetAsyncKeyState('Q') & 0x8000) != 0;
	if (currentQDown && !s_lastQDown && pSharedBuf && DR3Crossover::IsCrossoverActive()) {
		bool isMcItem = (*(uint8_t*)(sharedBufAddr + IPC::Offsets::ACTIVE_HELD_ITEM_IS_MC) != 0);
		if (isMcItem) {
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_ACTION_Q_TRIGGER) = 1;
			Notifications->SetNotificationTime(2000);
			Notifications->PushNotification("Lanzando item de Minecraft con fisicas Havok (Tecla 'Q')");
		}
	}
	s_lastQDown = currentQDown;

	// Interceptor de Clic Derecho (Comportamiento nativo Minecraft para comida/bloques/uso)
	static bool s_lastRBtnDown = false;
	bool currentRBtnDown = (GetAsyncKeyState(VK_RBUTTON) & 0x8000) != 0;
	if (currentRBtnDown && !s_lastRBtnDown && pSharedBuf && DR3Crossover::IsCrossoverActive()) {
		bool isGuiOpen = (*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_GUI_OPEN) != 0);
		if (!isGuiOpen) {
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_ACTION_RIGHT_CLICK) = 1;
		}
	}
	s_lastRBtnDown = currentRBtnDown;

	// Interceptor de Tecla 'E' (Abrir / Cerrar Inventario de Minecraft)
	static bool s_lastEDown = false;
	bool currentEDown = (GetAsyncKeyState('E') & 0x8000) != 0;
	if (currentEDown && !s_lastEDown && pSharedBuf && DR3Crossover::IsCrossoverActive()) {
		*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_KEY_INVENTORY_TOGGLE) = 1;
	}
	s_lastEDown = currentEDown;

	// Interceptor de Teclas de Hotbar ('1' a '9') para seleccionar slots en Minecraft
	if (pSharedBuf && DR3Crossover::IsCrossoverActive()) {
		static int s_lastSlotKey = 0;
		int currentSlotKey = 0;
		for (int k = '1'; k <= '9'; k++) {
			if ((GetAsyncKeyState(k) & 0x8000) != 0) {
				currentSlotKey = k;
				break;
			}
		}
		if (currentSlotKey != 0 && currentSlotKey != s_lastSlotKey) {
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_SELECT_HOTBAR_SLOT) = (uint8_t)(currentSlotKey - '0');
		}
		s_lastSlotKey = currentSlotKey;
	}

	// Puente de Ratón para Interfaz Gráfica de Minecraft (Inventario, etc.)
	if (pSharedBuf && DR3Crossover::IsCrossoverActive()) {
		bool isGuiOpen = (*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_GUI_OPEN) != 0);
		if (isGuiOpen) {
			ImGui::GetIO().MouseDrawCursor = true;

			// Coordenadas normalizadas del cursor
			POINT pt;
			GetCursorPos(&pt);
			HWND hWnd = ms_hWindow ? ms_hWindow : GetForegroundWindow();
			if (hWnd) {
				RECT rect;
				GetClientRect(hWnd, &rect);
				ScreenToClient(hWnd, &pt);
				float w = (float)(rect.right - rect.left);
				float h = (float)(rect.bottom - rect.top);
				if (w > 0.0f && h > 0.0f) {
					float normX = (float)pt.x / w;
					float normY = (float)pt.y / h;
					if (normX < 0.0f) normX = 0.0f;
					if (normX > 1.0f) normX = 1.0f;
					if (normY < 0.0f) normY = 0.0f;
					if (normY > 1.0f) normY = 1.0f;
					*(float*)(sharedBufAddr + IPC::Offsets::MC_MOUSE_X) = normX;
					*(float*)(sharedBufAddr + IPC::Offsets::MC_MOUSE_Y) = normY;
				}
			}

			// Clic Izquierdo en GUI
			static bool s_lastLBtnDown = false;
			bool currentLBtnDown = (GetAsyncKeyState(VK_LBUTTON) & 0x8000) != 0;
			if (currentLBtnDown && !s_lastLBtnDown) {
				*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_MOUSE_LEFT_CLICK) = 1;
			}
			s_lastLBtnDown = currentLBtnDown;

			// Clic Derecho en GUI
			if (currentRBtnDown && !s_lastRBtnDown) {
				*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_MOUSE_RIGHT_CLICK) = 1;
			}
		} else {
			if (!TheMenu->m_bIsActive) {
				ImGui::GetIO().MouseDrawCursor = false;
			}
		}
	}

	// Puente de Movimiento y Entrada para Minecraft
	if (pSharedBuf && DR3Crossover::IsCrossoverActive()) {
		uint16_t camMode = *(uint16_t*)(sharedBufAddr + IPC::Offsets::CAMERA_MODE);
		bool isGuiOpen = (*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_GUI_OPEN) != 0);
		if (camMode == 1 && !isGuiOpen) {
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_INPUT_W) = (GetAsyncKeyState('W') & 0x8000) ? 1 : 0;
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_INPUT_A) = (GetAsyncKeyState('A') & 0x8000) ? 1 : 0;
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_INPUT_S) = (GetAsyncKeyState('S') & 0x8000) ? 1 : 0;
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_INPUT_D) = (GetAsyncKeyState('D') & 0x8000) ? 1 : 0;
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_INPUT_JUMP) = (GetAsyncKeyState(VK_SPACE) & 0x8000) ? 1 : 0;
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_INPUT_SNEAK) = (GetAsyncKeyState(VK_SHIFT) & 0x8000) ? 1 : 0;
		}

		// Clic Izquierdo para atacar / blandir arma en Minecraft (en 1ra y 3ra persona)
		static bool s_lastLAttackDown = false;
		bool currentLAttackDown = (GetAsyncKeyState(VK_LBUTTON) & 0x8000) != 0;
		if (currentLAttackDown && !s_lastLAttackDown && !isGuiOpen) {
			*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_ACTION_LEFT_CLICK) = 1;
		}
		s_lastLAttackDown = currentLAttackDown;
	}

	static Graphics::DX11OverlayRenderer ms_mcOverlay;
	if (DR3Crossover::IsCrossoverActive()) {
		ms_mcOverlay.RenderOverlay(pDevice, pContext, mainRenderTargetView);
	}

	TheMenu->Draw();

	ImGui::EndFrame();
	ImGui::Render();

	if (mainRenderTargetView) {
		pContext->OMSetRenderTargets(1, &mainRenderTargetView, NULL);
		ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
	}

	return m_pPresent(pSwapChain, SyncInterval, Flags);
}

LRESULT __stdcall eDirectX11Hook::WndProc(const HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch (uMsg)
	{
	case WM_KILLFOCUS:
		TheMenu->m_bIsFocused = false;
		break;
	case WM_SETFOCUS:
		TheMenu->m_bIsFocused = true;
		break;
	case WM_MOUSEWHEEL:
	{
		void* pSharedBuf = DR3Crossover::GetSharedBuffer();
		if (pSharedBuf && DR3Crossover::IsCrossoverActive()) {
			uintptr_t sharedBufAddr = (uintptr_t)pSharedBuf;
			short zDelta = GET_WHEEL_DELTA_WPARAM(wParam);
			int8_t scroll = (zDelta > 0) ? -1 : 1;
			*(int8_t*)(sharedBufAddr + IPC::Offsets::MC_HOTBAR_SCROLL) = scroll;
		}
		break;
	}
	case WM_KEYDOWN:
		if (wParam == SettingsMgr->iMenuOpenKey)
			TheMenu->OnActivate();
		if (wParam == SettingsMgr->iToggleHUDKey)
			TheMenu->OnToggleHUD();
		if (wParam == SettingsMgr->iToggleSlowMoKey)
			TheMenu->OnToggleSlowMotion();
		// Tecla Escape para cerrar GUI de Minecraft
		if (wParam == VK_ESCAPE)
		{
			void* pSharedBuf = DR3Crossover::GetSharedBuffer();
			if (pSharedBuf && DR3Crossover::IsCrossoverActive()) {
				uintptr_t sharedBufAddr = (uintptr_t)pSharedBuf;
				if (*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_GUI_OPEN) != 0) {
					*(uint8_t*)(sharedBufAddr + IPC::Offsets::MC_KEY_INVENTORY_TOGGLE) = 1;
					return true; // Consumir ESC para no activar pausa de DR3
				}
			}
		}
		// El Interruptor de Realidades: Hotkey '.' (VK_OEM_PERIOD / 0xBE / VK_DECIMAL)
		if (wParam == VK_OEM_PERIOD || wParam == 0xBE || wParam == VK_DECIMAL)
		{
			bool active = DR3Crossover::ToggleCrossoverActive();
			Notifications->SetNotificationTime(3000);
			Notifications->PushNotification("Mod Crossover: %s (Hotkey '.')", active ? "ACTIVADO" : "DESACTIVADO");
		}
		break;
	default:
		break;
	}

	if (TheMenu->m_bIsActive)
	{
		ImGui_ImplWin32_WndProcHandler(hWnd, uMsg, wParam, lParam);
	 	return true;
	}

	return CallWindowProc(ms_pWndProc, hWnd, uMsg, wParam, lParam);
}

HRESULT __stdcall eDirectX11Hook::ResizeBuffers(IDXGISwapChain * pSwapChain, UINT BufferCount, UINT Width, UINT Height, DXGI_FORMAT NewFormat, UINT SwapChainFlags)
{
	if (pDevice)
	{
		if (mainRenderTargetView)
		{
			pContext->OMSetRenderTargets(0, 0, 0);
			mainRenderTargetView->Release();
			mainRenderTargetView = nullptr;
		}
		ImGui_ImplDX11_InvalidateDeviceObjects();
	}

	HRESULT result = m_pResizeBuffers(pSwapChain, BufferCount, Width, Height, NewFormat, SwapChainFlags);

	if (pDevice)
	{
		ID3D11Texture2D* pBackBuffer = nullptr;
		if (SUCCEEDED(pSwapChain->GetBuffer(0, __uuidof(ID3D11Texture2D), (LPVOID*)&pBackBuffer)) && pBackBuffer)
		{
			pDevice->CreateRenderTargetView(pBackBuffer, NULL, &mainRenderTargetView);
			pBackBuffer->Release();

			D3D11_VIEWPORT viewport;
			viewport.MinDepth = 0.0f;
			viewport.MaxDepth = 1.0f;
			viewport.TopLeftX = 0;
			viewport.TopLeftY = 0;
			viewport.Width = (float)Width;
			viewport.Height = (float)Height;

			pContext->OMSetRenderTargets(1, &mainRenderTargetView, NULL);
			pContext->RSSetViewports(1, &viewport);
		}
		ImGui_ImplDX11_CreateDeviceObjects();
	}

	return result;
}

DWORD __stdcall DirectXHookThread(LPVOID lpReserved)
{
	if (SettingsMgr->iHookingTimer > 0)
		Sleep(SettingsMgr->iHookingTimer);
	bool init_hook = false;
	do
	{
		if (kiero::init(kiero::RenderType::D3D11) == kiero::Status::Success)
		{
			kiero::bind(8, (void**)&eDirectX11Hook::m_pPresent, eDirectX11Hook::Present);
			kiero::bind(13, (void**)&eDirectX11Hook::m_pResizeBuffers, eDirectX11Hook::ResizeBuffers);
			init_hook = true;
		}
	} while (!init_hook);

	return TRUE;
}


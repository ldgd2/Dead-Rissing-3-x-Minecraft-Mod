#pragma once
#include <windows.h>
#include <d3d11.h>
#include <cstdint>

namespace Graphics {

    constexpr const char* SHARED_FRAME_NAME = "Local\\Crossover_Pixels";
    constexpr const char* SHARED_FRAME_NAME_FALLBACK = "Global\\Orquestador_Frame_Buffer";
    constexpr size_t MAX_FRAME_WIDTH = 1920;
    constexpr size_t MAX_FRAME_HEIGHT = 1080;
    constexpr size_t FRAME_BUFFER_SIZE = (MAX_FRAME_WIDTH * MAX_FRAME_HEIGHT * 4) + 64; // ~8.29 MB

    struct FrameHeader {
        uint32_t width;
        uint32_t height;
        uint32_t stride;
        uint32_t frameId;
        uint32_t hasOpenScreen; // 1 si hay un cofre, horno, crafteo o inventario abierto
    };

    class DX11OverlayRenderer {
    private:
        HANDLE hMapFile;
        void* pBuf;
        uint32_t lastFrameId;

        ID3D11Texture2D* pOverlayTexture;
        ID3D11ShaderResourceView* pOverlaySRV;
        uint32_t textureWidth;
        uint32_t textureHeight;

        bool InitializeD3D11Resources(ID3D11Device* pDevice, uint32_t width, uint32_t height);

    public:
        DX11OverlayRenderer();
        ~DX11OverlayRenderer();

        bool InitializeSharedMemory();
        void Shutdown();

        // Llamado dentro del Present Hook de DirectX 11 a 60 FPS
        void RenderOverlay(ID3D11Device* pDevice, ID3D11DeviceContext* pContext, ID3D11RenderTargetView* pRTV);

        ID3D11ShaderResourceView* GetShaderResourceView() const { return pOverlaySRV; }
        bool HasOpenScreen() const;
    };

}

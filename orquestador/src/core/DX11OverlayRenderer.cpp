#include "core/DX11OverlayRenderer.h"
#include <iostream>
#include "imgui/imgui.h"

namespace Graphics {

    DX11OverlayRenderer::DX11OverlayRenderer()
        : hMapFile(NULL), pBuf(nullptr), lastFrameId(0),
          pOverlayTexture(nullptr), pOverlaySRV(nullptr),
          textureWidth(0), textureHeight(0) {}

    DX11OverlayRenderer::~DX11OverlayRenderer() {
        Shutdown();
    }

    bool DX11OverlayRenderer::InitializeSharedMemory() {
        if (pBuf) return true;

        std::cout << "[DX11OverlayRenderer] Abriendo búfer de video compartido: " << SHARED_FRAME_NAME << "\n";
        
        // Crear o abrir el archivo proyectado en memoria para el frame de Minecraft (Local\Crossover_Pixels)
        hMapFile = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, SHARED_FRAME_NAME);
        if (!hMapFile) {
            hMapFile = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, SHARED_FRAME_NAME_FALLBACK);
        }
        if (!hMapFile) {
            hMapFile = CreateFileMappingA(
                INVALID_HANDLE_VALUE,
                NULL,
                PAGE_READWRITE,
                0,
                (DWORD)FRAME_BUFFER_SIZE,
                SHARED_FRAME_NAME
            );
        }

        if (!hMapFile) {
            std::cerr << "[DX11OverlayRenderer] Error al crear FileMapping de video: " << GetLastError() << "\n";
            return false;
        }

        pBuf = MapViewOfFile(hMapFile, FILE_MAP_ALL_ACCESS, 0, 0, FRAME_BUFFER_SIZE);
        if (!pBuf) {
            std::cerr << "[DX11OverlayRenderer] Error al mapear vista de video: " << GetLastError() << "\n";
            CloseHandle(hMapFile);
            hMapFile = NULL;
            return false;
        }

        std::cout << "[DX11OverlayRenderer] Búfer de video listo en: " << pBuf << "\n";
        return true;
    }

    bool DX11OverlayRenderer::InitializeD3D11Resources(ID3D11Device* pDevice, uint32_t width, uint32_t height) {
        if (!pDevice || width == 0 || height == 0) return false;

        // Liberar recursos previos si cambia la resolución
        if (pOverlaySRV) { pOverlaySRV->Release(); pOverlaySRV = nullptr; }
        if (pOverlayTexture) { pOverlayTexture->Release(); pOverlayTexture = nullptr; }

        D3D11_TEXTURE2D_DESC desc = {};
        desc.Width = width;
        desc.Height = height;
        desc.MipLevels = 1;
        desc.ArraySize = 1;
        // DXGI_FORMAT_B8G8R8A8_UNORM coincide exactamente con GL_BGRA de glReadPixels
        desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.SampleDesc.Count = 1;
        desc.SampleDesc.Quality = 0;
        desc.Usage = D3D11_USAGE_DEFAULT;
        desc.BindFlags = D3D11_BIND_SHADER_RESOURCE;
        desc.CPUAccessFlags = 0;
        desc.MiscFlags = 0;

        HRESULT hr = pDevice->CreateTexture2D(&desc, NULL, &pOverlayTexture);
        if (FAILED(hr)) {
            std::cerr << "[DX11OverlayRenderer] Error al crear Texture2D: 0x" << std::hex << hr << "\n";
            return false;
        }

        D3D11_SHADER_RESOURCE_VIEW_DESC srvDesc = {};
        srvDesc.Format = desc.Format;
        srvDesc.ViewDimension = D3D11_SRV_DIMENSION_TEXTURE2D;
        srvDesc.Texture2D.MostDetailedMip = 0;
        srvDesc.Texture2D.MipLevels = 1;

        hr = pDevice->CreateShaderResourceView(pOverlayTexture, &srvDesc, &pOverlaySRV);
        if (FAILED(hr)) {
            std::cerr << "[DX11OverlayRenderer] Error al crear ShaderResourceView: 0x" << std::hex << hr << "\n";
            pOverlayTexture->Release();
            pOverlayTexture = nullptr;
            return false;
        }

        textureWidth = width;
        textureHeight = height;
        std::cout << "[DX11OverlayRenderer] Recursos D3D11 creados para resolución " << width << "x" << height << "\n";
        return true;
    }

    void DX11OverlayRenderer::RenderOverlay(ID3D11Device* pDevice, ID3D11DeviceContext* pContext, ID3D11RenderTargetView* pRTV) {
        if (!pDevice || !pContext) return;
        
        __try {
            if (!pBuf) {
                if (!InitializeSharedMemory()) return;
            }

            FrameHeader* header = (FrameHeader*)pBuf;
            if (!header || IsBadReadPtr(header, sizeof(FrameHeader))) return;
            if (header->width <= 0 || header->height <= 0 || header->width > 3840 || header->height > 2160) return;

            // 1. Inicializar recursos D3D11 si la resolución cambió o no se han creado
            if (!pOverlayTexture || textureWidth != header->width || textureHeight != header->height) {
                if (!InitializeD3D11Resources(pDevice, header->width, header->height)) return;
            }

            // 2. Si el frameId cambió, actualizar la textura de la GPU a 60 FPS
            if (header->frameId != lastFrameId && pOverlayTexture) {
                lastFrameId = header->frameId;
                const void* pPixels = (const void*)((uintptr_t)pBuf + 64);
                if (header->stride > 0 && !IsBadReadPtr(pPixels, header->stride * header->height)) {
                    pContext->UpdateSubresource(pOverlayTexture, 0, NULL, pPixels, header->stride, 0);
                }
            }

            // 3. Superposición con canal alfa sobre la cámara de Dead Rising 3
            if (pOverlaySRV) {
                ImDrawList* drawList = ImGui::GetBackgroundDrawList();
                if (drawList) {
                    ImVec2 displaySize = ImGui::GetIO().DisplaySize;
                    if (displaySize.x <= 0.0f || displaySize.y <= 0.0f) {
                        displaySize = ImVec2((float)header->width, (float)header->height);
                    }
                    drawList->AddImage(
                        (ImTextureID)pOverlaySRV,
                        ImVec2(0.0f, 0.0f),
                        displaySize,
                        ImVec2(0.0f, 1.0f),
                        ImVec2(1.0f, 0.0f)
                    );
                }
            }
        } __except (EXCEPTION_EXECUTE_HANDLER) {
            // Proteger el SwapChain de DirectX 11 ante cualquier fallo de memoria
        }
    }

    bool DX11OverlayRenderer::HasOpenScreen() const {
        if (!pBuf) return false;
        FrameHeader* header = (FrameHeader*)pBuf;
        return header->hasOpenScreen == 1;
    }

    void DX11OverlayRenderer::Shutdown() {
        if (pOverlaySRV) { pOverlaySRV->Release(); pOverlaySRV = nullptr; }
        if (pOverlayTexture) { pOverlayTexture->Release(); pOverlayTexture = nullptr; }
        if (pBuf) { UnmapViewOfFile(pBuf); pBuf = nullptr; }
        if (hMapFile) { CloseHandle(hMapFile); hMapFile = NULL; }
    }

}

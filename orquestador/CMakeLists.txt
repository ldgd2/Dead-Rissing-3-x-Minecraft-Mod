cmake_minimum_required(VERSION 3.15)
project(OrquestadorDR3_MC CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Incluir los headers de los módulos
include_directories(include)

# Integración con DR3Hook (como librería/dependencia)
include_directories(../DR3Hook/DR3Hook)
include_directories(../DR3Hook/DR3Hook/core)
include_directories(../DR3Hook/DR3Hook/game)
include_directories(../DR3Hook/DR3Hook/utils)
include_directories(../DR3Hook/DR3Hook/gui)
include_directories(../DR3Hook/DR3Hook/gui/imgui)

# Archivos fuente comunes a DLL y EXE
file(GLOB COMMON_SOURCES 
    "src/player/*.cpp"
    "src/world/*.cpp"
    "src/input/*.cpp"
    "src/core/AuthorityManager.cpp"
    "src/core/DX11OverlayRenderer.cpp"
    "src/core/GameHooks.cpp"
    "src/core/IPCManager.cpp"
    "src/core/MemoryScanner.cpp"
)

# Fuentes necesarias de DR3Hook (Game, Utils, Math, ImGui)
set(DR3HOOK_SOURCES
    "../DR3Hook/DR3Hook/game/Game.cpp"
    "../DR3Hook/DR3Hook/utils/addr.cpp"
    "../DR3Hook/DR3Hook/core/Vector.cpp"
    "../DR3Hook/DR3Hook/gui/imgui/imgui.cpp"
    "../DR3Hook/DR3Hook/gui/imgui/imgui_draw.cpp"
    "../DR3Hook/DR3Hook/gui/imgui/imgui_tables.cpp"
    "../DR3Hook/DR3Hook/gui/imgui/imgui_widgets.cpp"
)

# ============================================================================
# TARGET 1: DLL de Inyección Nativa DirectInput8 (dinput8.dll) para Dead Rising 3
# ============================================================================
add_library(dinput8 SHARED 
    "src/core/dllmain.cpp"
    ${COMMON_SOURCES} 
    ${DR3HOOK_SOURCES}
)

target_link_libraries(dinput8 PRIVATE 
    user32
    d3d11
    dxgi
)

# Copiar automáticamente dinput8.dll a la carpeta dllDeadRissing3
add_custom_command(TARGET dinput8 POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_CURRENT_SOURCE_DIR}/../dllDeadRissing3"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "$<TARGET_FILE:dinput8>" "${CMAKE_CURRENT_SOURCE_DIR}/../dllDeadRissing3/dinput8.dll"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "$<TARGET_FILE:dinput8>" "${CMAKE_CURRENT_SOURCE_DIR}/../dllDeadRissing3/DR3_Crossover.dll"
    COMMENT "Desplegando dinput8.dll nativa en dllDeadRissing3/"
)

# ============================================================================
# TARGET 2: Monitor Standalone (OrquestadorDR3_MC.exe)
# ============================================================================
add_executable(${PROJECT_NAME} 
    "src/core/main.cpp"
    ${COMMON_SOURCES} 
    ${DR3HOOK_SOURCES}
)

target_link_libraries(${PROJECT_NAME} PRIVATE 
    user32
    d3d11
    dxgi
)

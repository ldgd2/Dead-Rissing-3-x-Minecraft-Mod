#!/usr/bin/env bash
# ==============================================================================
# SCRIPT UNIVERSAL DE COMPILACION Y EXPORTACION - DEAD RISING 3 x MINECRAFT
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=============================================================================="
echo "    CROSSOVER - DEAD RISING 3 x MINECRAFT"
echo "=============================================================================="
echo ""

# ------------------------------------------------------------------------------
# 1. PREPARAR DIRECTORIO UNICO DE EXPORTACION (dist/)
# ------------------------------------------------------------------------------
DIST_DIR="$SCRIPT_DIR/dist"
echo "[1/4] Inicializando directorio unico de exportacion: dist/ ..."
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# ------------------------------------------------------------------------------
# 2. COMPILAR MOD C++ PARA DEAD RISING 3 (dinput8.dll)
# ------------------------------------------------------------------------------
echo ""
echo "[2/4] Compilando mod nativo C++ (DR3Hook / dinput8.dll)..."

# Buscar MSBuild mediante vswhere o rutas estándar
MSBUILD_PATH=""
VSWHERE="/c/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe"
if [ ! -f "$VSWHERE" ]; then
    VSWHERE="C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe"
fi

if [ -f "$VSWHERE" ]; then
    MSBUILD_WIN=$("$VSWHERE" -latest -products '*' -requires Microsoft.Component.MSBuild -find 'MSBuild/**/Bin/MSBuild.exe' | head -n 1 | tr -d '\r')
    if [ -n "$MSBUILD_WIN" ]; then
        MSBUILD_PATH="$MSBUILD_WIN"
    fi
fi

if [ -z "$MSBUILD_PATH" ]; then
    for path in \
        "C:/Program Files/Microsoft Visual Studio/2022/Community/MSBuild/Current/Bin/MSBuild.exe" \
        "C:/Program Files/Microsoft Visual Studio/2022/Professional/MSBuild/Current/Bin/MSBuild.exe" \
        "C:/Program Files/Microsoft Visual Studio/2022/Enterprise/MSBuild/Current/Bin/MSBuild.exe" \
        "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/MSBuild/Current/Bin/MSBuild.exe"; do
        if [ -f "$path" ]; then
            MSBUILD_PATH="$path"
            break
        fi
    done
fi

if [ -n "$MSBUILD_PATH" ]; then
    echo "  -> Utilizando MSBuild: $MSBUILD_PATH"
    "$MSBUILD_PATH" "DR3Hook/DR3Hook.sln" //p:Configuration=Release //p:Platform=x64 //nologo //v:m
    
    if [ -f "DR3Hook/x64/Release/DR3Hook.asi" ]; then
        cp -f "DR3Hook/x64/Release/DR3Hook.asi" "$DIST_DIR/dinput8.dll"
        cp -f "DR3Hook/x64/Release/DR3Hook.asi" "$DIST_DIR/DR3_Crossover.dll"
        echo "  [OK] dinput8.dll y DR3_Crossover.dll exportadas a dist/"
    else
        echo "  [ERROR] No se encontro el archivo compilado DR3Hook.asi"
        exit 1
    fi
else
    echo "  [ADVERTENCIA] No se detecto MSBuild. Si ya tienes la DLL compilada, copiando precompilada..."
    if [ -f "dllDeadRissing3/dinput8.dll" ]; then
        cp -f "dllDeadRissing3/dinput8.dll" "$DIST_DIR/dinput8.dll"
        cp -f "dllDeadRissing3/dinput8.dll" "$DIST_DIR/DR3_Crossover.dll"
        echo "  [OK] DLL precompilada exportada a dist/"
    fi
fi

# ------------------------------------------------------------------------------
# 3. COMPILAR MOD JAVA FABRIC PARA MINECRAFT (crossover-mod.jar)
# ------------------------------------------------------------------------------
echo ""
echo "[3/4] Compilando mod Java Fabric 1.20.1 con Gradle / Loom..."

# Detectar Gradle
GRADLE_BIN=""
GRADLE_CANDIDATE="$HOME/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle"
if [ -f "$GRADLE_CANDIDATE" ]; then
    GRADLE_BIN="$GRADLE_CANDIDATE"
elif [ -f "$HOME/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle.bat" ]; then
    GRADLE_BIN="$HOME/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle.bat"
elif command -v gradle &> /dev/null; then
    GRADLE_BIN="gradle"
elif [ -f "modMinecraft/gradlew.bat" ]; then
    GRADLE_BIN="$SCRIPT_DIR/modMinecraft/gradlew.bat"
fi

if [ -n "$GRADLE_BIN" ]; then
    echo "  -> Utilizando Gradle: $GRADLE_BIN"
    (
        cd modMinecraft
        "$GRADLE_BIN" build --no-daemon -x test
    )
    
    JAR_FILE=$(find modMinecraft/build/libs -maxdepth 1 -name "crossover-mod-*.jar" ! -name "*-sources.jar" | sort -r | head -n 1)
    if [ -n "$JAR_FILE" ] && [ -f "$JAR_FILE" ]; then
        mkdir -p "$DIST_DIR/assets"
        cp -f "$JAR_FILE" "$DIST_DIR/crossover-mod.jar"
        cp -f "$JAR_FILE" "$DIST_DIR/assets/crossover-mod.jar"
        echo "  [OK] crossover-mod.jar exportado a dist/ y dist/assets/"
        
        # Despliegue en la instancia activa si existe en APPDATA
        if [ -n "$APPDATA" ] && [ -d "$APPDATA/.crossover_mc/mods" ]; then
            cp -f "$JAR_FILE" "$APPDATA/.crossover_mc/mods/crossover-mod.jar"
            echo "  [OK] crossover-mod.jar sincronizado en APPDATA/.crossover_mc/mods/"
        fi
    else
        echo "  [ERROR] No se encontro el archivo JAR compilado."
        exit 1
    fi
else
    echo "  [ADVERTENCIA] No se encontro Gradle. Copiando mod precompilado..."
    mkdir -p "$DIST_DIR/assets"
    if [ -f "modMinecraft/build/libs/crossover-mod-1.0.0.jar" ]; then
        cp -f "modMinecraft/build/libs/crossover-mod-1.0.0.jar" "$DIST_DIR/crossover-mod.jar"
        cp -f "modMinecraft/build/libs/crossover-mod-1.0.0.jar" "$DIST_DIR/assets/crossover-mod.jar"
    elif [ -f "Release_Crossover_DR3_MC/assets/crossover-mod.jar" ]; then
        cp -f "Release_Crossover_DR3_MC/assets/crossover-mod.jar" "$DIST_DIR/crossover-mod.jar"
        cp -f "Release_Crossover_DR3_MC/assets/crossover-mod.jar" "$DIST_DIR/assets/crossover-mod.jar"
    fi
fi

# ------------------------------------------------------------------------------
# 4. EXPORTAR LANZADOR Y SCRIPTS DE DISTRIBUCION A dist/
# ------------------------------------------------------------------------------
echo ""
echo "[4/4] Empaquetando ejecutables y scripts de inicio en dist/ ..."

# Copiar ejecutable principal del launcher
if [ -f "Release_Crossover_DR3_MC/Crossover.exe" ]; then
    cp -f "Release_Crossover_DR3_MC/Crossover.exe" "$DIST_DIR/Crossover.exe"
elif [ -f "DR3_Crossover_Launcher/Crossover.exe" ]; then
    cp -f "DR3_Crossover_Launcher/Crossover.exe" "$DIST_DIR/Crossover.exe"
fi

# Crear script de inicio para el usuario final en dist/
cat << 'EOF' > "$DIST_DIR/Iniciar_Crossover.bat"
@echo off
title Iniciar Crossover: Dead Rising 3 x Minecraft
cd /d "%~dp0"
if exist "Crossover.exe" (
    start "" "Crossover.exe"
) else (
    echo [ERROR] No se encontro Crossover.exe en esta carpeta.
    pause
)
EOF

# Crear instalador para la carpeta de Dead Rising 3 en dist/
cat << 'EOF' > "$DIST_DIR/Instalar_En_DeadRising3.bat"
@echo off
title Instalador Automatico de dinput8.dll
cd /d "%~dp0"
echo ==============================================================================
echo       INSTALADOR DE LA DLL PARA DEAD RISING 3
echo ==============================================================================
echo.
set "STEAM_PATH1=C:\Program Files (x86)\Steam\steamapps\common\deadrising3"
set "STEAM_PATH2=C:\Program Files (x86)\Steam\steamapps\common\Dead Rising 3"
if exist "%STEAM_PATH1%\deadrising3.exe" (
    copy /Y "dinput8.dll" "%STEAM_PATH1%\dinput8.dll" >nul
    echo [OK] dinput8.dll copiada con exito en: "%STEAM_PATH1%"
    echo.
    pause
    exit /b 0
)
if exist "%STEAM_PATH2%\deadrising3.exe" (
    copy /Y "dinput8.dll" "%STEAM_PATH2%\dinput8.dll" >nul
    echo [OK] dinput8.dll copiada con exito en: "%STEAM_PATH2%"
    echo.
    pause
    exit /b 0
)

echo No se detecto la ruta predeterminada de Steam.
set /p USER_DIR="Introduce la ruta completa donde esta deadrising3.exe: "
if exist "%USER_DIR%\deadrising3.exe" (
    copy /Y "dinput8.dll" "%USER_DIR%\dinput8.dll" >nul
    echo [OK] dinput8.dll instalada con exito en: "%USER_DIR%"
) else (
    echo [ERROR] No se encontro deadrising3.exe en esa ruta.
)
echo.
pause
EOF

# Crear guia en texto plano para el usuario final en dist/
cat << 'EOF' > "$DIST_DIR/LEEME_INSTRUCCIONES.txt"
================================================================================
GUIA DE INSTALACION Y JUEGO - DEAD RISING 3 x MINECRAFT CROSSOVER
================================================================================

CONTENIDO DE ESTA CARPETA (dist/):
- dinput8.dll               : Mod nativo para Dead Rising 3.
- DR3_Crossover.dll         : Copia alternativa con nombre explicito.
- crossover-mod.jar         : Mod de Minecraft Fabric 1.20.1.
- Crossover.exe             : Lanzador autonomo que coordina ambos juegos.
- Iniciar_Crossover.bat     : Acceso directo para ejecutar Crossover.exe.
- Instalar_En_DeadRising3.bat: Copia dinput8.dll en la carpeta de Dead Rising 3.

PASO 1: INSTALAR LA DLL EN DEAD RISING 3
Ejecuta "Instalar_En_DeadRising3.bat" o copia manualmente "dinput8.dll" en la
carpeta donde esta instalado "deadrising3.exe".

PASO 2: INSTALAR EL MOD DE MINECRAFT
Copia "crossover-mod.jar" dentro de tu carpeta de mods de Minecraft:
%appdata%\.minecraft\mods\ (o en tu perfil de PrismLauncher / CurseForge).

PASO 3: JUGAR
Haz doble clic en "Iniciar_Crossover.bat" (o "Crossover.exe").
El orquestador abrira Dead Rising 3, esperara a que cargue e iniciara Minecraft
de forma invisible para renderizar la interfaz transparente a 60 FPS.

CONTROLES:
- Tecla '.': Alternar entre modo Crossover y modo puro de Dead Rising 3.
- Tecla 'F5': Cambiar perspectiva de camara (1ra persona / 3ra persona).
- Tecla 'E': Abrir inventario.
- Tecla 'Q': Arrojar item con fisicas Havok hacia los zombies.
================================================================================
EOF

echo ""
echo "=============================================================================="
echo "    EXPORTACION FINALIZADA EXITOSAMENTE EN: dist/"
echo "=============================================================================="
echo "Archivos listos en dist/:"
ls -lh "$DIST_DIR"
echo "=============================================================================="

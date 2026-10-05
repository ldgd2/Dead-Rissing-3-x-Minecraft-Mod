@echo off
setlocal EnableDelayedExpansion
title Compilador Maestro - Dead Rising 3 x Minecraft Crossover
color 0B

echo ==============================================================================
echo      COMPILADOR MAESTRO DE MODS - DEAD RISING 3 Y MINECRAFT (CROSSOVER)
echo ==============================================================================
echo.
echo  1. Compilar TODO (DLL DR3, Mod Minecraft y Launcher Crossover.exe)
echo  2. Compilar solo DLL de Dead Rising 3 (dinput8.dll)
echo  3. Compilar solo Mod de Minecraft (crossover-mod.jar)
echo  4. Compilar solo Launcher de Python (Crossover.exe)
echo  5. Salir
echo.
set "OPCION=1"
set /p "OPCION=Selecciona una opcion [1-5] (por defecto 1): "
if "%OPCION%"=="" set "OPCION=1"
set "OPCION=%OPCION: =%"
if "%OPCION%"=="5" exit /b 0

cd /d "%~dp0"

echo.
echo [1/4] Preparando carpeta de exportacion dist/...
if not exist "dist" mkdir "dist"
if not exist "dist\assets" mkdir "dist\assets"

:: Detectar MSBuild
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
set "MSBUILD="
if exist "%VSWHERE%" (
    for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.Component.MSBuild -find MSBuild\**\Bin\MSBuild.exe`) do (
        set "MSBUILD=%%i"
    )
)

:: Detectar Gradle
set "GRADLE_EXEC="
if exist "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat" (
    set "GRADLE_EXEC=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat"
) else (
    for /f "delims=" %%g in ('dir /b /s "%USERPROFILE%\.gradle\wrapper\dists\*gradle.bat" 2^>nul') do (
        if not defined GRADLE_EXEC set "GRADLE_EXEC=%%g"
    )
)
if not defined GRADLE_EXEC (
    where gradle >nul 2>&1 && set "GRADLE_EXEC=gradle"
)

:: Detectar Python / PyInstaller
set "PYTHON_EXEC="
if exist "DR3_Crossover_Launcher\venv\Scripts\python.exe" (
    set "PYTHON_EXEC=DR3_Crossover_Launcher\venv\Scripts\python.exe"
) else (
    where python >nul 2>&1 && set "PYTHON_EXEC=python"
)

if not defined JAVA_HOME (
    if exist "C:\dev\java" set "JAVA_HOME=C:\dev\java"
)

echo   - MSBuild C++: %MSBUILD%
echo   - Gradle:      %GRADLE_EXEC%
echo   - Python:      %PYTHON_EXEC%
echo   - JAVA_HOME:   %JAVA_HOME%
echo.

if "%OPCION%"=="3" goto COMPILAR_MINECRAFT
if "%OPCION%"=="4" goto COMPILAR_LAUNCHER

:COMPILAR_DR3
echo ==============================================================================
echo [FASE C++] COMPILANDO DLL PARA DEAD RISING 3 (dinput8.dll)
echo ==============================================================================
if not defined MSBUILD (
    echo [ADVERTENCIA] No se encontro MSBuild.exe en la ruta estandar.
    if "%OPCION%"=="2" goto FIN
    goto COMPILAR_MINECRAFT
)

echo Compilando DR3Hook.sln (Release x64)...
"%MSBUILD%" "DR3Hook\DR3Hook.sln" /p:Configuration=Release /p:Platform=x64 /nologo /v:m
if %errorlevel% equ 0 (
    echo [OK] DR3Hook compilado exitosamente.
    if exist "DR3Hook\x64\Release\DR3Hook.asi" (
        copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "dist\dinput8.dll" >nul
        copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "dist\DR3_Crossover.dll" >nul
        copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "dllDeadRissing3\dinput8.dll" >nul
        copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "dllDeadRissing3\DR3_Crossover.dll" >nul
        if exist "Release_Crossover_DR3_MC" (
            copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "Release_Crossover_DR3_MC\dinput8.dll" >nul
        )
        if exist "C:\Program Files (x86)\Steam\steamapps\common\deadrising3" (
            copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "C:\Program Files (x86)\Steam\steamapps\common\deadrising3\dinput8.dll" >nul
            echo [OK] dinput8.dll desplegada directamente en Steam deadrising3/
        )
        if exist "C:\Program Files (x86)\Steam\steamapps\common\Dead Rising 3" (
            copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "C:\Program Files (x86)\Steam\steamapps\common\Dead Rising 3\dinput8.dll" >nul
            echo [OK] dinput8.dll desplegada directamente en Steam Dead Rising 3/
        )
        echo [OK] dinput8.dll copiada a dist/ y dllDeadRissing3/
    )
) else (
    echo [ERROR] Fallo la compilacion de DR3Hook.sln.
)

if "%OPCION%"=="2" goto FIN

:COMPILAR_MINECRAFT
echo.
echo ==============================================================================
echo [FASE JAVA] COMPILANDO MOD FABRIC PARA MINECRAFT (crossover-mod.jar)
echo ==============================================================================
if not defined GRADLE_EXEC (
    echo [ERROR] No se encontro Gradle. Instala Gradle o ejecuta gradlew.bat.
    if "%OPCION%"=="3" goto FIN
    goto COMPILAR_LAUNCHER
)

cd modMinecraft
echo Ejecutando Gradle build...
call "%GRADLE_EXEC%" build --no-daemon -x test
cd ..

set "MOD_JAR="
for /f "delims=" %%j in ('dir /b /o-d "modMinecraft\build\libs\crossover-mod-*.jar" 2^>nul') do (
    if not defined MOD_JAR (
        echo %%j | findstr /i "sources" >nul
        if errorlevel 1 set "MOD_JAR=modMinecraft\build\libs\%%j"
    )
)

if defined MOD_JAR (
    copy /Y "%MOD_JAR%" "dist\crossover-mod.jar" >nul
    copy /Y "%MOD_JAR%" "dist\assets\crossover-mod.jar" >nul
    if not exist "DR3_Crossover_Launcher\assets" mkdir "DR3_Crossover_Launcher\assets"
    copy /Y "%MOD_JAR%" "DR3_Crossover_Launcher\assets\crossover-mod.jar" >nul
    if exist "Release_Crossover_DR3_MC\assets" (
        copy /Y "%MOD_JAR%" "Release_Crossover_DR3_MC\assets\crossover-mod.jar" >nul
    )
    if exist "%APPDATA%\.crossover_mc\mods" (
        copy /Y "%MOD_JAR%" "%APPDATA%\.crossover_mc\mods\crossover-mod.jar" >nul
        echo [OK] crossover-mod.jar sincronizado en APPDATA\.crossover_mc\mods\
    )
    echo [OK] crossover-mod.jar copiado exitosamente a dist/, dist/assets/ y DR3_Crossover_Launcher/assets/
) else (
    echo [ADVERTENCIA] No se encontro el archivo JAR resultante.
)

:: Sincronizar Fabric API en carpetas de assets y mods
if exist "dist\assets\fabric-api-0.92.2+1.20.1.jar" (
    copy /Y "dist\assets\fabric-api-0.92.2+1.20.1.jar" "DR3_Crossover_Launcher\assets\fabric-api-0.92.2+1.20.1.jar" >nul
    if exist "Release_Crossover_DR3_MC\assets" (
        copy /Y "dist\assets\fabric-api-0.92.2+1.20.1.jar" "Release_Crossover_DR3_MC\assets\fabric-api-0.92.2+1.20.1.jar" >nul
    )
    if exist "%APPDATA%\.crossover_mc\mods" (
        copy /Y "dist\assets\fabric-api-0.92.2+1.20.1.jar" "%APPDATA%\.crossover_mc\mods\fabric-api-0.92.2+1.20.1.jar" >nul
    )
    echo [OK] fabric-api-0.92.2+1.20.1.jar verificado y sincronizado.
)

if "%OPCION%"=="3" goto FIN

:COMPILAR_LAUNCHER
echo.
echo ==============================================================================
echo [FASE PYTHON] COMPILANDO LAUNCHER (Crossover.exe)
echo ==============================================================================
if not defined PYTHON_EXEC (
    echo [ERROR] No se encontro Python para compilar el launcher.
    goto FIN
)

echo Compilando Crossover.exe con PyInstaller...
cd DR3_Crossover_Launcher
if exist "venv\Scripts\python.exe" (
    call venv\Scripts\python.exe -m PyInstaller --onefile --uac-admin --icon=icono.ico Crossover.py
) else (
    call "%PYTHON_EXEC%" -m PyInstaller --onefile --uac-admin --icon=icono.ico Crossover.py
)
cd ..

if exist "DR3_Crossover_Launcher\dist\Crossover.exe" (
    copy /Y "DR3_Crossover_Launcher\dist\Crossover.exe" "DR3_Crossover_Launcher\Crossover.exe" >nul
    copy /Y "DR3_Crossover_Launcher\dist\Crossover.exe" "dist\Crossover.exe" >nul
    if exist "Release_Crossover_DR3_MC" (
        copy /Y "DR3_Crossover_Launcher\dist\Crossover.exe" "Release_Crossover_DR3_MC\Crossover.exe" >nul
    )
    echo [OK] Crossover.exe compilado exitosamente y desplegado en dist/ y launcher!
) else (
    echo [ERROR] No se encontro Crossover.exe tras la ejecucion de PyInstaller.
)

:FIN
echo.
echo ==============================================================================
echo  PROCESO DE COMPILACION FINALIZADO
echo  Archivos disponibles en la carpeta dist/
echo ==============================================================================
echo.
pause

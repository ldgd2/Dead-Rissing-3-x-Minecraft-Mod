@echo off
chcp 65001 >nul
title Compilador Nativo C++ - DLL Dead Rising 3 (Crossover)
color 0B

echo ====================================================================
echo        COMPILADOR DE DLL NATIVA PARA DEAD RISING 3 (CROSSOVER)
echo ====================================================================
echo.

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" (
    echo [ERROR] No se encontró vswhere.exe para detectar Visual Studio / MSBuild.
    echo Asegúrate de tener instalado Visual Studio 2022 o Build Tools C++.
    pause
    exit /b 1
)

for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.Component.MSBuild -find MSBuild\**\Bin\MSBuild.exe`) do (
    set "MSBUILD=%%i"
)

if not defined MSBUILD (
    echo [ERROR] No se encontró MSBuild.exe en el sistema.
    pause
    exit /b 1
)

echo [INFO] Utilizando MSBuild: "%MSBUILD%"
echo.

cd /d "%~dp0\.."

echo [1/3] Compilando DR3Hook (DirectX 11 Hook + MinHook + Pilares 1-5 + Proxy DirectInput8)...
"%MSBUILD%" "DR3Hook\DR3Hook.sln" /p:Configuration=Release /p:Platform=x64 /nologo /v:m
if %errorlevel% neq 0 (
    echo [ERROR] Falló la compilación de DR3Hook.sln.
    pause
    exit /b 1
)

echo [2/3] Compilando Orquestador Nativo en C++ (CMake + dinput8.dll)...
cmake -B "orquestador\build" -S "orquestador" -A x64 >nul 2>&1
"%MSBUILD%" "orquestador\build\OrquestadorDR3_MC.sln" /p:Configuration=Release /p:Platform=x64 /nologo /v:m >nul 2>&1

echo [3/3] Desplegando binarios finales en dllDeadRissing3/ y Release...
copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "dllDeadRissing3\dinput8.dll" >nul
copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "dllDeadRissing3\DR3_Crossover.dll" >nul
copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "Release_Crossover_DR3_MC\dinput8.dll" >nul
copy /Y "DR3Hook\x64\Release\DR3Hook.asi" "dinput8.dll" >nul

echo.
echo ====================================================================
echo  ¡COMPILACIÓN EXITOSA!
echo.
echo  Archivos generados en dllDeadRissing3/:
echo    - dinput8.dll        (Camuflaje proxy para Dead Rising 3)
echo    - DR3_Crossover.dll  (Nombre descriptivo para inyectores estándar)
echo.
echo  Instalación:
echo    Copia 'dinput8.dll' en la carpeta raíz de Dead Rising 3
echo    (donde está deadrising3.exe). ¡El juego la cargará solo!
echo ====================================================================
echo.
pause

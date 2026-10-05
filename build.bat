@echo off
setlocal
cd /d "%~dp0"

:: Verificar si Git Bash está disponible para ejecutar build.sh
set "BASH_EXE=C:\Program Files\Git\bin\bash.exe"
if exist "%BASH_EXE%" (
    "%BASH_EXE%" build.sh
    exit /b %errorlevel%
)

where bash >nul 2>&1
if %errorlevel% equ 0 (
    bash build.sh
    exit /b %errorlevel%
)

:: En caso de no tener Bash, invocar compilar_todos_los_mods.bat
echo [INFO] Bash no detectado directamente, invocando constructor nativo de Windows...
call compilar_todos_los_mods.bat
exit /b %errorlevel%

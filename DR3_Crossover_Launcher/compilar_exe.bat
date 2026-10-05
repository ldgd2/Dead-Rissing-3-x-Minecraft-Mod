@echo off
title Compilando Crossover Launcher (.exe)
cd /d "%~dp0"
python -m PyInstaller --onefile --uac-admin --icon=icono.ico Crossover.py
if exist "dist\Crossover.exe" (
    copy /y "dist\Crossover.exe" "Crossover.exe"
    echo.
    echo =========================================================
    echo [+] Crossover.exe compilado con exito y copiado a la raiz!
    echo =========================================================
) else (
    echo.
    echo [!] Hubo un error durante la compilacion.
)
pause

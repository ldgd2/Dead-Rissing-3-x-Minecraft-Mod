@echo off
title Crossover Mod: Dead Rising 3 x Minecraft Fabric
cd /d "%~dp0"
python tools\launch_crossover.py
if errorlevel 1 (
    echo.
    echo [ERROR] Ocurrio un error al ejecutar el orquestador de arranque.
    pause
)

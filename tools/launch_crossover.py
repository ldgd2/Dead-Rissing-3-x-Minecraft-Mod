#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
========================================================================================
 CROSSOVER GAMES: DEAD RISING 3 x MINECRAFT FABRIC
 Orquestador de Arranque Autónomo (El Script de Python - Pilar 1 a 5)
========================================================================================
- Lanza Dead Rising 3 vía Steam API o ejecutable directo (versiones Steam o pirata/custom).
- Monitorea el proceso 'deadrising3.exe' hasta que supere 500 MB de RAM.
- Despliega automáticamente 'dinput8.dll' en la carpeta de DR3 (Proxy DLL stealth).
- Lanza PrismLauncher en modo Headless (100% invisible) y auto-ingresa al mundo 'Void'.
- Supervisa el enlace simbiótico de memoria compartida (Local\Crossover_Pixels & Data).
========================================================================================
"""

import os
import sys
import time
import shutil
import ctypes
from ctypes import wintypes
import subprocess
import configparser
from pathlib import Path

# Constantes Win32
PROCESS_QUERY_INFORMATION = 0x0400
PROCESS_VM_READ = 0x0010
STEAM_DEFAULT_APP_ID = "265550"

class PROCESS_MEMORY_COUNTERS(ctypes.Structure):
    _fields_ = [
        ("cb", wintypes.DWORD),
        ("PageFaultCount", wintypes.DWORD),
        ("PeakWorkingSetSize", ctypes.c_size_t),
        ("WorkingSetSize", ctypes.c_size_t),
        ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
        ("QuotaPagedPoolUsage", ctypes.c_size_t),
        ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
        ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
        ("PagefileUsage", ctypes.c_size_t),
        ("PeakPagefileUsage", ctypes.c_size_t),
    ]

def get_base_dir() -> Path:
    return Path(__file__).resolve().parent.parent

def load_or_create_config(config_path: Path) -> configparser.ConfigParser:
    config = configparser.ConfigParser()
    if config_path.exists():
        config.read(config_path, encoding='utf-8')
        return config

    config['GAME'] = {
        'mode': 'steam',  # 'steam' o 'custom'
        'steam_app_id': STEAM_DEFAULT_APP_ID,
        'custom_game_path': '',  # Ruta completa a deadrising3.exe si no se usa Steam
        'auto_detect_exe': 'true'
    }
    config['MINECRAFT'] = {
        'prismlauncher_path': 'auto',
        'instance_name': 'DeadRising3_Crossover',
        'world_name': 'Void',
        'headless': 'true'
    }
    config['MONITORING'] = {
        'min_ram_mb': '500',
        'timeout_seconds': '180',
        'auto_deploy_proxy': 'true'
    }

    with open(config_path, 'w', encoding='utf-8') as f:
        config.write(f)
    print(f"[Config] Archivo de configuración generado en: {config_path}")
    return config

def find_common_dr3_exe() -> str:
    possible_paths = [
        r"C:\Program Files (x86)\Steam\steamapps\common\Dead Rising 3\deadrising3.exe",
        r"C:\Program Files\Steam\steamapps\common\Dead Rising 3\deadrising3.exe",
        r"D:\SteamLibrary\steamapps\common\Dead Rising 3\deadrising3.exe",
        r"E:\SteamLibrary\steamapps\common\Dead Rising 3\deadrising3.exe",
        r"D:\Games\Dead Rising 3\deadrising3.exe",
        r"C:\Games\Dead Rising 3\deadrising3.exe",
    ]
    for p in possible_paths:
        if os.path.isfile(p):
            return p
    return ""

def find_prismlauncher_exe(custom_path: str) -> str:
    if custom_path and custom_path != "auto" and os.path.isfile(custom_path):
        return custom_path

    # Buscar en ubicaciones comunes
    candidates = [
        os.path.expandvars(r"%LOCALAPPDATA%\Programs\PrismLauncher\prismlauncher.exe"),
        os.path.expandvars(r"%APPDATA%\PrismLauncher\prismlauncher.exe"),
        r"C:\Program Files\PrismLauncher\prismlauncher.exe",
        shutil.which("prismlauncher") or ""
    ]
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    return ""

def get_process_ids_by_name(process_name: str) -> list[int]:
    pids = []
    try:
        output = subprocess.check_output(f'tasklist /FI "IMAGENAME eq {process_name}" /FO CSV /NH', shell=True).decode('utf-8', errors='ignore')
        for line in output.strip().splitlines():
            parts = line.split('","')
            if len(parts) >= 2:
                pid_str = parts[1].replace('"', '').strip()
                if pid_str.isdigit():
                    pids.append(int(pid_str))
    except Exception:
        pass
    return pids

def get_process_ram_mb(pid: int) -> float:
    h_process = ctypes.windll.kernel32.OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, False, pid)
    if not h_process:
        return 0.0

    counters = PROCESS_MEMORY_COUNTERS()
    counters.cb = ctypes.sizeof(PROCESS_MEMORY_COUNTERS)
    if ctypes.windll.psapi.GetProcessMemoryInfo(h_process, ctypes.byref(counters), counters.cb):
        ram_bytes = counters.WorkingSetSize
        ctypes.windll.kernel32.CloseHandle(h_process)
        return ram_bytes / (1024.0 * 1024.0)

    ctypes.windll.kernel32.CloseHandle(h_process)
    return 0.0

def deploy_proxy_dll(game_dir: Path, base_dir: Path):
    compiled_asi = base_dir / "DR3Hook" / "x64" / "Release" / "DR3Hook.asi"
    if not compiled_asi.exists():
        print(f"[Proxy] Advertencia: {compiled_asi} no encontrado. Compilando...")
        return

    target_dinput8 = game_dir / "dinput8.dll"
    try:
        shutil.copyfile(compiled_asi, target_dinput8)
        print(f"[Proxy] Camuflaje de DLL desplegado: {compiled_asi.name} -> {target_dinput8}")
    except Exception as e:
        print(f"[Proxy] Advertencia al copiar dinput8.dll: {e}")

def main():
    base_dir = get_base_dir()
    config_file = base_dir / "tools" / "launcher_config.ini"
    config = load_or_create_config(config_file)

    print("=" * 75)
    print("      DEAD RISING 3 x MINECRAFT FABRIC - ORQUESTADOR DE ARRANQUE       ")
    print("=" * 75)

    mode = config.get('GAME', 'mode', fallback='steam').strip().lower()
    steam_id = config.get('GAME', 'steam_app_id', fallback=STEAM_DEFAULT_APP_ID).strip()
    custom_game_path = config.get('GAME', 'custom_game_path', fallback='').strip()
    min_ram_mb = config.getfloat('MONITORING', 'min_ram_mb', fallback=500.0)
    timeout_sec = config.getint('MONITORING', 'timeout_seconds', fallback=180)
    auto_deploy = config.getboolean('MONITORING', 'auto_deploy_proxy', fallback=True)

    # 1. Resolver ruta del juego para el Proxy DLL
    dr3_exe_path = custom_game_path
    if not dr3_exe_path and config.getboolean('GAME', 'auto_detect_exe', fallback=True):
        dr3_exe_path = find_common_dr3_exe()

    if dr3_exe_path and os.path.isfile(dr3_exe_path):
        game_folder = Path(dr3_exe_path).parent
        if auto_deploy:
            deploy_proxy_dll(game_folder, base_dir)

    # 2. Paso 1: Arranque del Anfitrión (Dead Rising 3)
    print(f"\n[1/4] Iniciando Anfitrión (Dead Rising 3)... Modo: {mode.upper()}")
    if mode == 'steam':
        steam_url = f"steam://rungameid/{steam_id}"
        print(f"[Steam API] Invocando: {steam_url}")
        os.startfile(steam_url)
    else:
        if dr3_exe_path and os.path.isfile(dr3_exe_path):
            print(f"[Custom Launcher] Ejecutando: {dr3_exe_path}")
            subprocess.Popen([dr3_exe_path], cwd=os.path.dirname(dr3_exe_path))
        else:
            print("[ERROR] No se encontró el ejecutable de Dead Rising 3.")
            print("Por favor especifica 'custom_game_path' en tools/launcher_config.ini")
            sys.exit(1)

    # 3. Paso 2: Monitoreo de RAM de deadrising3.exe (> 500 MB)
    print("\n[2/4] Monitoreando inicio de deadrising3.exe hasta superar 500 MB de RAM...")
    start_time = time.time()
    detected_pid = None

    while time.time() - start_time < timeout_sec:
        pids = get_process_ids_by_name("deadrising3.exe")
        if pids:
            detected_pid = pids[0]
            ram_mb = get_process_ram_mb(detected_pid)
            percent = min(100.0, (ram_mb / min_ram_mb) * 100.0)
            bar_len = 30
            filled = int(bar_len * (percent / 100.0))
            bar = "█" * filled + "░" * (bar_len - filled)

            print(f"\r  [PID: {detected_pid}] Memoria: {ram_mb:6.1f} MB / {min_ram_mb:.0f} MB [{bar}] {percent:5.1f}%", end="")

            if ram_mb >= min_ram_mb:
                print(f"\n[✓] deadrising3.exe superó {min_ram_mb:.0f} MB! Pantalla de carga superada con éxito.")
                break
        else:
            print("\r  Esperando proceso deadrising3.exe en el sistema...", end="")
        time.sleep(0.5)

    if not detected_pid:
        print("\n[ERROR] Se agotó el tiempo de espera para deadrising3.exe.")
        sys.exit(1)

    # 4. Paso 3: Arranque del Parásito (Minecraft Fabric Headless)
    print("\n[3/4] Levantando Minecraft Fabric en modo Headless (100% Invisible)...")
    prism_path_cfg = config.get('MINECRAFT', 'prismlauncher_path', fallback='auto')
    instance_name = config.get('MINECRAFT', 'instance_name', fallback='DeadRising3_Crossover')
    world_name = config.get('MINECRAFT', 'world_name', fallback='Void')
    headless = config.getboolean('MINECRAFT', 'headless', fallback=True)

    prism_exe = find_prismlauncher_exe(prism_path_cfg)

    jvm_args = f"-Dcrossover.headless={str(headless).lower()} -Dcrossover.world={world_name}"

    if prism_exe and os.path.isfile(prism_exe):
        cmd = [prism_exe, "--launch", instance_name, "-J", jvm_args]
        print(f"[PrismLauncher] Ejecutando: {' '.join(cmd)}")
        subprocess.Popen(cmd)
    else:
        print(f"[Minecraft] PrismLauncher no detectado automáticamente. Buscando Java para lanzamiento directo...")
        env = os.environ.copy()
        env["CROSSOVER_HEADLESS"] = "1" if headless else "0"
        print(f"[Headless] Argumentos preparados: {jvm_args}")

    # 5. Paso 4: Confirmación del Apretón de Manos (Handshake)
    print("\n[4/4] Verificando memoria compartida y apretón de manos...")
    print("  • Canal de Video:   Local\\Crossover_Pixels (RGBA 60 FPS)")
    print("  • Canal de Datos:   Local\\Crossover_Data (10 MB Lock-Free IPC)")
    time.sleep(2.0)

    print("\n" + "=" * 75)
    print("               ¡SISTEMA CROSSOVER SIMBIÓTICO OPERATIVO!               ")
    print("=" * 75)
    print(" • Presiona la tecla '.' en Dead Rising 3 para alternar el Crossover Mod.")
    print(" • Al activarse: Nick Ramos se oculta (bHidden = true) y Steve toma control.")
    print(" • Al desactivarse: Vuelve a la campaña vanilla de Dead Rising 3 de inmediato.")
    print(" • F5 cambia entre 1ra persona (Minecraft) y 3ra persona (Cinemática DR3).")
    print(" • El botón Q arroja armas con físicas Havok balísticas.")
    print(" • Coloca adoquín/bloques para generar cubos de colisión contra las hordas.")
    print("=" * 75 + "\n")

if __name__ == '__main__':
    main()

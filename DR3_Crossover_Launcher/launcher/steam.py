import os
import subprocess
from config.settings import STEAM_DR3_URL

def launch_dead_rising(custom_exe_path: str = None):
    """Lanza el juego a través de la API local de Steam o ruta custom."""
    if custom_exe_path and os.path.isfile(custom_exe_path):
        print(f"[*] Lanzando Dead Rising 3 directamente desde: {custom_exe_path}")
        subprocess.Popen([custom_exe_path], cwd=os.path.dirname(custom_exe_path))
        return

    print("[*] Lanzando Dead Rising 3 a través de Steam...")
    try:
        os.startfile(STEAM_DR3_URL)
    except Exception as e:
        print(f"[!] Advertencia al invocar Steam URL: {e}")
        # Buscar en rutas comunes
        common_paths = [
            r"C:\Program Files (x86)\Steam\steamapps\common\Dead Rising 3\deadrising3.exe",
            r"C:\Program Files\Steam\steamapps\common\Dead Rising 3\deadrising3.exe",
            r"D:\SteamLibrary\steamapps\common\Dead Rising 3\deadrising3.exe",
            r"E:\SteamLibrary\steamapps\common\Dead Rising 3\deadrising3.exe",
            r"D:\Games\Dead Rising 3\deadrising3.exe",
            r"C:\Games\Dead Rising 3\deadrising3.exe",
        ]
        for cp in common_paths:
            if os.path.isfile(cp):
                print(f"[*] Encontrado ejecutable alternativo: {cp}")
                subprocess.Popen([cp], cwd=os.path.dirname(cp))
                return
        print("[!] No se pudo iniciar Dead Rising 3 automáticamente. Asegúrate de iniciarlo manualmente.")

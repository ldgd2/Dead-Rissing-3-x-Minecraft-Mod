import psutil
import time
from config.settings import DR3_PROCESS_NAME, RAM_THRESHOLD_MB

def wait_for_forge_engine():
    """Bloquea la ejecución hasta que DR3 consuma la RAM necesaria."""
    print("[*] Esperando inicialización del motor Forge...")
    while True:
        try:
            for proc in psutil.process_iter(['name', 'memory_info']):
                if proc.info['name'] and proc.info['name'].lower() == DR3_PROCESS_NAME.lower():
                    mem_info = proc.info.get('memory_info')
                    if mem_info:
                        ram_usage = mem_info.rss / (1024 * 1024)
                        print(f"\r[*] deadrising3.exe detectado -> Memoria: {ram_usage:.1f} MB / {RAM_THRESHOLD_MB} MB", end="")
                        if ram_usage > RAM_THRESHOLD_MB:
                            print(f"\n[+] Forge estable detectado (RAM: {ram_usage:.1f} MB).")
                            return True
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            pass
        time.sleep(2)

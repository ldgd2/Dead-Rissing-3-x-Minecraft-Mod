import win32gui
import win32con
import win32process
import psutil
import time

def hide_glfw_window():
    """Busca EXCLUSIVAMENTE la ventana gráfica del proceso Java de Minecraft y le aplica SW_HIDE."""
    print("[*] Buscando ventana gráfica de Minecraft (javaw.exe)...")
    
    hidden_count = [0]
    
    def enum_windows_callback(hwnd, _):
        if not win32gui.IsWindowVisible(hwnd):
            return
            
        title = win32gui.GetWindowText(hwnd).strip()
        if not title:
            return
            
        # NUNCA tocar ventanas de editores, navegadores o el IDE
        title_lower = title.lower()
        if any(ign in title_lower for ign in ["antigravity", "visual studio", "code", "terminal", "powershell", "cmd", "explorer"]):
            return

        try:
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            proc_name = psutil.Process(pid).name().lower()
            
            # Solo ocultar si el proceso es java.exe o javaw.exe y la ventana es de Minecraft
            if proc_name in ("javaw.exe", "java.exe"):
                if "minecraft" in title_lower:
                    win32gui.ShowWindow(hwnd, win32con.SW_HIDE)
                    hidden_count[0] += 1
                    print(f"[+] Ventana de Minecraft oculta correctamente (PID: {pid}, Titulo: '{title}').")
        except Exception:
            pass

    intentos = 0
    while intentos < 15:
        time.sleep(1.5)
        try:
            win32gui.EnumWindows(enum_windows_callback, None)
            if hidden_count[0] > 0:
                break
            intentos += 1
        except Exception:
            pass

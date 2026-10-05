import time
import sys
import psutil
from config.logger import setup_logger, log_info, log_error
from launcher.steam import launch_dead_rising
from process.monitor import wait_for_forge_engine
from launcher.minecraft import launch_headless_engine, get_minecraft_process
from process.window import hide_glfw_window

def main():
    logger = setup_logger()
    log_info("==================================================")
    log_info("        DR3 x MINECRAFT: CROSSOVER ENGINE         ")
    log_info("==================================================")
    
    try:
        # 1. Anfitrion (Dead Rising 3)
        log_info("[Paso 1/4] Iniciando Dead Rising 3...")
        launch_dead_rising()
        wait_for_forge_engine()
        
        # Pausa para estabilizacion de CPU y memoria
        time.sleep(3) 
        
        # 2. Parasito Logico (Minecraft Fabric)
        log_info("[Paso 2/4] Levantando motor de Minecraft Fabric...")
        launch_headless_engine()
        
        # 3. Camuflaje de ventana grafica (exclusivamente para javaw.exe)
        log_info("[Paso 3/4] Ajustando visibilidad de ventana de Minecraft...")
        hide_glfw_window()
        
        log_info("==================================================")
        log_info(" ECOSISTEMA CROSSOVER EN LINEA Y OPERATIVO")
        log_info(" • Presiona '.' en Dead Rising 3 para alternar el Mod.")
        log_info(" • Registros en vivo activos en: logs/crossover_launcher.log")
        log_info("==================================================")

        # 4. Monitor de sesion en tiempo real
        mc_proc = get_minecraft_process()
        while True:
            time.sleep(1.0)
            
            # Verificar si Minecraft termino
            if mc_proc and mc_proc.poll() is not None:
                log_error(f"El proceso de Minecraft se cerro (Codigo de salida: {mc_proc.returncode}).")
                break
                
            # Verificar si Dead Rising 3 sigue corriendo
            dr3_running = any("deadrising3" in p.name().lower() for p in psutil.process_iter(['name']))
            if not dr3_running:
                log_info("Dead Rising 3 se ha cerrado. Finalizando sesion crossover limpiamente...")
                if mc_proc and mc_proc.poll() is None:
                    mc_proc.terminate()
                break

    except KeyboardInterrupt:
        log_info("Cierre solicitado por el usuario (Ctrl+C).")
    except Exception as e:
        log_error(f"Excepcion fatal en el lanzador: {e}")
        import traceback
        log_error(traceback.format_exc())
    finally:
        log_info("Sesion finalizada. Consulta 'logs/crossover_launcher.log' para detalles completos.")
        time.sleep(2)

if __name__ == "__main__":
    main()

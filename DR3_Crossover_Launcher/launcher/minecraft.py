import os
import sys
import shutil
import subprocess
import uuid
import threading
import urllib.request
import minecraft_launcher_lib
from config.settings import MC_VERSION, MC_DIRECTORY, USERNAME, JVM_ARGUMENTS
from config.logger import log_info, log_error, log_debug

_mc_process = None

def _get_root_dir():
    """Obtiene el directorio raíz sea como script o como .exe compilado."""
    if getattr(sys, 'frozen', False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def inject_mod():
    """Copia el .jar del mod y asegura Fabric API en la carpeta de mods."""
    root = _get_root_dir()
    candidates = [
        os.path.join(root, "assets", "crossover-mod.jar"),
        os.path.join(root, "crossover-mod.jar"),
        os.path.join(root, "..", "dist", "assets", "crossover-mod.jar"),
        os.path.join(root, "..", "dist", "crossover-mod.jar"),
        os.path.join(root, "..", "modMinecraft", "build", "libs", "crossover-mod.jar"),
    ]
    
    mod_source = None
    for cand in candidates:
        if os.path.exists(cand):
            mod_source = os.path.abspath(cand)
            break

    mods_folder = os.path.join(MC_DIRECTORY, "mods")
    os.makedirs(mods_folder, exist_ok=True)
    
    # 1. Inyectar crossover-mod.jar
    if mod_source and os.path.exists(mod_source):
        log_info(f"Inyectando 'crossover-mod.jar' desde '{mod_source}' en '{mods_folder}'...")
        dest_jar = os.path.join(mods_folder, "crossover-mod.jar")
        shutil.copy2(mod_source, dest_jar)
        log_info("[+] Mod inyectado exitosamente.")
    else:
        log_error(f"No se encontro 'crossover-mod.jar' en ninguna de las rutas esperadas (ej. {candidates[0]})")

    # 2. Inyectar Fabric API (Dependencia crítica de registro y lifecycle)
    fabric_api_filename = "fabric-api-0.92.2+1.20.1.jar"
    fabric_api_dest = os.path.join(mods_folder, fabric_api_filename)
    if not os.path.exists(fabric_api_dest):
        # 2a. Buscar en cache local / assets del proyecto
        api_candidates = [
            os.path.join(root, "assets", fabric_api_filename),
            os.path.join(root, "..", "dist", "assets", fabric_api_filename),
            os.path.join(root, "dist", "assets", fabric_api_filename),
        ]
        api_source = next((c for c in api_candidates if os.path.exists(c)), None)
        
        # 2b. Buscar en cache de Gradle local
        if not api_source:
            import glob
            home = os.path.expanduser('~')
            pattern = os.path.join(home, '.gradle', 'caches', 'modules-2', 'files-2.1', 'net.fabricmc.fabric-api', 'fabric-api', '0.92.2+1.20.1', '**', '*.jar')
            jars = [f for f in glob.glob(pattern, recursive=True) if 'sources' not in f]
            if jars:
                api_source = jars[0]

        if api_source:
            log_info(f"Copiando '{fabric_api_filename}' hacia '{mods_folder}'...")
            shutil.copy2(api_source, fabric_api_dest)
            dist_assets = os.path.join(root, "..", "dist", "assets")
            if os.path.isdir(dist_assets) and not os.path.exists(os.path.join(dist_assets, fabric_api_filename)):
                shutil.copy2(api_source, os.path.join(dist_assets, fabric_api_filename))
            log_info("[+] Fabric API inyectado exitosamente.")
        else:
            log_info("[*] Descargando Fabric API 0.92.2+1.20.1...")
            maven_url = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.2%2B1.20.1/fabric-api-0.92.2%2B1.20.1.jar"
            try:
                import ssl
                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE
                with urllib.request.urlopen(maven_url, context=ctx) as resp, open(fabric_api_dest, 'wb') as out_f:
                    shutil.copyfileobj(resp, out_f)
                dist_assets = os.path.join(root, "..", "dist", "assets")
                if os.path.isdir(dist_assets):
                    shutil.copy2(fabric_api_dest, os.path.join(dist_assets, fabric_api_filename))
                log_info("[+] Fabric API descargado e inyectado con éxito.")
            except Exception as e:
                log_error(f"Error al descargar Fabric API: {e}")

def ensure_minecraft_options_and_world():
    """Garantiza options.txt sin TalkBack/accesibilidad y el mundo Void preparado."""
    # 1. options.txt (Desactiva TalkBack / Narrador / Accesibilidad)
    options_file = os.path.join(MC_DIRECTORY, "options.txt")
    default_options = {
        "version": "3465",
        "onboardAccessibility": "false",
        "narrator": "0",
        "tutorialStep": "none",
        "pauseOnLostFocus": "false",
        "soundCategory_master": "1.0",
        "soundCategory_music": "0.0",
        "guiScale": "0",
        "fullscreen": "false",
        "perspective": "1",
    }
    
    current_options = {}
    if os.path.exists(options_file):
        try:
            with open(options_file, "r", encoding="utf-8") as f:
                for line in f:
                    if ":" in line:
                        k, v = line.strip().split(":", 1)
                        current_options[k] = v
        except Exception:
            pass
            
    current_options.update(default_options)
    try:
        with open(options_file, "w", encoding="utf-8") as f:
            for k, v in current_options.items():
                f.write(f"{k}:{v}\n")
        log_info("[+] Opciones de Minecraft optimizadas (TalkBack y accesibilidad desactivados).")
    except Exception as e:
        log_error(f"Error al escribir options.txt: {e}")

    # 2. Mundo superflat Void
    void_world_dir = os.path.join(MC_DIRECTORY, "saves", "Void")
    os.makedirs(void_world_dir, exist_ok=True)
    level_dat = os.path.join(void_world_dir, "level.dat")

    # Si level.dat no existe, carece de DragonFight, de barreras o de keepInventory, limpiarlo para regenerar
    need_generate = not os.path.exists(level_dat)
    if not need_generate:
        try:
            import gzip
            with gzip.open(level_dat, 'rb') as f:
                content = f.read()
                if b'DragonFight' not in content or b'minecraft:barrier' not in content or b'keepInventory' not in content:
                    need_generate = True
                    shutil.rmtree(void_world_dir, ignore_errors=True)
                    os.makedirs(void_world_dir, exist_ok=True)
        except Exception:
            need_generate = True

    if need_generate:
            try:
                import struct, gzip
                def n_str(s):
                    b = s.encode('utf-8')
                    return struct.pack('>H', len(b)) + b
                def n_tag(t, name, p):
                    return struct.pack('>b', t) + n_str(name) + p
                    
                # 128 capas de barrera invisible (desde Y=-64 hasta Y=64) para piso sólido y transparente
                layer0 = n_tag(8, 'block', n_str('minecraft:barrier')) + n_tag(3, 'height', struct.pack('>i', 128)) + b'\x00'
                layers_p = struct.pack('>b', 10) + struct.pack('>i', 1) + layer0
                flat_s = (n_tag(8, 'biome', n_str('minecraft:the_void')) + n_tag(9, 'layers', layers_p) +
                          n_tag(9, 'structure_overrides', struct.pack('>bi', 8, 0)) +
                          n_tag(1, 'features', struct.pack('>b', 0)) + n_tag(1, 'lakes', struct.pack('>b', 0)) + b'\x00')
                gen = n_tag(8, 'type', n_str('minecraft:flat')) + n_tag(10, 'settings', flat_s) + b'\x00'
                ow = n_tag(8, 'type', n_str('minecraft:overworld')) + n_tag(10, 'generator', gen) + b'\x00'
                dims = n_tag(10, 'minecraft:overworld', ow) + b'\x00'
                wgen = (n_tag(1, 'bonus_chest', struct.pack('>b', 0)) + n_tag(1, 'generate_features', struct.pack('>b', 0)) +
                        n_tag(4, 'seed', struct.pack('>q', 0)) + n_tag(10, 'dimensions', dims) + b'\x00')
                vtag = (n_tag(3, 'Id', struct.pack('>i', 3465)) + n_tag(8, 'Name', n_str('1.20.1')) +
                        n_tag(8, 'Series', n_str('main')) + n_tag(1, 'Snapshot', struct.pack('>b', 0)) + b'\x00')
                gr = (n_tag(8, 'doDaylightCycle', n_str('false')) +
                      n_tag(8, 'doWeatherCycle', n_str('false')) +
                      n_tag(8, 'doMobSpawning', n_str('false')) +
                      n_tag(8, 'keepInventory', n_str('true')) +
                      n_tag(8, 'fallDamage', n_str('false')) +
                      n_tag(8, 'doImmediateRespawn', n_str('true')) + b'\x00')
                
                # DragonFight tag directo en Data y en DimensionData
                df_tag = (n_tag(1, 'DragonKilled', struct.pack('>b', 1)) +
                          n_tag(1, 'PreviouslyKilled', struct.pack('>b', 1)) + b'\x00')
                dim1_tag = n_tag(10, 'DragonFight', df_tag) + b'\x00'
                dim_data_tag = n_tag(10, '1', dim1_tag) + b'\x00'

                data_p = (n_tag(8, 'LevelName', n_str('Void')) + n_tag(3, 'DataVersion', struct.pack('>i', 3465)) +
                          n_tag(3, 'version', struct.pack('>i', 19133)) + n_tag(10, 'Version', vtag) +
                          n_tag(3, 'GameType', struct.pack('>i', 1)) + n_tag(1, 'Difficulty', struct.pack('>b', 0)) +
                          n_tag(1, 'DifficultyLocked', struct.pack('>b', 1)) + n_tag(1, 'allowCommands', struct.pack('>b', 1)) +
                          n_tag(1, 'initialized', struct.pack('>b', 1)) + n_tag(4, 'DayTime', struct.pack('>q', 6000)) +
                          n_tag(4, 'Time', struct.pack('>q', 6000)) + n_tag(4, 'LastPlayed', struct.pack('>q', 1700000000000)) +
                          n_tag(3, 'SpawnX', struct.pack('>i', 0)) + n_tag(3, 'SpawnY', struct.pack('>i', 65)) +
                          n_tag(3, 'SpawnZ', struct.pack('>i', 0)) + n_tag(10, 'GameRules', gr) +
                          n_tag(10, 'WorldGenSettings', wgen) +
                          n_tag(10, 'DragonFight', df_tag) +
                          n_tag(10, 'DimensionData', dim_data_tag) + b'\x00')
                full_nbt = b'\x0a\x00\x00' + n_tag(10, 'Data', data_p) + b'\x00'
                with gzip.open(level_dat, 'wb') as f:
                    f.write(full_nbt)
                log_info("[+] Generado mundo 'Void' superflat autónomamente.")
                root = _get_root_dir()
                for a_dir in [os.path.join(root, "assets", "void_world"), os.path.join(root, "..", "dist", "assets", "void_world"), os.path.join(root, "dist", "assets", "void_world")]:
                    try:
                        os.makedirs(a_dir, exist_ok=True)
                        shutil.copy2(level_dat, os.path.join(a_dir, "level.dat"))
                    except Exception:
                        pass
            except Exception as e:
                log_error(f"Error al generar level.dat: {e}")

    # Limpiar cualquier session.lock previo para evitar bloqueos de I/O en Windows
    session_lock = os.path.join(void_world_dir, "session.lock")
    if os.path.exists(session_lock):
        try:
            os.remove(session_lock)
        except Exception:
            pass

def _stream_output(pipe):
    """Hilo daemon para leer en tiempo real la salida de Minecraft y registrarla."""
    try:
        for line in iter(pipe.readline, ''):
            line_str = line.strip()
            if line_str:
                log_info(f"[Minecraft] {line_str}")
    except Exception as e:
        log_debug(f"Flujo de lectura de Minecraft finalizado: {e}")
    finally:
        pipe.close()

def launch_headless_engine():
    """Instala Fabric, inyecta el mod y lanza el proceso transmitiendo logs en vivo."""
    global _mc_process
    log_info(f"Verificando motor de Minecraft {MC_VERSION} en '{MC_DIRECTORY}'...")
    os.makedirs(MC_DIRECTORY, exist_ok=True)
    
    # 1. Instalación silenciosa de Fabric y dependencias
    try:
        log_info("Verificando instalación de Fabric Loader...")
        minecraft_launcher_lib.fabric.install_fabric(MC_VERSION, MC_DIRECTORY)
    except Exception as e:
        log_debug(f"Verificación de Fabric completada: {e}")

    # 2. Inyección del mod
    inject_mod()
    
    # 3. Preparar options.txt y mundo Void
    ensure_minecraft_options_and_world()
    
    # 4. Determinar la versión instalada de Fabric
    installed_versions = minecraft_launcher_lib.utils.get_installed_versions(MC_DIRECTORY)
    fabric_version = None
    for v in installed_versions:
        if "fabric" in v.get("id", "").lower() and MC_VERSION in v.get("id", ""):
            fabric_version = v["id"]
            break
            
    if not fabric_version:
        latest = minecraft_launcher_lib.utils.get_latest_version(MC_DIRECTORY)
        fabric_version = latest["id"] if latest else f"fabric-loader-{MC_VERSION}"
    
    log_info(f"Versión de Fabric seleccionada: {fabric_version}")

    options = {
        "username": USERNAME,
        "uuid": str(uuid.uuid4()),
        "token": "0",  # El string "0" desactiva los intentos de conexión web de Authlib
        "jvmArguments": JVM_ARGUMENTS
    }
    
    log_info("Iniciando Minecraft Fabric en segundo plano...")
    try:
        command = minecraft_launcher_lib.command.get_minecraft_command(fabric_version, MC_DIRECTORY, options)
        
        # Lanzar proceso capturando stdout y stderr unificados para logging en tiempo real
        _mc_process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1
        )
        
        # Iniciar hilo de lectura continua
        t = threading.Thread(target=_stream_output, args=(_mc_process.stdout,), daemon=True)
        t.start()
        
        log_info(f"[OK] Proceso de Minecraft lanzado (PID: {_mc_process.pid}). Logs conectados.")
    except Exception as e:
        log_error(f"Error al generar comando de Minecraft: {e}")
        try:
            log_info("Descargando componentes base de Minecraft...")
            minecraft_launcher_lib.install.install_minecraft_version(MC_VERSION, MC_DIRECTORY)
            command = minecraft_launcher_lib.command.get_minecraft_command(fabric_version, MC_DIRECTORY, options)
            _mc_process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1
            )
            t = threading.Thread(target=_stream_output, args=(_mc_process.stdout,), daemon=True)
            t.start()
            log_info(f"[OK] Minecraft descargado y lanzado con éxito (PID: {_mc_process.pid}).")
        except Exception as e2:
            log_error(f"Fallo crítico al iniciar Minecraft: {e2}")

def get_minecraft_process():
    return _mc_process

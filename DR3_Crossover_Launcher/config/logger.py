import os
import sys
import logging
from datetime import datetime

_logger = None

def setup_logger():
    global _logger
    if _logger is not None:
        return _logger

    # Determinar carpeta de logs
    if getattr(sys, 'frozen', False):
        base_dir = os.path.dirname(sys.executable)
    else:
        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    logs_dir = os.path.join(base_dir, "logs")
    os.makedirs(logs_dir, exist_ok=True)

    log_file = os.path.join(logs_dir, "crossover_launcher.log")

    _logger = logging.getLogger("CrossoverLauncher")
    _logger.setLevel(logging.DEBUG)

    # Formato limpio con timestamp milisegundos
    formatter = logging.Formatter(
        "[%(asctime)s.%(msecs)03d] [%(levelname)s] %(message)s",
        datefmt="%H:%M:%S"
    )

    # Handler a archivo (sobrescribir en cada arranque para depuración limpia)
    file_handler = logging.FileHandler(log_file, mode="w", encoding="utf-8")
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(formatter)
    _logger.addHandler(file_handler)

    # Handler a consola (stdout)
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(formatter)
    _logger.addHandler(console_handler)

    _logger.info(f"Sistema de registro en tiempo real iniciado. Archivo de log: {log_file}")
    return _logger

def log_info(msg: str):
    logger = setup_logger()
    logger.info(msg)

def log_error(msg: str):
    logger = setup_logger()
    logger.error(msg)

def log_debug(msg: str):
    logger = setup_logger()
    logger.debug(msg)

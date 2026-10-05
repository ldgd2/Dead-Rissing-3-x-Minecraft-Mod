import os

# --- DR3 Config ---
STEAM_DR3_URL = "steam://rungameid/265550"
DR3_PROCESS_NAME = "deadrising3.exe"
RAM_THRESHOLD_MB = 400

# --- Minecraft Config ---
MC_VERSION = "1.20.1"
MC_DIRECTORY = os.path.join(os.getenv("APPDATA"), ".crossover_mc")
USERNAME = "Lider"
JVM_ARGUMENTS = [
    "-Xmx4G",
    "-XX:+UnlockExperimentalVMOptions",
    "-Dcrossover.headless=true",
    "-Dcrossover.world=Void"
]

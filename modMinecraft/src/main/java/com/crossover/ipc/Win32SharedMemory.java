package com.crossover.ipc;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import com.crossover.player.camera.BoneRotation;
import com.crossover.player.camera.BoneRetargetingData;

/**
 * Cliente IPC de Memoria Compartida para Minecraft Fabric.
 * Utiliza JNA para obtener el Handle de Windows y Unsafe para lectura cruda y Lock-Free.
 */
public class Win32SharedMemory {

    // --- Definiciones de JNA para Kernel32 ---
    public interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int FILE_MAP_ALL_ACCESS = 0xF001F;

        Pointer OpenFileMappingA(int dwDesiredAccess, boolean bInheritHandle, String lpName);
        Pointer CreateFileMappingA(Pointer hFile, Pointer lpAttributes, int flProtect, int dwMaximumSizeHigh, int dwMaximumSizeLow, String lpName);
        Pointer MapViewOfFile(Pointer hFileMappingObject, int dwDesiredAccess, int dwFileOffsetHigh, int dwFileOffsetLow, int dwNumberOfBytesToMap);
        boolean UnmapViewOfFile(Pointer lpBaseAddress);
        boolean CloseHandle(Pointer hObject);
    }

    private Pointer hMapFile;
    private Pointer pBuf;
    private long baseAddress;
    private Unsafe unsafe;

    // --- Offsets de la Estructura (Deben coincidir byte a byte con SharedMemoryLayout.h de C++) ---
    public static final long OFFSET_SEQLOCK = 0x00;
    public static final long OFFSET_MAGIC = 0x04;
    public static final long OFFSET_PLAYER_X = 0x08;
    public static final long OFFSET_PLAYER_Y = 0x0C;
    public static final long OFFSET_PLAYER_Z = 0x10;
    
    // --- Offsets de Autoridad Cruzada (Atomic Bools en C++) ---
    public static final long OFFSET_REQUEST_AUTHORITY_MC = 0x14; // uint8_t (1 byte: 1 = MC 1ra pers, 0 = DR3 3ra pers)
    public static final long OFFSET_ACK_AUTHORITY_DR3 = 0x15;    // uint8_t (1 byte: 1 = Confirmado por DR3)
    public static final long OFFSET_CAMERA_MODE = 0x16;          // uint16_t (2 bytes: 0 = 3rd, 1 = 1st)

    public static final long OFFSET_HEALTH = 0x1A;
    public static final long OFFSET_XP_LEVEL = 0x1E;
    public static final long OFFSET_PP_POINTS = 0x22;
    public static final long OFFSET_WEAPON_DURABILITY = 0x26;    // uint32_t (4 bytes: condición actual de [rcx+10])

    public static final long OFFSET_CAM_PITCH = 0x2A;
    public static final long OFFSET_CAM_YAW = 0x2E;
    public static final long OFFSET_CAM_ROLL = 0x32;
    public static final long OFFSET_CAM_FOV = 0x36;
    public static final long OFFSET_WEAPON_MAX_DURABILITY = 0x3A;// uint32_t (4 bytes)

    // Offsets de Retargeting Huesos (Cuaterniones: 4 floats = 16 bytes c/u)
    public static final long OFFSET_BONE_HEAD = 0x40;
    public static final long OFFSET_BONE_TORSO = 0x50;
    public static final long OFFSET_BONE_R_ARM = 0x60;
    public static final long OFFSET_BONE_L_ARM = 0x70;
    public static final long OFFSET_BONE_R_LEG = 0x80;
    public static final long OFFSET_BONE_L_LEG = 0x90;

    public static final long OFFSET_NICK_HIDDEN = 0xA0;

    // Pilar 3: Ecosistema del Inventario Dinámico (El Ítem Universal vía NBT)
    public static final long OFFSET_LOOT_COUNTER = 0xB0;         // uint32_t (4 bytes: contador monotónico)
    public static final long OFFSET_LOOT_ITEM_ID = 0xB4;         // uint32_t (4 bytes: ID numérico del ítem DR3)
    public static final long OFFSET_LOOT_DURABILITY = 0xB8;      // uint32_t (4 bytes: condición actual)
    public static final long OFFSET_LOOT_MAX_DURABILITY = 0xBC;  // uint32_t (4 bytes: condición máxima)
    public static final long OFFSET_LOOT_AMMO = 0xC0;            // uint32_t (4 bytes: munición)
    public static final long OFFSET_LOOT_DISPLAY_NAME = 0xC4;    // char[64] (64 bytes: texto plano del nombre en DR3)
    public static final long OFFSET_LOOT_ICON_ID = 0x104;        // char[64] (64 bytes: nombre del icono 2D original)

    // Pilar 4: Lanzamiento de Armas, Disparo y Uso de Ítems (Físicas Havok)
    public static final long OFFSET_USE_ACTION_COUNTER = 0x144;
    public static final long OFFSET_USE_ACTION_TYPE = 0x148;
    public static final long OFFSET_WEAPON_AMMO = 0x14C;

    public static final long OFFSET_THROW_ACTION_COUNTER = 0x150;
    public static final long OFFSET_THROW_ITEM_TYPE = 0x154;
    public static final long OFFSET_THROW_ORIGIN_X = 0x158;
    public static final long OFFSET_THROW_ORIGIN_Y = 0x15C;
    public static final long OFFSET_THROW_ORIGIN_Z = 0x160;
    public static final long OFFSET_THROW_DIR_X = 0x164;
    public static final long OFFSET_THROW_DIR_Y = 0x168;
    public static final long OFFSET_THROW_DIR_Z = 0x16C;
    public static final long OFFSET_THROW_IS_MC_ITEM = 0x170;
    public static final long OFFSET_THROW_ITEM_NAME = 0x174;

    public static final long OFFSET_LANDED_EVENT_COUNTER = 0x1B4;
    public static final long OFFSET_LANDED_POS_X = 0x1B8;
    public static final long OFFSET_LANDED_POS_Y = 0x1BC;
    public static final long OFFSET_LANDED_POS_Z = 0x1C0;
    public static final long OFFSET_LANDED_HIT_ZOMBIE = 0x1C4;
    public static final long OFFSET_LANDED_DAMAGE_DEALT = 0x1C8;

    // Pilar 5: Construcción y Entorno (Sincronización del Reloj y Bloqueo de Hordas)
    public static final long OFFSET_WORLD_TIME_HOURS = 0x1D0;
    public static final long OFFSET_WORLD_TIME_MINUTES = 0x1D4;
    public static final long OFFSET_TIME_HOOK_ACTIVE = 0x1D8;

    public static final long OFFSET_BUILDING_ACTION_COUNTER = 0x1DC;
    public static final long OFFSET_BUILDING_ACTION_TYPE = 0x1E0;
    public static final long OFFSET_BUILDING_BLOCK_X = 0x1E4;
    public static final long OFFSET_BUILDING_BLOCK_Y = 0x1E8;
    public static final long OFFSET_BUILDING_BLOCK_Z = 0x1EC;
    public static final long OFFSET_BUILDING_BLOCK_ID = 0x1F0;

    public static final long OFFSET_COLLISION_CUBES_ACTIVE = 0x210;
    public static final long OFFSET_CROSSOVER_ACTIVE = 0x214;
    public static final long OFFSET_ACTIVE_HELD_ITEM_IS_MC = 0x215;
    public static final long OFFSET_MC_ACTION_Q_TRIGGER = 0x216;
    public static final long OFFSET_MC_ACTION_RIGHT_CLICK = 0x217;
    public static final long OFFSET_STEVE_DEAD = 0x218;
    public static final long OFFSET_NICK_DEAD = 0x219;
    public static final long OFFSET_MC_HEALTH_CHANGED = 0x21A;
    public static final long OFFSET_DR3_HEALTH_CHANGED = 0x21B;

    // Puente de Interfaz y Controles de Minecraft en DR3 (Inventario 'E', Hotbar y Ratón)
    public static final long OFFSET_MC_KEY_INVENTORY_TOGGLE = 0x21C;
    public static final long OFFSET_MC_SELECT_HOTBAR_SLOT = 0x21D;
    public static final long OFFSET_MC_HOTBAR_SCROLL = 0x21E;
    public static final long OFFSET_MC_GUI_OPEN = 0x21F;
    public static final long OFFSET_MC_MOUSE_X = 0x220;
    public static final long OFFSET_MC_MOUSE_Y = 0x224;
    public static final long OFFSET_MC_MOUSE_LEFT_CLICK = 0x228;
    public static final long OFFSET_MC_MOUSE_RIGHT_CLICK = 0x229;

    // Puente de Movimiento y Acción en 1ra Persona
    public static final long OFFSET_MC_INPUT_W = 0x22A;
    public static final long OFFSET_MC_INPUT_A = 0x22B;
    public static final long OFFSET_MC_INPUT_S = 0x22C;
    public static final long OFFSET_MC_INPUT_D = 0x22D;
    public static final long OFFSET_MC_INPUT_JUMP = 0x22E;
    public static final long OFFSET_MC_INPUT_SNEAK = 0x22F;
    public static final long OFFSET_MC_ACTION_LEFT_CLICK = 0x230;

    // Sincronización precisa de Puntos de Vida y Corazones Dorados (Absorción)
    public static final long OFFSET_DR3_MAX_HEALTH = 0x234;
    public static final long OFFSET_DR3_RAW_HEALTH = 0x238;

    private int localUseCounter = 0;
    private int localThrowCounter = 0;
    private int localBuildingCounter = 0;

    public Win32SharedMemory() {
        this.unsafe = getUnsafe();
    }

    private Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo obtener sun.misc.Unsafe", e);
        }
    }

    public boolean connect(String mapName, int size) {
        System.out.println("[Fabric IPC] Conectando a Memoria Compartida: " + mapName);

        hMapFile = Kernel32.INSTANCE.OpenFileMappingA(Kernel32.FILE_MAP_ALL_ACCESS, false, mapName);
        if (hMapFile == null) {
            System.err.println("[Fabric IPC] Falló OpenFileMapping. Asegúrate de que el Orquestador C++ esté corriendo.");
            return false;
        }

        pBuf = Kernel32.INSTANCE.MapViewOfFile(hMapFile, Kernel32.FILE_MAP_ALL_ACCESS, 0, 0, size);
        if (pBuf == null) {
            System.err.println("[Fabric IPC] Falló MapViewOfFile.");
            Kernel32.INSTANCE.CloseHandle(hMapFile);
            return false;
        }

        this.baseAddress = Pointer.nativeValue(pBuf);
        System.out.println("[Fabric IPC] Conectado exitosamente en la dirección: 0x" + Long.toHexString(baseAddress));
        return true;
    }

    public void disconnect() {
        if (pBuf != null) Kernel32.INSTANCE.UnmapViewOfFile(pBuf);
        if (hMapFile != null) Kernel32.INSTANCE.CloseHandle(hMapFile);
    }

    public boolean isConnected() {
        return baseAddress != 0;
    }

    // --- Lectura/Escritura Lock-Free de Posición ---

    public float[] readPlayerPositionLockFree() {
        if (baseAddress == 0) return new float[]{0, 0, 0};

        int seq1, seq2;
        float x, y, z;
        do {
            seq1 = unsafe.getIntVolatile(null, baseAddress + OFFSET_SEQLOCK);
            x = unsafe.getFloat(baseAddress + OFFSET_PLAYER_X);
            y = unsafe.getFloat(baseAddress + OFFSET_PLAYER_Y);
            z = unsafe.getFloat(baseAddress + OFFSET_PLAYER_Z);
            seq2 = unsafe.getIntVolatile(null, baseAddress + OFFSET_SEQLOCK);
        } while (seq1 != seq2 || (seq1 & 1) != 0);

        return new float[]{x, y, z};
    }

    public float readPlayerX() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_PLAYER_X);
    }

    public float readPlayerY() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_PLAYER_Y);
    }

    public float readPlayerZ() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_PLAYER_Z);
    }

    public void writePlayerPosition(float x, float y, float z) {
        if (baseAddress == 0) return;
        unsafe.putFloat(baseAddress + OFFSET_PLAYER_X, x);
        unsafe.putFloat(baseAddress + OFFSET_PLAYER_Y, y);
        unsafe.putFloat(baseAddress + OFFSET_PLAYER_Z, z);
    }

    // --- Control de Cámara y Ángulos ---

    public void writeCameraAngles(float pitch, float yaw, float roll, float fov) {
        if (baseAddress == 0) return;
        unsafe.putFloat(baseAddress + OFFSET_CAM_PITCH, pitch);
        unsafe.putFloat(baseAddress + OFFSET_CAM_YAW, yaw);
        unsafe.putFloat(baseAddress + OFFSET_CAM_ROLL, roll);
        unsafe.putFloat(baseAddress + OFFSET_CAM_FOV, fov);
    }

    // --- Autoridad de Físicas F5 ---

    public void writeAuthorityRequest(boolean requestMC) {
        if (baseAddress == 0) return;
        unsafe.putByte(baseAddress + OFFSET_REQUEST_AUTHORITY_MC, (byte) (requestMC ? 1 : 0));
    }

    public boolean readAckAuthorityYielded() {
        if (baseAddress == 0) return false;
        byte ack = unsafe.getByteVolatile(null, baseAddress + OFFSET_ACK_AUTHORITY_DR3);
        return ack == 1;
    }

    public int readCameraMode() {
        if (baseAddress == 0) return 0;
        return unsafe.getShort(baseAddress + OFFSET_CAMERA_MODE);
    }

    // --- Sincronización de Salud y Muerte Cruzada (Nick <-> Steve) ---

    public float readPlayerHealth() {
        if (baseAddress == 0) return 100.0f;
        return unsafe.getFloat(baseAddress + OFFSET_HEALTH);
    }

    public void writePlayerHealth(float health) {
        if (baseAddress == 0) return;
        unsafe.putFloat(baseAddress + OFFSET_HEALTH, health);
        unsafe.putByte(baseAddress + OFFSET_MC_HEALTH_CHANGED, (byte) 1);
    }

    public boolean readDr3HealthChanged() {
        if (baseAddress == 0) return false;
        byte val = unsafe.getByteVolatile(null, baseAddress + OFFSET_DR3_HEALTH_CHANGED);
        if (val == 1) {
            unsafe.putByte(baseAddress + OFFSET_DR3_HEALTH_CHANGED, (byte) 0);
            return true;
        }
        return false;
    }

    public float readDr3MaxHealth() {
        if (baseAddress == 0) return 1000.0f;
        float maxHp = unsafe.getFloat(baseAddress + OFFSET_DR3_MAX_HEALTH);
        return maxHp >= 100.0f ? maxHp : 1000.0f;
    }

    public float readDr3RawHealth() {
        if (baseAddress == 0) return 1000.0f;
        return unsafe.getFloat(baseAddress + OFFSET_DR3_RAW_HEALTH);
    }

    public void writeDr3MaxHealth(float maxHealth) {
        if (baseAddress == 0) return;
        unsafe.putFloat(baseAddress + OFFSET_DR3_MAX_HEALTH, maxHealth);
    }

    public void writeDr3RawHealth(float rawHealth) {
        if (baseAddress == 0) return;
        unsafe.putFloat(baseAddress + OFFSET_DR3_RAW_HEALTH, rawHealth);
    }

    public void writeSteveDead(boolean dead) {
        if (baseAddress == 0) return;
        unsafe.putByte(baseAddress + OFFSET_STEVE_DEAD, (byte) (dead ? 1 : 0));
    }

    public boolean readNickDead() {
        if (baseAddress == 0) return false;
        return unsafe.getByteVolatile(null, baseAddress + OFFSET_NICK_DEAD) == 1;
    }

    public void clearNickDead() {
        if (baseAddress == 0) return;
        unsafe.putByte(baseAddress + OFFSET_NICK_DEAD, (byte) 0);
    }

    // --- Orientación y Ángulos de Cámara de DR3 ---

    public float readCameraPitch() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_CAM_PITCH);
    }

    public float readCameraYaw() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_CAM_YAW);
    }

    public float readCameraFov() {
        if (baseAddress == 0) return 70.0f;
        return unsafe.getFloat(baseAddress + OFFSET_CAM_FOV);
    }

    // --- Pilar 3: Durabilidad de Armas (83 79 10 00) ---

    public int readWeaponDurability() {
        if (baseAddress == 0) return 0;
        return unsafe.getInt(baseAddress + OFFSET_WEAPON_DURABILITY);
    }

    public int readWeaponMaxDurability() {
        if (baseAddress == 0) return 100;
        int max = unsafe.getInt(baseAddress + OFFSET_WEAPON_MAX_DURABILITY);
        return max > 0 ? max : 100;
    }

    // --- Pilar 3: Recolección e Inventario (Loot Buffer) ---

    public int readLootCounter() {
        if (baseAddress == 0) return 0;
        return unsafe.getIntVolatile(null, baseAddress + OFFSET_LOOT_COUNTER);
    }

    public int readLootItemId() {
        if (baseAddress == 0) return 0;
        return unsafe.getInt(baseAddress + OFFSET_LOOT_ITEM_ID);
    }

    public int readLootDurability() {
        if (baseAddress == 0) return 100;
        return unsafe.getInt(baseAddress + OFFSET_LOOT_DURABILITY);
    }

    public int readLootMaxDurability() {
        if (baseAddress == 0) return 100;
        int max = unsafe.getInt(baseAddress + OFFSET_LOOT_MAX_DURABILITY);
        return max > 0 ? max : 100;
    }

    public int readLootAmmo() {
        if (baseAddress == 0) return 0;
        return unsafe.getInt(baseAddress + OFFSET_LOOT_AMMO);
    }

    public String readLootDisplayName() {
        if (baseAddress == 0) return "Arma de Los Perdidos";
        byte[] bytes = new byte[64];
        for (int i = 0; i < 64; i++) {
            byte b = unsafe.getByte(baseAddress + OFFSET_LOOT_DISPLAY_NAME + i);
            if (b == 0) break;
            bytes[i] = b;
        }
        String str = new String(bytes, StandardCharsets.UTF_8).trim();
        return str.isEmpty() ? "Arma de Los Perdidos" : str;
    }

    public String readLootIconId() {
        if (baseAddress == 0) return "icon_default";
        byte[] bytes = new byte[64];
        for (int i = 0; i < 64; i++) {
            byte b = unsafe.getByte(baseAddress + OFFSET_LOOT_ICON_ID + i);
            if (b == 0) break;
            bytes[i] = b;
        }
        String str = new String(bytes, StandardCharsets.UTF_8).trim();
        return str.isEmpty() ? "icon_default" : str;
    }

    // --- Retargeting de Huesos ---

    private BoneRotation readBoneRotationFromQuaternion(long offset) {
        if (baseAddress == 0) return BoneRotation.ZERO;

        float qx = unsafe.getFloat(baseAddress + offset);
        float qy = unsafe.getFloat(baseAddress + offset + 4);
        float qz = unsafe.getFloat(baseAddress + offset + 8);
        float qw = unsafe.getFloat(baseAddress + offset + 12);

        double sinr_cosp = 2.0 * (qw * qx + qy * qz);
        double cosr_cosp = 1.0 - 2.0 * (qx * qx + qy * qy);
        float roll = (float) Math.atan2(sinr_cosp, cosr_cosp);

        double sinp = 2.0 * (qw * qy - qz * qx);
        float pitch;
        if (Math.abs(sinp) >= 1.0) {
            pitch = (float) Math.copySign(Math.PI / 2.0, sinp);
        } else {
            pitch = (float) Math.asin(sinp);
        }

        double siny_cosp = 2.0 * (qw * qz + qx * qy);
        double cosy_cosp = 1.0 - 2.0 * (qy * qy + qz * qz);
        float yaw = (float) Math.atan2(siny_cosp, cosy_cosp);

        return new BoneRotation(pitch, yaw, roll);
    }

    public BoneRetargetingData readBoneRetargeting() {
        if (baseAddress == 0) {
            return new BoneRetargetingData(
                BoneRotation.ZERO, BoneRotation.ZERO,
                BoneRotation.ZERO, BoneRotation.ZERO,
                BoneRotation.ZERO, BoneRotation.ZERO
            );
        }

        BoneRotation head     = readBoneRotationFromQuaternion(OFFSET_BONE_HEAD);
        BoneRotation torso    = readBoneRotationFromQuaternion(OFFSET_BONE_TORSO);
        BoneRotation rightArm = readBoneRotationFromQuaternion(OFFSET_BONE_R_ARM);
        BoneRotation leftArm  = readBoneRotationFromQuaternion(OFFSET_BONE_L_ARM);
        BoneRotation rightLeg = readBoneRotationFromQuaternion(OFFSET_BONE_R_LEG);
        BoneRotation leftLeg  = readBoneRotationFromQuaternion(OFFSET_BONE_L_LEG);

        return new BoneRetargetingData(head, torso, rightArm, leftArm, rightLeg, leftLeg);
    }

    // --- Estadísticas y Utilidades ---

    public float readFloat(long offset) {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + offset);
    }

    public int readInt(long offset) {
        if (baseAddress == 0) return 0;
        return unsafe.getInt(baseAddress + offset);
    }

    // --- Pilar 4: Lanzamiento de Armas, Disparo y Uso de Ítems ---

    public void sendUseAction(int actionType) {
        if (baseAddress == 0) return;
        localUseCounter++;
        unsafe.putInt(baseAddress + OFFSET_USE_ACTION_TYPE, actionType);
        unsafe.putInt(baseAddress + OFFSET_USE_ACTION_COUNTER, localUseCounter);
    }

    public void sendThrowAction(int itemType, double originX, double originY, double originZ,
                                double dirX, double dirY, double dirZ, boolean isMcItem, String itemName) {
        if (baseAddress == 0) return;
        localThrowCounter++;
        unsafe.putInt(baseAddress + OFFSET_THROW_ITEM_TYPE, itemType);
        unsafe.putFloat(baseAddress + OFFSET_THROW_ORIGIN_X, (float) originX);
        unsafe.putFloat(baseAddress + OFFSET_THROW_ORIGIN_Y, (float) originY);
        unsafe.putFloat(baseAddress + OFFSET_THROW_ORIGIN_Z, (float) originZ);
        unsafe.putFloat(baseAddress + OFFSET_THROW_DIR_X, (float) dirX);
        unsafe.putFloat(baseAddress + OFFSET_THROW_DIR_Y, (float) dirY);
        unsafe.putFloat(baseAddress + OFFSET_THROW_DIR_Z, (float) dirZ);
        unsafe.putByte(baseAddress + OFFSET_THROW_IS_MC_ITEM, (byte) (isMcItem ? 1 : 0));

        // Escribir nombre del ítem (64 bytes máx)
        long namePtr = baseAddress + OFFSET_THROW_ITEM_NAME;
        for (int i = 0; i < 64; i++) unsafe.putByte(namePtr + i, (byte) 0);
        if (itemName != null) {
            byte[] bytes = itemName.getBytes(StandardCharsets.UTF_8);
            int len = Math.min(bytes.length, 63);
            for (int i = 0; i < len; i++) {
                unsafe.putByte(namePtr + i, bytes[i]);
            }
        }

        unsafe.putInt(baseAddress + OFFSET_THROW_ACTION_COUNTER, localThrowCounter);
    }

    public int readWeaponAmmo() {
        if (baseAddress == 0) return 0;
        return unsafe.getInt(baseAddress + OFFSET_WEAPON_AMMO);
    }

    public int readLandedEventCounter() {
        if (baseAddress == 0) return 0;
        return unsafe.getInt(baseAddress + OFFSET_LANDED_EVENT_COUNTER);
    }

    public float readLandedX() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_LANDED_POS_X);
    }

    public float readLandedY() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_LANDED_POS_Y);
    }

    public float readLandedZ() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_LANDED_POS_Z);
    }

    public boolean readLandedHitZombie() {
        if (baseAddress == 0) return false;
        return unsafe.getByte(baseAddress + OFFSET_LANDED_HIT_ZOMBIE) == 1;
    }

    public float readLandedDamage() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_LANDED_DAMAGE_DEALT);
    }

    // --- Pilar 5: Sincronización del Reloj y Construcción (Muros Havok) ---

    public float readWorldTimeHours() {
        if (baseAddress == 0) return 12.0f;
        return unsafe.getFloat(baseAddress + OFFSET_WORLD_TIME_HOURS);
    }

    public float readWorldTimeMinutes() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_WORLD_TIME_MINUTES);
    }

    public int readWorldTimeMinutesInt() {
        return (int) readWorldTimeMinutes();
    }

    public boolean isTimeHookActive() {
        if (baseAddress == 0) return false;
        return unsafe.getByte(baseAddress + OFFSET_TIME_HOOK_ACTIVE) == 1;
    }

    public void sendBlockPlace(int x, int y, int z, String blockId) {
        if (baseAddress == 0) return;
        localBuildingCounter++;
        unsafe.putInt(baseAddress + OFFSET_BUILDING_ACTION_TYPE, 1); // 1 = PLACE
        unsafe.putInt(baseAddress + OFFSET_BUILDING_BLOCK_X, x);
        unsafe.putInt(baseAddress + OFFSET_BUILDING_BLOCK_Y, y);
        unsafe.putInt(baseAddress + OFFSET_BUILDING_BLOCK_Z, z);

        long idPtr = baseAddress + OFFSET_BUILDING_BLOCK_ID;
        for (int i = 0; i < 32; i++) unsafe.putByte(idPtr + i, (byte) 0);
        if (blockId != null) {
            byte[] bytes = blockId.getBytes(StandardCharsets.UTF_8);
            int len = Math.min(bytes.length, 31);
            for (int i = 0; i < len; i++) unsafe.putByte(idPtr + i, bytes[i]);
        }

        unsafe.putInt(baseAddress + OFFSET_BUILDING_ACTION_COUNTER, localBuildingCounter);
    }

    public void sendBlockBreak(int x, int y, int z) {
        if (baseAddress == 0) return;
        localBuildingCounter++;
        unsafe.putInt(baseAddress + OFFSET_BUILDING_ACTION_TYPE, 2); // 2 = BREAK
        unsafe.putInt(baseAddress + OFFSET_BUILDING_BLOCK_X, x);
        unsafe.putInt(baseAddress + OFFSET_BUILDING_BLOCK_Y, y);
        unsafe.putInt(baseAddress + OFFSET_BUILDING_BLOCK_Z, z);
        unsafe.putInt(baseAddress + OFFSET_BUILDING_ACTION_COUNTER, localBuildingCounter);
    }

    public int readActiveCollisionCubes() {
        if (baseAddress == 0) return 0;
        return unsafe.getInt(baseAddress + OFFSET_COLLISION_CUBES_ACTIVE);
    }

    public void writeActiveHeldItemIsMc(boolean isMc) {
        if (baseAddress == 0) return;
        unsafe.putByte(baseAddress + OFFSET_ACTIVE_HELD_ITEM_IS_MC, (byte) (isMc ? 1 : 0));
    }

    public boolean readActiveHeldItemIsMc() {
        if (baseAddress == 0) return false;
        return unsafe.getByte(baseAddress + OFFSET_ACTIVE_HELD_ITEM_IS_MC) != 0;
    }

    public boolean consumeActionQTrigger() {
        if (baseAddress == 0) return false;
        byte val = unsafe.getByte(baseAddress + OFFSET_MC_ACTION_Q_TRIGGER);
        if (val != 0) {
            unsafe.putByte(baseAddress + OFFSET_MC_ACTION_Q_TRIGGER, (byte) 0);
            return true;
        }
        return false;
    }

    public boolean readActionRightClick() {
        if (baseAddress == 0) return false;
        return unsafe.getByte(baseAddress + OFFSET_MC_ACTION_RIGHT_CLICK) != 0;
    }

    public void consumeActionRightClick() {
        if (baseAddress == 0) return;
        unsafe.putByte(baseAddress + OFFSET_MC_ACTION_RIGHT_CLICK, (byte) 0);
    }

    public boolean consumeInventoryToggle() {
        if (baseAddress == 0) return false;
        byte val = unsafe.getByte(baseAddress + OFFSET_MC_KEY_INVENTORY_TOGGLE);
        if (val != 0) {
            unsafe.putByte(baseAddress + OFFSET_MC_KEY_INVENTORY_TOGGLE, (byte) 0);
            return true;
        }
        return false;
    }

    public int consumeHotbarSlotSelect() {
        if (baseAddress == 0) return 0;
        byte val = unsafe.getByte(baseAddress + OFFSET_MC_SELECT_HOTBAR_SLOT);
        if (val != 0) {
            unsafe.putByte(baseAddress + OFFSET_MC_SELECT_HOTBAR_SLOT, (byte) 0);
            return (int) val;
        }
        return 0;
    }

    public int consumeHotbarScroll() {
        if (baseAddress == 0) return 0;
        byte val = unsafe.getByte(baseAddress + OFFSET_MC_HOTBAR_SCROLL);
        if (val != 0) {
            unsafe.putByte(baseAddress + OFFSET_MC_HOTBAR_SCROLL, (byte) 0);
            return (int) val;
        }
        return 0;
    }

    public void writeGuiOpen(boolean isOpen) {
        if (baseAddress == 0) return;
        unsafe.putByte(baseAddress + OFFSET_MC_GUI_OPEN, (byte) (isOpen ? 1 : 0));
    }

    public float readMouseX() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_MC_MOUSE_X);
    }

    public float readMouseY() {
        if (baseAddress == 0) return 0.0f;
        return unsafe.getFloat(baseAddress + OFFSET_MC_MOUSE_Y);
    }

    public boolean consumeMouseLeftClick() {
        if (baseAddress == 0) return false;
        byte val = unsafe.getByte(baseAddress + OFFSET_MC_MOUSE_LEFT_CLICK);
        if (val != 0) {
            unsafe.putByte(baseAddress + OFFSET_MC_MOUSE_LEFT_CLICK, (byte) 0);
            return true;
        }
        return false;
    }

    public boolean consumeMouseRightClick() {
        if (baseAddress == 0) return false;
        byte val = unsafe.getByte(baseAddress + OFFSET_MC_MOUSE_RIGHT_CLICK);
        if (val != 0) {
            unsafe.putByte(baseAddress + OFFSET_MC_MOUSE_RIGHT_CLICK, (byte) 0);
            return true;
        }
        return false;
    }

    public boolean readInputW() { return baseAddress != 0 && unsafe.getByte(baseAddress + OFFSET_MC_INPUT_W) != 0; }
    public boolean readInputA() { return baseAddress != 0 && unsafe.getByte(baseAddress + OFFSET_MC_INPUT_A) != 0; }
    public boolean readInputS() { return baseAddress != 0 && unsafe.getByte(baseAddress + OFFSET_MC_INPUT_S) != 0; }
    public boolean readInputD() { return baseAddress != 0 && unsafe.getByte(baseAddress + OFFSET_MC_INPUT_D) != 0; }
    public boolean readInputJump() { return baseAddress != 0 && unsafe.getByte(baseAddress + OFFSET_MC_INPUT_JUMP) != 0; }
    public boolean readInputSneak() { return baseAddress != 0 && unsafe.getByte(baseAddress + OFFSET_MC_INPUT_SNEAK) != 0; }

    public boolean consumeActionLeftClick() {
        if (baseAddress == 0) return false;
        byte val = unsafe.getByte(baseAddress + OFFSET_MC_ACTION_LEFT_CLICK);
        if (val != 0) {
            unsafe.putByte(baseAddress + OFFSET_MC_ACTION_LEFT_CLICK, (byte) 0);
            return true;
        }
        return false;
    }
}

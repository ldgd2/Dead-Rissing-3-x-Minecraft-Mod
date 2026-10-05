#pragma once
#include <cstdint>

// Constantes globales de mapeo y offsets para el bus de memoria compartida (IPC)
// Deben coincidir exactamente byte por byte con Win32SharedMemory.java

namespace IPC {

    constexpr const char* SHARED_MEMORY_NAME = "Local\\Crossover_Data";
    constexpr const char* SHARED_MEMORY_NAME_FALLBACK = "Global\\Orquestador_MC_DR3";
    constexpr size_t SHARED_MEMORY_SIZE = 10 * 1024 * 1024; // 10 MB

    constexpr uint32_t IPC_MAGIC_HEADER = 0x4452334D; // "DR3M"

    namespace Offsets {
        constexpr size_t SEQLOCK = 0x00;                  // uint32_t (4 bytes)
        constexpr size_t MAGIC = 0x04;                    // uint32_t (4 bytes)
        constexpr size_t PLAYER_X = 0x08;                 // float (4 bytes)
        constexpr size_t PLAYER_Y = 0x0C;                 // float (4 bytes)
        constexpr size_t PLAYER_Z = 0x10;                 // float (4 bytes)
        constexpr size_t REQUEST_AUTHORITY_MC = 0x14;     // uint8_t (1 byte: 1 = MC 1ra pers, 0 = DR3 3ra pers)
        constexpr size_t ACK_AUTHORITY_DR3 = 0x15;        // uint8_t (1 byte: 1 = Confirmado por DR3)
        constexpr size_t CAMERA_MODE = 0x16;              // uint16_t (2 bytes: 0 = 3rd, 1 = 1st)
        constexpr size_t RESERVED_FLAGS = 0x18;           // uint16_t (2 bytes)
        constexpr size_t HEALTH = 0x1A;                   // float (4 bytes)
        constexpr size_t XP_LEVEL = 0x1E;                 // int32_t (4 bytes)
        constexpr size_t PP_POINTS = 0x22;                // int32_t (4 bytes)
        constexpr size_t WEAPON_DURABILITY = 0x26;        // uint32_t (4 bytes: condición actual de [rcx+10])
        constexpr size_t CAM_PITCH = 0x2A;                // float (4 bytes)
        constexpr size_t CAM_YAW = 0x2E;                  // float (4 bytes)
        constexpr size_t CAM_ROLL = 0x32;                 // float (4 bytes)
        constexpr size_t CAM_FOV = 0x36;                  // float (4 bytes)
        constexpr size_t WEAPON_MAX_DURABILITY = 0x3A;    // uint32_t (4 bytes: condición máxima del arma activa)
        
        // Retargeting Huesos (Cinemática Directa -> Cuaterniones Qx, Qy, Qz, Qw)
        constexpr size_t BONE_HEAD = 0x40;                // float[4] (16 bytes)
        constexpr size_t BONE_TORSO = 0x50;               // float[4] (16 bytes)
        constexpr size_t BONE_R_ARM = 0x60;               // float[4] (16 bytes)
        constexpr size_t BONE_L_ARM = 0x70;               // float[4] (16 bytes)
        constexpr size_t BONE_R_LEG = 0x80;               // float[4] (16 bytes)
        constexpr size_t BONE_L_LEG = 0x90;               // float[4] (16 bytes)

        // Estado gráfico de Nick
        constexpr size_t NICK_HIDDEN = 0xA0;              // uint8_t (1 byte)

        // Pilar 3: Ecosistema del Inventario Dinámico (El Ítem Universal vía NBT)
        constexpr size_t LOOT_COUNTER = 0xB0;             // uint32_t (4 bytes: contador monotónico de recolección)
        constexpr size_t LOOT_ITEM_ID = 0xB4;             // uint32_t (4 bytes: ID numérico del ítem DR3)
        constexpr size_t LOOT_DURABILITY = 0xB8;          // uint32_t (4 bytes: durabilidad actual)
        constexpr size_t LOOT_MAX_DURABILITY = 0xBC;      // uint32_t (4 bytes: durabilidad máxima)
        constexpr size_t LOOT_AMMO = 0xC0;                // uint32_t (4 bytes: munición)
        constexpr size_t LOOT_DISPLAY_NAME = 0xC4;        // char[64] (64 bytes: nombre del arma en DR3 sin mapear)
        constexpr size_t LOOT_ICON_ID = 0x104;            // char[64] (64 bytes: nombre o archivo del icono 2D original)

        // Pilar 4: Lanzamiento de Armas, Disparo y Uso de Ítems (Físicas Havok)
        constexpr size_t USE_ACTION_COUNTER = 0x144;      // uint32_t (4 bytes: contador monotónico de uso)
        constexpr size_t USE_ACTION_TYPE = 0x148;         // uint32_t (4 bytes: 0=None, 1=Fire, 2=Eat)
        constexpr size_t WEAPON_AMMO = 0x14C;             // uint32_t (4 bytes: munición activa [rbx+28])

        constexpr size_t THROW_ACTION_COUNTER = 0x150;    // uint32_t (4 bytes: contador de lanzamiento con Q)
        constexpr size_t THROW_ITEM_TYPE = 0x154;         // uint32_t (4 bytes: 1=Sword, 2=Axe, 3=Shovel, 4=Pickaxe, 5=Food, 6=Block, 7=DR3_Weapon)
        constexpr size_t THROW_ORIGIN_X = 0x158;          // float (4 bytes)
        constexpr size_t THROW_ORIGIN_Y = 0x15C;          // float (4 bytes)
        constexpr size_t THROW_ORIGIN_Z = 0x160;          // float (4 bytes)
        constexpr size_t THROW_DIR_X = 0x164;             // float (4 bytes)
        constexpr size_t THROW_DIR_Y = 0x168;             // float (4 bytes)
        constexpr size_t THROW_DIR_Z = 0x16C;             // float (4 bytes)
        constexpr size_t THROW_IS_MC_ITEM = 0x170;        // uint8_t (1 byte: 1=Minecraft item, 0=DR3 weapon)
        constexpr size_t THROW_ITEM_NAME = 0x174;         // char[64] (64 bytes)

        constexpr size_t LANDED_EVENT_COUNTER = 0x1B4;    // uint32_t (4 bytes: evento cuando el proyectil se detiene en suelo)
        constexpr size_t LANDED_POS_X = 0x1B8;            // float (4 bytes)
        constexpr size_t LANDED_POS_Y = 0x1BC;            // float (4 bytes)
        constexpr size_t LANDED_POS_Z = 0x1C0;            // float (4 bytes)
        constexpr size_t LANDED_HIT_ZOMBIE = 0x1C4;       // uint8_t (1 byte: 1 si impactó zombie, 0 suelo)
        constexpr size_t LANDED_DAMAGE_DEALT = 0x1C8;     // float (4 bytes)

        // Pilar 5: Construcción y Entorno (Sincronización del Reloj y Bloqueo de Hordas)
        constexpr size_t WORLD_TIME_HOURS = 0x1D0;        // float (4 bytes: hora de DR3 leída de firma 8B 41 28, ej: 18.5f)
        constexpr size_t WORLD_TIME_MINUTES = 0x1D4;      // uint32_t (4 bytes: minutos del día 0 a 1440)
        constexpr size_t TIME_HOOK_ACTIVE = 0x1D8;        // uint8_t (1 byte: 1 si hook AOB 8B 41 28 está activo)

        constexpr size_t BUILDING_ACTION_COUNTER = 0x1DC; // uint32_t (4 bytes: contador monotónico de bloques)
        constexpr size_t BUILDING_ACTION_TYPE = 0x1E0;    // uint32_t (4 bytes: 1=PLACE, 2=BREAK)
        constexpr size_t BUILDING_BLOCK_X = 0x1E4;        // int32_t (4 bytes: coordenada X del bloque)
        constexpr size_t BUILDING_BLOCK_Y = 0x1E8;        // int32_t (4 bytes: coordenada Y del bloque)
        constexpr size_t BUILDING_BLOCK_Z = 0x1EC;        // int32_t (4 bytes: coordenada Z del bloque)
        constexpr size_t BUILDING_BLOCK_ID = 0x1F0;       // char[32] (32 bytes: ej: "minecraft:cobblestone")

        constexpr size_t COLLISION_CUBES_ACTIVE = 0x210;  // uint32_t (4 bytes: número de cubos Havok activos en DR3)

        // Interruptor de Realidades: Hotkey '.' en DR3
        constexpr size_t CROSSOVER_ACTIVE = 0x214;        // uint8_t (1 byte: 1 = modo crossover activo, 0 = desactivado)

        // Estado del ítem en mano y sincronización nativa de acciones (Q y Clic Derecho)
        constexpr size_t ACTIVE_HELD_ITEM_IS_MC = 0x215;  // uint8_t (1 byte: 1 = Ítem de Minecraft en mano, 0 = Arma DR3 / desarmado)
        constexpr size_t MC_ACTION_Q_TRIGGER = 0x216;     // uint8_t (1 byte: 1 = Petición de lanzamiento con Q a Minecraft)
        constexpr size_t MC_ACTION_RIGHT_CLICK = 0x217;   // uint8_t (1 byte: 1 = Petición de uso nativo Clic Derecho a Minecraft)

        // Sincronización bidireccional de Salud y Muerte Cruzada (Nick <-> Steve)
        constexpr size_t STEVE_DEAD = 0x218;              // uint8_t (1 byte: 1 = Steve ha muerto en Minecraft)
        constexpr size_t NICK_DEAD = 0x219;               // uint8_t (1 byte: 1 = Nick Ramos ha muerto en DR3)
        constexpr size_t MC_HEALTH_CHANGED = 0x21A;       // uint8_t (1 byte: 1 = Minecraft actualizó el valor de salud)
        constexpr size_t DR3_HEALTH_CHANGED = 0x21B;      // uint8_t (1 byte: 1 = DR3 actualizó el valor de salud)

        // Puente de Interfaz y Controles de Minecraft en DR3 (Inventario 'E', Hotbar y Ratón)
        constexpr size_t MC_KEY_INVENTORY_TOGGLE = 0x21C; // uint8_t (1 byte: 1 = Alternar menú de inventario 'E')
        constexpr size_t MC_SELECT_HOTBAR_SLOT = 0x21D;   // uint8_t (1 byte: 1 a 9 = Slot de hotbar seleccionado)
        constexpr size_t MC_HOTBAR_SCROLL = 0x21E;        // int8_t  (1 byte: -1 / +1 desplazamiento con rueda)
        constexpr size_t MC_GUI_OPEN = 0x21F;             // uint8_t (1 byte: 1 = Hay una interfaz gráfica abierta en Minecraft)
        constexpr size_t MC_MOUSE_X = 0x220;              // float   (4 bytes: posición normalizada X del ratón 0.0 a 1.0)
        constexpr size_t MC_MOUSE_Y = 0x224;              // float   (4 bytes: posición normalizada Y del ratón 0.0 a 1.0)
        constexpr size_t MC_MOUSE_LEFT_CLICK = 0x228;     // uint8_t (1 byte: 1 = Clic izquierdo en GUI)
        constexpr size_t MC_MOUSE_RIGHT_CLICK = 0x229;    // uint8_t (1 byte: 1 = Clic derecho en GUI)

        // Puente de Movimiento y Acción en 1ra Persona (WASD + Ataque)
        constexpr size_t MC_INPUT_W = 0x22A;              // uint8_t (1 byte: 1 = W presionado)
        constexpr size_t MC_INPUT_A = 0x22B;              // uint8_t (1 byte: 1 = A presionado)
        constexpr size_t MC_INPUT_S = 0x22C;              // uint8_t (1 byte: 1 = S presionado)
        constexpr size_t MC_INPUT_D = 0x22D;              // uint8_t (1 byte: 1 = D presionado)
        constexpr size_t MC_INPUT_JUMP = 0x22E;           // uint8_t (1 byte: 1 = Espacio presionado)
        constexpr size_t MC_INPUT_SNEAK = 0x22F;          // uint8_t (1 byte: 1 = Shift presionado)
        constexpr size_t MC_ACTION_LEFT_CLICK = 0x230;    // uint8_t (1 byte: 1 = Ataque/Golpe con ítem de Minecraft)
    }

    enum class UseActionType : uint32_t {
        NONE = 0,
        FIRE_WEAPON = 1,
        CONSUME_FOOD = 2
    };

    enum class ThrowItemType : uint32_t {
        NONE = 0,
        SWORD = 1,
        AXE = 2,
        SHOVEL = 3,
        PICKAXE = 4,
        FOOD = 5,
        BLOCK = 6,
        DR3_WEAPON = 7
    };

    struct Quaternion {
        float x;
        float y;
        float z;
        float w;
    };

    struct BoneMatrixData {
        Quaternion head;
        Quaternion torso;
        Quaternion rightArm;
        Quaternion leftArm;
        Quaternion rightLeg;
        Quaternion leftLeg;
    };

    struct DynamicLootPackage {
        uint32_t sequence;
        uint32_t itemId;
        uint32_t durability;
        uint32_t maxDurability;
        uint32_t ammo;
        char displayName[64];
        char iconId[64];
    };
}

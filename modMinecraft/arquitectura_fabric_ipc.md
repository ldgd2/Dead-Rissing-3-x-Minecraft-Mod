# Plan de Implementación: Mod de Fabric (Cliente Java) y Puente IPC

Este documento detalla la arquitectura para el lado de Minecraft (Java) del Crossover. El objetivo es que el mod de Fabric lea y escriba en la Memoria Compartida de Windows en tiempo real, aplicando los datos al motor de renderizado y físicas de Minecraft sin causar *stuttering* ni lag.

## 1. El Puente de Memoria (JNA + sun.misc.Unsafe)

Aunque Java tiene NIO (`MappedByteBuffer`), este no soporta nativamente la lectura de Memoria Compartida Nombrada de Windows (`OpenFileMapping`). Por lo tanto, usaremos **JNA (Java Native Access)** para obtener el puntero de memoria, y luego `sun.misc.Unsafe` para leer los bytes a velocidad nativa.

### 1.1. Enlace Nativo (JNA)
Se creará una clase `Win32SharedMemory` que cargará `Kernel32.dll` para llamar a:
*   `OpenFileMappingA(FILE_MAP_ALL_ACCESS, false, "Global\\Orquestador_MC_DR3")`
*   `MapViewOfFile(...)` -> Retorna el puntero crudo (`Pointer`).

### 1.2. Lectura Lock-Free (El SeqLock en Java)
Para evitar que Java se bloquee si DR3 está escribiendo, replicaremos el `SeqLock` de C++ en Java usando `Unsafe`:
```java
// Bucle conceptual de lectura segura sin bloqueos (Lock-Free)
long seq1, seq2;
do {
    seq1 = unsafe.getIntVolatile(bufferAddress);
    // ... leer coordenadas X, Y, Z, Rotación ...
    seq2 = unsafe.getIntVolatile(bufferAddress);
} while (seq1 != seq2 || (seq1 & 1) != 0);
```
Esto garantiza latencia de submilisegundos en la lectura.

---

## 2. Puntos de Inyección (Mixins de Fabric)

Fabric utiliza Mixins (basados en SpongePowered) para inyectar código directamente en el bytecode de Minecraft. Necesitamos secuestrar sistemas clave:

### 2.1. `MixinPlayerEntityRenderer` (El Retargeting)
*   **Propósito:** Forzar los huesos de Steve para que imiten las animaciones de Nick Ramos cuando DR3 tiene la autoridad (3ra persona).
*   **Inyección:** En el método `render(...)` o `setupTransforms(...)`.
*   **Lógica:** Anular las matemáticas originales (`limbAngle`, `limbDistance`). Leer el array de cuaterniones del Ring Buffer y aplicar las rotaciones exactas a `model.rightArm`, `model.leftLeg`, etc.

### 2.2. `MixinMinecraftClient` (Tick de Renderizado - 60+ FPS)
*   **Propósito:** Sincronizar el interpolador de tiempo. Las posiciones de los zombies (Dummies) vienen a 60FPS de DR3, pero el *Tick* lógico de Minecraft es de 20 TPS.
*   **Inyección:** En el bucle principal de renderizado.
*   **Lógica:** Actualizar las posiciones visuales de los *Hitboxes* invisibles para que se muevan de forma fluida, calculando los sub-frames visuales.

### 2.3. `MixinClientPlayerEntity` y `MixinMouse` (Autoridad y Telemetría)
*   **Propósito:** Controlar el flujo de datos según el estado de la cámara (Master/Slave).
*   **Lógica en 3ra Persona:** Bloquear el input nativo de movimiento de Minecraft. Steve se mueve únicamente por las coordenadas dictadas desde DR3.
*   **Lógica en 1ra Persona:** Leer el input del ratón/teclado, aplicar físicas de bloques (gravedad, saltos) y enviar las coordenadas absolutas de Steve por IPC hacia el `AuthorityManager` de DR3.

---

## 3. Gestor del Event Bus (Acciones Discretas)

El mod mantendrá un hilo secundario ligero (o utilizará el final del `TickEvent.Client`) para vaciar los *Ring Buffers* de eventos:

*   **Lectura de Daño (DR3 -> MC):** Si DR3 envía un evento `COMBAT_DAMAGE`, Java lo lee y aplica el daño al ítem activo de Steve (reduciendo la durabilidad verde de la barra).
*   **Escritura de Construcción (MC -> DR3):** Al usar la clase nativa `BlockItem.place(...)`, el mod empaqueta el ID del bloque y la coordenada (convertida con la matriz de `SpatialTranslator`) y la escribe en el buffer `physicsChannelMCtoDR3` para que el *Object Spawner* de DR3 genere el bloque físico.

---

## 4. Estructura de Paquetes en Memoria (Structs en Java)

Al leer memoria cruda, los offsets deben ser exactos. Crearemos clases tipo `Struct` con variables estáticas que definan los bytes:

*   `OFFSET_SEQLOCK = 0x00`
*   `OFFSET_AUTHORITY_STATE = 0x04`
*   `OFFSET_PLAYER_X = 0x08`
*   `OFFSET_PLAYER_Y = 0x0C`
...
Esto permite que el mod de Fabric extraiga los *floats* e *ints* usando `unsafe.getFloat(baseAddress + OFFSET)`.

## Siguientes Pasos
1. Crear el proyecto base del mod de Fabric.
2. Implementar la conexión de JNA y la clase `Unsafe` para leer un buffer de prueba.
3. Escribir el primer Mixin para anular el movimiento de la cámara y probar la sincronización de perspectiva.

package com.crossover.player;

import com.crossover.ipc.Win32SharedMemory;
import com.crossover.player.camera.BoneRetargetingData;
import com.crossover.player.camera.CameraManager;
import com.crossover.player.health.HealthManager;
import com.crossover.player.item.ItemManager;
import com.crossover.player.item.ThrowAndUseManager;
import com.crossover.player.xp.XpManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * Gestor del Jugador y Pilar 2: El Puppet Master.
 * Controla la máquina de estados de autoridad F5 y el retargeting de huesos.
 */
public class PlayerManager {

    private final Win32SharedMemory ipc;
    
    public enum CameraState {
        FIRST_PERSON,            // Minecraft gobierna físicas (WASD/salto) y Free Camera anclada a Nick
        THIRD_PERSON,            // Dead Rising 3 gobierna físicas y cinemática de huesos
        TRANSITIONING_TO_FIRST,  // Esperando ACK de DR3
        TRANSITIONING_TO_THIRD   // Devolviendo control a DR3
    }

    private CameraState currentState = CameraState.THIRD_PERSON;

    // Sub-módulos
    public final CameraManager cameraManager;
    public final HealthManager healthManager;
    public final ItemManager itemManager;
    public final ThrowAndUseManager throwAndUseManager;
    public final XpManager xpManager;

    public PlayerManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
        this.cameraManager = new CameraManager(ipc);
        this.healthManager = new HealthManager(ipc);
        this.itemManager = new ItemManager(ipc);
        this.throwAndUseManager = new ThrowAndUseManager(ipc);
        this.xpManager = new XpManager(ipc);
    }

    /**
     * Llamado cada tick (20 TPS) desde ClientTickEvents en CrossoverMod.
     */
    public void tick(ClientPlayerEntity player) {
        if (ipc == null || !ipc.isConnected()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        // 0. Asegurar que el HUD de Minecraft (hotbar, corazones, armadura) esté SIEMPRE visible
        if (client.options.hudHidden) {
            client.options.hudHidden = false;
        }

        // 1. Alternar Menú de Inventario ('E' o ESC disparado desde Dead Rising 3)
        if (ipc.consumeInventoryToggle()) {
            client.execute(() -> {
                if (client.currentScreen == null) {
                    client.setScreen(new InventoryScreen(player));
                } else {
                    client.setScreen(null);
                }
            });
        }

        // 2. Selección de Slot de Hotbar (Teclas '1' a '9' desde Dead Rising 3)
        int slotToSelect = ipc.consumeHotbarSlotSelect();
        if (slotToSelect >= 1 && slotToSelect <= 9) {
            player.getInventory().selectedSlot = slotToSelect - 1;
        }

        // 3. Desplazamiento de Hotbar con Rueda del Ratón desde Dead Rising 3
        int scrollDelta = ipc.consumeHotbarScroll();
        if (scrollDelta != 0) {
            player.getInventory().scrollInHotbar(scrollDelta);
        }

        // 4. Actualizar estado de Interfaz Gráfica abierta hacia el Hook de DirectX 11 de DR3
        boolean isGuiOpen = (client.currentScreen != null);
        ipc.writeGuiOpen(isGuiOpen);

        // 5. Si la GUI de Minecraft está abierta, procesar coordenadas y clics de ratón reenviados desde DR3
        if (isGuiOpen && client.currentScreen != null) {
            float normX = ipc.readMouseX();
            float normY = ipc.readMouseY();
            double mouseX = normX * (double) client.getWindow().getScaledWidth();
            double mouseY = normY * (double) client.getWindow().getScaledHeight();

            if (ipc.consumeMouseLeftClick()) {
                client.execute(() -> {
                    if (client.currentScreen != null) {
                        client.currentScreen.mouseClicked(mouseX, mouseY, 0);
                        client.currentScreen.mouseReleased(mouseX, mouseY, 0);
                    }
                });
            } else if (ipc.consumeMouseRightClick()) {
                client.execute(() -> {
                    if (client.currentScreen != null) {
                        client.currentScreen.mouseClicked(mouseX, mouseY, 1);
                        client.currentScreen.mouseReleased(mouseX, mouseY, 1);
                    }
                });
            }
        }

        // 6. Sincronización de estadísticas, inventario y físicas de lanzamiento
        healthManager.syncHealth(player);
        xpManager.syncExperience(player);
        itemManager.tick(player);
        throwAndUseManager.tick(player);
        
        // Orientación de Steve perfectamente sincronizada con la cámara de DR3 en TODOS los modos (1ra y 3ra persona)
        float pitch = ipc.readCameraPitch();
        float yaw = ipc.readCameraYaw();
        player.setPitch(pitch);
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        // 7. Control de Físicas según el estado de Autoridad
        if (currentState == CameraState.THIRD_PERSON) {
            // DR3 es el Master. Steve es el esclavo visual.
            // Anclamos a Steve firmemente en el spawn (0, 65, 0) sobre la plataforma sólida de barreras
            // para que su chunk esté 100% cargado y se renderice de forma impecable en 3ra y 2da persona.
            player.setNoGravity(true);
            player.setVelocity(0, 0, 0);
            player.fallDistance = 0;
            player.setPos(0.0, 65.0, 0.0);
        } else if (currentState == CameraState.FIRST_PERSON) {
            // Minecraft es el Master (WASD + Salto transmitido desde DR3).
            boolean fwd = ipc.readInputW();
            boolean back = ipc.readInputS();
            boolean left = ipc.readInputA();
            boolean right = ipc.readInputD();
            boolean jump = ipc.readInputJump();
            boolean sneak = ipc.readInputSneak();

            player.input.pressingForward = fwd;
            player.input.pressingBack = back;
            player.input.pressingLeft = left;
            player.input.pressingRight = right;
            player.input.jumping = jump;
            player.input.sneaking = sneak;

            float movementForward = 0.0f;
            if (fwd) movementForward += 1.0f;
            if (back) movementForward -= 1.0f;
            player.input.movementForward = movementForward;

            float movementSideways = 0.0f;
            if (left) movementSideways += 1.0f;
            if (right) movementSideways -= 1.0f;
            player.input.movementSideways = movementSideways;

            // Enviamos posición de Steve para teletransportar la cápsula de Nick en DR3
            ipc.writePlayerPosition(
                (float) player.getX(), 
                (float) player.getY(), 
                (float) player.getZ()
            );
            cameraManager.syncCamera(currentState, player.getPitch(), player.getYaw(), 0.0f, 70.0f);
        }
    }

    /**
     * Invocado al presionar teclas de perspectiva (',', 'Ñ', '{') para alternar entre Primera Persona y Tercera Persona.
     */
    public void requestCameraChange(boolean toFirstPerson) {
        if (ipc == null || !ipc.isConnected()) return;

        if (toFirstPerson) {
            System.out.println("[PlayerManager] Activando Primera Persona (Minecraft Master)...");
            this.currentState = CameraState.FIRST_PERSON;
            ipc.writeAuthorityRequest(true);
        } else {
            System.out.println("[PlayerManager] Activando Tercera Persona (DR3 Master)...");
            this.currentState = CameraState.THIRD_PERSON;
            ipc.writeAuthorityRequest(false);
        }
    }

    public boolean isThirdPerson() {
        return this.currentState == CameraState.THIRD_PERSON;
    }

    public CameraState getCurrentState() {
        return this.currentState;
    }

    /**
     * Retargeting de Huesos (Pilar 2):
     * Inyecta los ángulos derivados de la cinemática directa de Dead Rising 3
     * en las 6 cajas anatómicas del modelo de Steve (Cabeza, Torso, Brazos, Piernas).
     */
    public void applyRetargetingBones(AbstractClientPlayerEntity player, PlayerEntityModel<?> model) {
        if (ipc == null || !ipc.isConnected() || !isThirdPerson()) return;

        // 1. Extraer rotaciones de huesos desde Memoria Compartida
        BoneRetargetingData bones = ipc.readBoneRetargeting();

        // Si los huesos aún no tienen datos de cinemática de DR3 (rotaciones en 0),
        // permitir que Steve use sus animaciones naturales de caminata y carrera de Minecraft
        boolean hasSkeletonData = (bones.rightArm.pitch != 0.0f || bones.rightArm.yaw != 0.0f ||
                                   bones.leftArm.pitch != 0.0f  || bones.leftArm.yaw != 0.0f  ||
                                   bones.rightLeg.pitch != 0.0f || bones.leftLeg.pitch != 0.0f);

        if (!hasSkeletonData) {
            float pitchRad = (float) Math.toRadians(player.getPitch());
            model.head.pitch = pitchRad;
            model.body.pitch = pitchRad * 0.35f;
            return;
        }

        // 2. Si hay datos reales de Mocap de Dead Rising 3, inyectamos la cinemática directa
        player.limbAnimator.setSpeed(0.0f);

        // 3. Inyectar rotaciones en los pivotes de las 6 cajas principales
        // Cabeza
        model.head.pitch = bones.head.pitch;
        model.head.yaw   = bones.head.yaw;
        model.head.roll  = bones.head.roll;

        // Torso (Espina)
        model.body.pitch = bones.torso.pitch;
        model.body.yaw   = bones.torso.yaw;
        model.body.roll  = bones.torso.roll;

        // Brazo Derecho
        model.rightArm.pitch = bones.rightArm.pitch;
        model.rightArm.yaw   = bones.rightArm.yaw;
        model.rightArm.roll  = bones.rightArm.roll;

        // Brazo Izquierdo
        model.leftArm.pitch = bones.leftArm.pitch;
        model.leftArm.yaw   = bones.leftArm.yaw;
        model.leftArm.roll  = bones.leftArm.roll;

        // Pierna Derecha
        model.rightLeg.pitch = bones.rightLeg.pitch;
        model.rightLeg.yaw   = bones.rightLeg.yaw;
        model.rightLeg.roll  = bones.rightLeg.roll;

        // Pierna Izquierda
        model.leftLeg.pitch = bones.leftLeg.pitch;
        model.leftLeg.yaw   = bones.leftLeg.yaw;
        model.leftLeg.roll  = bones.leftLeg.roll;

        // 4. Copiar transformaciones a las capas secundarias de la skin de Steve
        model.hat.copyTransform(model.head);
        model.jacket.copyTransform(model.body);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
        model.rightPants.copyTransform(model.rightLeg);
        model.leftPants.copyTransform(model.leftLeg);
    }
}

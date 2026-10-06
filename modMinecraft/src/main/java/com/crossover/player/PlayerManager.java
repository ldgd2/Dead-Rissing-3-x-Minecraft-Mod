package com.crossover.player;

import com.crossover.ipc.Win32SharedMemory;
import com.crossover.player.camera.BoneRetargetingData;
import com.crossover.player.camera.CameraManager;
import com.crossover.player.health.HealthManager;
import com.crossover.player.item.ItemManager;
import com.crossover.player.item.ThrowAndUseManager;
import com.crossover.player.xp.XpManager;
import com.crossover.combat.CombatMobManager;
import com.crossover.world.CollisionMeshManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * Gestor del Jugador y Pilar 2: El Puppet Master.
 * Controla la máquina de estados de autoridad F5, retargeting de huesos y sincronización 1:1 de posición.
 */
public class PlayerManager {

    private final Win32SharedMemory ipc;
    
    public enum CameraState {
        FIRST_PERSON,            // Minecraft gobierna físicas (WASD/salto) y Free Camera anclada a Nick
        THIRD_PERSON,            // Dead Rising 3 gobierna físicas y cinemática de huesos
        TRANSITIONING_TO_FIRST,  // Esperando ACK de DR3
        TRANSITIONING_TO_THIRD   // Devolviendo control a DR3
    }

    private CameraState currentState = CameraState.FIRST_PERSON;
    private boolean barrierPlatformInitialized = false;
    private double baseGroundY = 0.0;

    // Sub-módulos
    public final CameraManager cameraManager;
    public final HealthManager healthManager;
    public final ItemManager itemManager;
    public final ThrowAndUseManager throwAndUseManager;
    public final XpManager xpManager;
    public final CollisionMeshManager collisionMeshManager;
    public final CombatMobManager combatMobManager;

    public PlayerManager(Win32SharedMemory ipc) {
        this.ipc = ipc;
        this.cameraManager = new CameraManager(ipc);
        this.healthManager = new HealthManager(ipc);
        this.itemManager = new ItemManager(ipc);
        this.throwAndUseManager = new ThrowAndUseManager(ipc);
        this.xpManager = new XpManager(ipc);
        this.collisionMeshManager = new CollisionMeshManager();
        this.combatMobManager = new CombatMobManager(ipc);
        if (ipc != null && ipc.isConnected()) {
            ipc.writeAuthorityRequest(true);
        }
    }

    /**
     * Llamado cada tick (20 TPS) desde ClientTickEvents en CrossoverMod.
     */
    public void tick(ClientPlayerEntity player) {
        if (ipc == null || !ipc.isConnected()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        // -1. Sincronización inicial 1:1 de Steve con la posición de Nick Ramos en Los Perdidos
        // Esperar a que DR3 confirme que está activamente DENTRO DE LA PARTIDA (readAckAuthority() y isDr3NickValid())
        if (!barrierPlatformInitialized) {
            if (!ipc.readAckAuthority() || !ipc.isDr3NickValid()) {
                return; // DR3 aún está en el menú principal o cargando pantalla, esperar pacientemente
            }
            float[] dr3Pos = ipc.readDr3NickPosition();
            float dr3X = dr3Pos[0];
            float dr3Y = dr3Pos[1];
            float dr3Z = dr3Pos[2];
            if (Math.abs(dr3X) > 1.0f || Math.abs(dr3Z) > 1.0f) {
                player.setPos(dr3X, dr3Y, dr3Z);
                baseGroundY = dr3Y;
                barrierPlatformInitialized = true;
                System.out.println("[PlayerManager] Steve teletransportado 1:1 a Los Perdidos en (" + dr3X + ", " + dr3Y + ", " + dr3Z + ")");
            } else {
                return;
            }
        }

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

        // 5. Si la GUI de Minecraft está abierta, procesar coordenadas, clics de ratón y tipeo de texto desde DR3
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

            int[] typed = ipc.consumeTypedEvent();
            if (typed != null) {
                int chr = typed[0];
                int vk = typed[1];
                client.execute(() -> {
                    if (client.currentScreen != null) {
                        if (chr >= 32) {
                            client.currentScreen.charTyped((char) chr, 0);
                        }
                        if (vk != 0) {
                            int glfwKey = mapVkToGlfw(vk);
                            if (glfwKey != 0) {
                                client.currentScreen.keyPressed(glfwKey, 0, 0);
                                client.currentScreen.keyReleased(glfwKey, 0, 0);
                            }
                        }
                    }
                });
            }
        }

        // 6. Sincronización de estadísticas, inventario y físicas de lanzamiento
        healthManager.syncHealth(player);
        xpManager.syncExperience(player);
        itemManager.tick(player);
        throwAndUseManager.tick(player);
        combatMobManager.tick(player);
        
        // 6.5. Asegurar que Steve tenga el Kit Crossover en su barra de acceso rápido
        ensureCrossoverKit(player);

        // Orientación de Steve perfectamente sincronizada con la cámara de DR3 en TODOS los modos (1ra y 3ra persona)
        float pitch = ipc.readCameraPitch();
        float yaw = ipc.readCameraYaw();
        player.setPitch(pitch);
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        // 7. Sincronización continua de la posición:
        int camMode = ipc.readCameraMode();
        boolean isFirstPerson = (camMode == 1 || currentState == CameraState.FIRST_PERSON);

        // Leer inputs WASD, salto y agachado retransmitidos desde DR3
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
        player.input.movementForward = (fwd ? 1.0f : 0.0f) - (back ? 1.0f : 0.0f);
        player.input.movementSideways = (left ? 1.0f : 0.0f) - (right ? 1.0f : 0.0f);

        if (isFirstPerson) {
            // EN PRIMERA PERSONA: Steve gobierna su movimiento con físicas nativas de Minecraft (WASD, salto, inercia)
            player.setNoGravity(false);

            // Anclar cota base de asfalto cada vez que Steve toque el suelo o bloques
            if (player.isOnGround()) {
                baseGroundY = player.getY();
            }

            // Protección contra caída al vacío: mantener a Steve sobre el asfalto de Los Perdidos
            if (player.getY() < baseGroundY - 3.0 && Math.abs(baseGroundY) > 0.001) {
                player.setPos(player.getX(), baseGroundY, player.getZ());
                player.setVelocity(0, 0, 0);
                player.setOnGround(true);
            }

            // Transmitir posición real de Steve hacia Dead Rising 3 para anclar la cámara de MT Framework
            ipc.writePlayerPosition((float) player.getX(), (float) player.getY(), (float) player.getZ());

            // Actualizar Malla de Colisión bajo los pies de Steve en la cota de asfalto baseGroundY (sin efecto ascensor)
            if (player.getWorld() instanceof net.minecraft.client.world.ClientWorld clientWorld) {
                java.util.Map<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState> placed = null;
                if (com.crossover.core.CrossoverMod.worldSyncManager != null) {
                    placed = com.crossover.core.CrossoverMod.worldSyncManager.getPlayerPlacedBlocks();
                }
                int floorY = (int) Math.floor(baseGroundY) - 1;
                collisionMeshManager.update(clientWorld, player.getX(), floorY, player.getZ(), placed);
            }

            cameraManager.syncCamera(currentState, player.getPitch(), player.getYaw(), 0.0f, 70.0f);
        } else {
            // EN TERCERA PERSONA: Nick Ramos en Dead Rising 3 gobierna la posición
            float[] dr3Pos = ipc.readPlayerPositionLockFree();
            float dr3X = dr3Pos[0];
            float dr3Y = dr3Pos[1];
            float dr3Z = dr3Pos[2];

            boolean hasDr3Coord = (Math.abs(dr3X) > 0.001f || Math.abs(dr3Z) > 0.001f || Math.abs(dr3Y - 65.0f) > 0.001f);
            double targetX = hasDr3Coord ? dr3X : player.getX();
            double targetY = hasDr3Coord ? dr3Y : player.getY();
            double targetZ = hasDr3Coord ? dr3Z : player.getZ();

            double prevX = player.getX();
            double prevY = player.getY();
            double prevZ = player.getZ();
            double dx = targetX - prevX;
            double dy = targetY - prevY;
            double dz = targetZ - prevZ;

            player.setPos(targetX, targetY, targetZ);
            player.setVelocity(dx, dy, dz);
            player.setNoGravity(true);
            player.fallDistance = 0;

            if (player.getWorld() instanceof net.minecraft.client.world.ClientWorld clientWorld) {
                java.util.Map<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState> placed = null;
                if (com.crossover.core.CrossoverMod.worldSyncManager != null) {
                    placed = com.crossover.core.CrossoverMod.worldSyncManager.getPlayerPlacedBlocks();
                }
                int floorY = (int) Math.floor(targetY) - 1;
                collisionMeshManager.update(clientWorld, targetX, floorY, targetZ, placed);
            }

            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist > 0.01) {
                float animSpeed = (float) Math.min(horizontalDist * 4.0, 1.5);
                player.limbAnimator.updateLimbs(animSpeed, 0.4f);
            } else if (fwd || back || left || right) {
                float speed = sneak ? 0.3f : 0.6f;
                player.limbAnimator.updateLimbs(speed, 0.4f);
            }
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
        // Preservar la esencia clásica y animaciones fluidas naturales de Steve en Minecraft (caminata, carrera, balanceo de brazos)
        float pitchRad = (float) Math.toRadians(player.getPitch());
        model.head.pitch = pitchRad;
        model.body.pitch = pitchRad * 0.25f;
        model.hat.copyTransform(model.head);
    }

    private boolean kitGiven = false;
    private void ensureCrossoverKit(ClientPlayerEntity player) {
        if (kitGiven) return;
        boolean hasItems = false;
        for (int i = 0; i < 9; i++) {
            if (!player.getInventory().getStack(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        if (!hasItems) {
            player.getInventory().setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.COBBLESTONE, 64));
            player.getInventory().setStack(1, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIRT, 64));
            player.getInventory().setStack(2, new net.minecraft.item.ItemStack(net.minecraft.item.Items.TNT, 64));
            player.getInventory().setStack(3, new net.minecraft.item.ItemStack(net.minecraft.item.Items.FLINT_AND_STEEL, 1));
            player.getInventory().setStack(4, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD, 1));
            
            net.minecraft.item.ItemStack harmPotion = new net.minecraft.item.ItemStack(net.minecraft.item.Items.SPLASH_POTION, 16);
            net.minecraft.potion.PotionUtil.setPotion(harmPotion, net.minecraft.potion.Potions.STRONG_HARMING);
            player.getInventory().setStack(5, harmPotion);

            player.getInventory().setStack(6, new net.minecraft.item.ItemStack(net.minecraft.item.Items.OAK_PLANKS, 64));
            player.getInventory().setStack(7, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_BLOCK, 64));
            player.getInventory().setStack(8, new net.minecraft.item.ItemStack(net.minecraft.item.Items.CARVED_PUMPKIN, 4));
            kitGiven = true;
            System.out.println("[PlayerManager] Kit Crossover entregado a Steve (Cobblestone, TNT, Mechero, Espada, Pociones, Hierro).");
        }
    }

    private static int mapVkToGlfw(int vk) {
        switch (vk) {
            case 0x08: return org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
            case 0x2E: return org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
            case 0x0D: return org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
            case 0x25: return org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
            case 0x27: return org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
            case 0x26: return org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
            case 0x28: return org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
            case 0x1B: return org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
            case 0x09: return org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
            default: return 0;
        }
    }
}

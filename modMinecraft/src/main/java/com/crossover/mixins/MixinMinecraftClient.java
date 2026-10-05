package com.crossover.mixins;

import com.crossover.ui.OverlayFrameCapturer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sobre MinecraftClient para ejecución Headless e Inyección de Framebuffer (Pilar 1, 3 y 4).
 * 1. Oculta la ventana principal de GLFW en el SO si se ejecuta en modo headless (-Dcrossover.headless=true).
 * 2. Entra automáticamente al mundo "Void" para quedar a la espera de Dead Rising 3.
 * 3. Captura cada frame renderizado a 60 FPS con canal alfa y lo transfiere a Local\Crossover_Pixels.
 */
@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {

    @Shadow public abstract net.minecraft.client.util.Window getWindow();

    private static boolean hasAutoJoinedWorld = false;
    private static boolean hasHiddenWindow = false;

    /**
     * Oculta la ventana de GLFW del sistema operativo si se pasa el argumento headless.
     */
    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void checkHeadlessMode(boolean tick, CallbackInfo ci) {
        if (!hasHiddenWindow) {
            String headlessProp = System.getProperty("crossover.headless", "false");
            String hiddenProp = System.getProperty("crossover.hidden", "false");
            if ("true".equalsIgnoreCase(headlessProp) || "true".equalsIgnoreCase(hiddenProp) || "1".equals(System.getenv("CROSSOVER_HEADLESS"))) {
                long windowHandle = this.getWindow().getHandle();
                if (windowHandle != 0L) {
                    GLFW.glfwHideWindow(windowHandle);
                    hasHiddenWindow = true;
                    System.out.println("[Crossover] Ventana de Minecraft oculta con éxito (Renderizado 100% Off-Screen activo).");
                }
            }
        }
    }

    /**
     * Entrada automática al mundo "Void" al aparecer la pantalla de título y resolución autónoma de pantallas modales.
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreenAutoJoin(Screen screen, CallbackInfo ci) {
        if (screen != null) {
            System.out.println("[Crossover] setScreen llamado con pantalla: " + screen.getClass().getSimpleName());
        }

        // Saltar pantalla de bienvenida/accesibilidad si apareciera
        if (screen != null && screen.getClass().getSimpleName().contains("Accessibility")) {
            MinecraftClient client = (MinecraftClient) (Object) this;
            client.execute(() -> client.setScreen(new TitleScreen(true)));
            return;
        }

        // Auto-confirmar BackupPromptScreen (ocurre al cargar mundos generados sin backup previo)
        if (screen instanceof net.minecraft.client.gui.screen.BackupPromptScreen) {
            try {
                for (java.lang.reflect.Field f : screen.getClass().getDeclaredFields()) {
                    if (net.minecraft.client.gui.screen.BackupPromptScreen.Callback.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        net.minecraft.client.gui.screen.BackupPromptScreen.Callback cb = 
                            (net.minecraft.client.gui.screen.BackupPromptScreen.Callback) f.get(screen);
                        if (cb != null) {
                            System.out.println("[Crossover] Auto-confirmando BackupPromptScreen (cargando sin backup de forma diferida)...");
                            MinecraftClient client = (MinecraftClient) (Object) this;
                            client.execute(() -> {
                                try {
                                    cb.proceed(false, false);
                                } catch (Throwable t) {
                                    System.err.println("[Crossover] Error al proceder en BackupPromptScreen: " + t.getMessage());
                                }
                            });
                            return;
                        }
                    }
                }
            } catch (Throwable t) {
                System.err.println("[Crossover] Error auto-confirmando BackupPromptScreen: " + t.getMessage());
            }
        }

        // Auto-confirmar ConfirmScreen (ocurre con mundos experimentales o flat personalizados)
        if (screen instanceof net.minecraft.client.gui.screen.ConfirmScreen) {
            try {
                for (java.lang.reflect.Field f : screen.getClass().getDeclaredFields()) {
                    if (it.unimi.dsi.fastutil.booleans.BooleanConsumer.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        it.unimi.dsi.fastutil.booleans.BooleanConsumer cb = 
                            (it.unimi.dsi.fastutil.booleans.BooleanConsumer) f.get(screen);
                        if (cb != null) {
                            System.out.println("[Crossover] Auto-confirmando ConfirmScreen (Aceptando advertencia)...");
                            MinecraftClient client = (MinecraftClient) (Object) this;
                            client.execute(() -> {
                                try {
                                    cb.accept(true);
                                } catch (Throwable t) {
                                    System.err.println("[Crossover] Error al proceder en ConfirmScreen: " + t.getMessage());
                                }
                            });
                            return;
                        }
                    }
                }
            } catch (Throwable t) {
                System.err.println("[Crossover] Error auto-confirmando ConfirmScreen: " + t.getMessage());
            }
        }

        // Auto-reaparecer inmediatamente si aparece la pantalla de muerte
        if (screen instanceof net.minecraft.client.gui.screen.DeathScreen) {
            MinecraftClient client = (MinecraftClient) (Object) this;
            if (client.player != null) {
                System.out.println("[Crossover] Pantalla de muerte detectada -> Auto-reapareciendo inmediatamente...");
                client.execute(() -> {
                    client.player.requestRespawn();
                    client.setScreen(null);
                });
                return;
            }
        }

        if (!hasAutoJoinedWorld && screen instanceof TitleScreen) {
            String targetWorld = System.getProperty("crossover.world", "Void");
            MinecraftClient client = (MinecraftClient) (Object) this;

            System.out.println("[Crossover] Detectada pantalla principal. Iniciando auto-ingreso al mundo '" + targetWorld + "'...");
            hasAutoJoinedWorld = true;

            // Iniciar servidor integrado local con el mundo Void omitiendo confirmaciones
            client.execute(() -> {
                try {
                    Object loader = client.createIntegratedServerLoader();
                    java.lang.reflect.Method m = null;
                    // Buscar método start(Screen, String, boolean, boolean) por tipos de parámetros (independiente de ofuscación)
                    for (java.lang.reflect.Method method : loader.getClass().getDeclaredMethods()) {
                        Class<?>[] params = method.getParameterTypes();
                        if (params.length == 4 && Screen.class.isAssignableFrom(params[0]) && params[1] == String.class && params[2] == boolean.class && params[3] == boolean.class) {
                            m = method;
                            break;
                        }
                    }
                    if (m != null) {
                        m.setAccessible(true);
                        // start(Screen screen, String worldName, boolean safeMode, boolean canShowBackupPrompt)
                        m.invoke(loader, screen, targetWorld, false, false);
                        System.out.println("[Crossover] Servidor integrado iniciado directamente con canShowBackupPrompt=false.");
                    } else {
                        client.createIntegratedServerLoader().start(screen, targetWorld);
                    }
                } catch (Exception e) {
                    System.err.println("[Crossover] Advertencia al cargar mundo " + targetWorld + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    /**
     * Captura el FBO transparente al final de cada frame y lo escribe en Local\Crossover_Pixels a 60 FPS.
     * Solo envía frames cuando el jugador ya está dentro del mundo (evitando pantallas de carga en DR3).
     */
    @Inject(method = "render(Z)V", at = @At("TAIL"))
    private void onRenderFrameComplete(boolean tick, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.world != null && client.getFramebuffer() != null) {
            boolean hasOpenScreen = client.currentScreen != null;
            OverlayFrameCapturer.captureFrame(client.getFramebuffer(), hasOpenScreen);
        }
    }
}

package com.crossover.mixins;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anula el dibujado del entorno de Minecraft para convertir el juego en un Overlay (Pilar 1).
 * Suprime cielo, nubes, sol, clima, partículas ambientales y bloques del terreno de Java,
 * permitiendo que Steve, las entidades y las interfaces se dibujen sobre el Canal Alfa.
 */
@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    // 1. Apagamos el Cielo. 
    // Usamos el Descriptor exacto de Bytecode para EVITAR chocar con la versión estática de renderSky.
    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSky(CallbackInfo ci) {
        ci.cancel(); // El fondo se vuelve Alfa 0.0f (100% Transparente)
    }

    // 2. Nota: El terreno en mundo Void no tiene biomas sólidos, pero permitimos que renderLayer
    // dibuje los bloques que el jugador coloque en el mundo (madera, piedra, TNT, etc.)


    // 3. Apagamos las nubes para que no interfieran con Los Perdidos.
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void onRenderClouds(CallbackInfo ci) {
        ci.cancel();
    }

    // 4. Apagamos el clima (lluvia/nieve de Minecraft).
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void onRenderWeather(CallbackInfo ci) {
        ci.cancel();
    }
}

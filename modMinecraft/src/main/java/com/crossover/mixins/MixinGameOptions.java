package com.crossover.mixins;

import com.crossover.core.CrossoverMod;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para interceptar la tecla F5 (Perspectiva de la Cámara).
 * Convierte el switch de F5 en el interruptor de Autoridad de Físicas y Huesos (Pilar 2).
 */
@Mixin(GameOptions.class)
public class MixinGameOptions {

    @Inject(method = "setPerspective(Lnet/minecraft/client/option/Perspective;)V", at = @At("HEAD"))
    public void onSetPerspective(Perspective perspective, CallbackInfo ci) {
        if (CrossoverMod.playerManager != null) {
            boolean isFirstPerson = perspective.isFirstPerson();
            CrossoverMod.playerManager.requestCameraChange(isFirstPerson);
        }
    }
}

package com.crossover.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Previene que el cliente de Minecraft pause el bucle del juego cuando se abre el inventario ('E')
 * o cualquier otra interfaz, y suprime el fondo oscurecido para que Dead Rising 3 permanezca
 * 100% nítido y visible alrededor de la ventana de interfaz de Minecraft.
 */
@Mixin(Screen.class)
public class MixinScreen {

    @Inject(method = "shouldPause", at = @At("HEAD"), cancellable = true)
    private void onShouldPause(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void onRenderBackground(DrawContext context, CallbackInfo ci) {
        ci.cancel(); // Anula el oscurecido de pantalla al abrir inventarios o cofres
    }
}

package com.crossover.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sobre Framebuffer para forzar el borrado con Alfa 0.0f (100% transparente).
 * Garantiza que todo pixel donde no se dibuje Steve o la GUI tenga Alfa = 0.0.
 */
@Mixin(Framebuffer.class)
public abstract class MixinFramebuffer {

    @Shadow public float[] clearColor;

    @Inject(method = "setClearColor", at = @At("HEAD"), cancellable = true)
    private void onSetClearColor(float r, float g, float b, float a, CallbackInfo ci) {
        if (this.clearColor != null && this.clearColor.length >= 4) {
            this.clearColor[0] = 0.0f;
            this.clearColor[1] = 0.0f;
            this.clearColor[2] = 0.0f;
            this.clearColor[3] = 0.0f;
        }
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        ci.cancel();
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void onClearForceZeroAlpha(boolean getError, CallbackInfo ci) {
        if (this.clearColor != null && this.clearColor.length >= 4) {
            this.clearColor[0] = 0.0f;
            this.clearColor[1] = 0.0f;
            this.clearColor[2] = 0.0f;
            this.clearColor[3] = 0.0f;
        }
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
    }
}

package com.crossover.mixins;
 
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anula el renderizado de la niebla de fondo (Fog) y garantiza Alfa 0.0 absoluto en el fondo.
 */
@Mixin(BackgroundRenderer.class)
public class MixinBackgroundRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void cancelFogRender(net.minecraft.client.render.Camera camera, float tickDelta, net.minecraft.client.world.ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        RenderSystem.setShaderFogColor(0.0f, 0.0f, 0.0f, 0.0f);
        ci.cancel();
    }
}

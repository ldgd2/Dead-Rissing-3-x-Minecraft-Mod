package com.crossover.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sobre InGameHud para anular overlays a pantalla completa con Canal Alfa = 1.0 (Pilar 1).
 * Suprime la viñeta (renderVignetteOverlay), spyglass, portal y efectos de pantalla completa
 * que de forma predeterminada pintaban toda la pantalla de negro opaco con Alfa = 255,
 * garantizando que el mundo 3D de Dead Rising 3 sea 100% visible a través de los píxeles transparentes.
 */
@Mixin(InGameHud.class)
public class MixinInGameHud {

    /**
     * Anula el renderizado de la viñeta (textures/misc/vignette.png).
     * En vanilla, renderVignetteOverlay utiliza blendFuncSeparate(ZERO, ONE_MINUS_SRC_COLOR, ONE, ZERO),
     * lo cual forzaba el buffer alfa a 1.0 en toda la pantalla e impedía la visión de Dead Rising 3.
     */
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Anula overlays de pantalla completa (nieve en polvo, etc.).
     */
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Anula el overlay de catalejo (spyglass scope) si llegara a activarse.
     */
    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglassOverlay(DrawContext context, float scale, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Anula el overlay del portal del Nether para evitar tintado de pantalla.
     */
    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        ci.cancel();
    }
}

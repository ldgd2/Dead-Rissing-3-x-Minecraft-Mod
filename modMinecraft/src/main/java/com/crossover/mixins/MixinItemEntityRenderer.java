package com.crossover.mixins;

import com.crossover.player.item.ThrowAndUseManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para controlar el renderizado de ítems en el suelo/aire.
 * Si el ítem está volando con físicas Havok en Dead Rising 3, cancela el dibujado
 * del sprite flotante vanilla de Minecraft hasta que el proyectil impacte y toque el suelo.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer {

    @Inject(
        method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderItemEntity(ItemEntity itemEntity, float f, float g, MatrixStack matrixStack,
                                    VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        if (ThrowAndUseManager.isFlyingInHavok(itemEntity.getId())) {
            ci.cancel();
        }
    }
}

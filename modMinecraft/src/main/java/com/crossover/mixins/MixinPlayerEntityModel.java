package com.crossover.mixins;

import com.crossover.core.CrossoverMod;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para inyectar cinemática directa (Mocap de Dead Rising 3)
 * en el modelo de Steve tras el cálculo de ángulos de Minecraft.
 */
@Mixin(PlayerEntityModel.class)
public class MixinPlayerEntityModel {

    @Inject(
        method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
        at = @At("TAIL")
    )
    public void onSetAnglesTail(LivingEntity entity, float limbAngle, float limbDistance, 
                                float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            if (CrossoverMod.playerManager != null && CrossoverMod.playerManager.isThirdPerson()) {
                // Inyecta los cuaterniones en las 6 cajas (Cabeza, Torso, Brazos, Piernas)
                CrossoverMod.playerManager.applyRetargetingBones(player, (PlayerEntityModel<?>) (Object) this);
            }
        }
    }
}

package com.crossover.mixins;

import com.crossover.core.CrossoverMod;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepta cuando el jugador presiona la tecla Q para soltar un ítem.
 * Desvía el ítem al motor de físicas Havok de Dead Rising 3 para lanzarlo como proyectil.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"))
    private void onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        ItemStack stack = player.getMainHandStack();
        if (!stack.isEmpty() && CrossoverMod.playerManager != null && CrossoverMod.playerManager.throwAndUseManager != null) {
            CrossoverMod.playerManager.throwAndUseManager.onPlayerDropItem(player, stack.copy(), null);
        }
    }
}

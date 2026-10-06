package com.crossover.mixins;

import com.crossover.core.CrossoverMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepta todas las detonaciones de explosiones (TNT, cargas explosivas)
 * recibidas en el cliente para transmitirlas inmediatamente como una explosión masiva
 * Havok en Dead Rising 3.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    @Inject(method = "onExplosion", at = @At("HEAD"))
    private void onExplosionReceived(ExplosionS2CPacket packet, CallbackInfo ci) {
        if (CrossoverMod.worldSyncManager != null && packet != null) {
            CrossoverMod.worldSyncManager.onTntExploded(
                    packet.getX(),
                    packet.getY(),
                    packet.getZ(),
                    packet.getRadius()
            );
        }
    }
}

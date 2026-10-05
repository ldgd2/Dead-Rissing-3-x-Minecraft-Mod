package com.crossover.mixins;

import com.crossover.core.CrossoverMod;
import net.minecraft.block.Block;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pilar 5: Interceptor de Construcción (Building) y Destrucción de Bloques.
 * Detecta cuando Steve coloca o rompe bloques en el mundo y envía inmediatamente
 * las coordenadas absolutas al motor de DR3Hook para generar o destruir los
 * cubos de colisión estáticos Havok (1x1x1m).
 */
@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    /**
     * Intercepta la colocación de bloques sobre otros bloques existentes en el mundo.
     */
    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void onInteractBlockPlaced(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (cir.getReturnValue().isAccepted() && CrossoverMod.worldSyncManager != null && player != null) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
                String blockId = Registries.BLOCK.getId(block).toString();

                CrossoverMod.worldSyncManager.onBlockPlaced(
                        targetPos.getX(),
                        targetPos.getY(),
                        targetPos.getZ(),
                        blockId,
                        block.getDefaultState()
                );
            }
        }
    }

    /**
     * Permite colocar bloques en el aire / sobre el asfalto de Los Perdidos
     * haciendo clic derecho con un bloque en la mano cuando no hay bloques de MC debajo.
     */
    @Inject(method = "interactItem", at = @At("HEAD"))
    private void onInteractItemAirPlacement(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (CrossoverMod.worldSyncManager != null && player != null) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BlockItem blockItem) {
                Vec3d eyePos = player.getCameraPosVec(1.0f);
                Vec3d lookVec = player.getRotationVec(1.0f);
                Vec3d target = eyePos.add(lookVec.multiply(3.5));

                BlockPos groundPos = new BlockPos(
                        (int) Math.floor(target.x),
                        (int) Math.floor(target.y),
                        (int) Math.floor(target.z)
                );

                Block block = blockItem.getBlock();
                String blockId = Registries.BLOCK.getId(block).toString();

                // Registrar en la capa visual y notificar a DR3
                CrossoverMod.worldSyncManager.onBlockPlaced(
                        groundPos.getX(),
                        groundPos.getY(),
                        groundPos.getZ(),
                        blockId,
                        block.getDefaultState()
                );
            } else if (player instanceof ClientPlayerEntity clientPlayer) {
                if (CrossoverMod.playerManager != null && CrossoverMod.playerManager.throwAndUseManager != null) {
                    CrossoverMod.playerManager.throwAndUseManager.onUseItem(clientPlayer, stack);
                }
            }
        }
    }

    /**
     * Intercepta cuando Steve rompe un bloque en el mundo.
     */
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (CrossoverMod.worldSyncManager != null && pos != null) {
            CrossoverMod.worldSyncManager.onBlockBroken(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
            );
        }
    }
}

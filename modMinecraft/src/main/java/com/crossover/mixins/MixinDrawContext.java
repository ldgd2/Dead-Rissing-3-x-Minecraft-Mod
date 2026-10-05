package com.crossover.mixins;

import com.crossover.player.item.DR3TextureCache;
import com.crossover.player.item.ItemDeadRising;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para renderizado dinámico en el inventario (Pilar 3).
 * Cuando el ítem es un ItemDeadRising, intercepta el dibujado del modelo 3D y renderiza
 * directamente el icono 2D cargado en VRAM escalado a 16x16 píxeles.
 */
@Mixin(DrawContext.class)
public abstract class MixinDrawContext {

    @Inject(
        method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    public void onDrawItemDynamic(LivingEntity entity, World world, ItemStack stack, 
                                  int x, int y, int seed, int z, CallbackInfo ci) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemDeadRising) {
            Identifier textureId = DR3TextureCache.getOrCreateIcon(stack);
            DrawContext self = (DrawContext) (Object) this;

            // Dibuja el icono 2D original escalado perfectamente al slot de 16x16
            self.drawTexture(textureId, x, y, 0.0f, 0.0f, 16, 16, 16, 16);

            // Cancela el dibujado del modelo 3D genérico de Minecraft
            ci.cancel();
        }
    }
}

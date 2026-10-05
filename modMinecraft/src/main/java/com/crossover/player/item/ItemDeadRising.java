package com.crossover.player.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * El Ítem Universal (El Camaleón):
 * Un único ítem genérico en Java que muta de forma, textura y nombre en tiempo real
 * según las etiquetas NBT inyectadas desde la memoria de Dead Rising 3.
 */
public class ItemDeadRising extends Item {

    public static final String NBT_NAME = "Name";
    public static final String NBT_ICON = "IconTexture";
    public static final String NBT_DURABILITY = "DR3_Durability";
    public static final String NBT_MAX_DURABILITY = "DR3_MaxDurability";
    public static final String NBT_WEAPON_ID = "DR3_WeaponId";
    public static final String NBT_AMMO = "DR3_Ammo";

    public ItemDeadRising(Item.Settings settings) {
        super(settings.maxCount(1));
    }

    /**
     * Nombre instantáneo (Cero mapeos / Cero JSONs de idiomas):
     * Devuelve directamente el nombre extraído de la memoria de Capcom.
     */
    @Override
    public Text getName(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(NBT_NAME)) {
            String dr3Name = nbt.getString(NBT_NAME);
            if (!dr3Name.isEmpty()) {
                return Text.literal(dr3Name).formatted(Formatting.GOLD);
            }
        }
        return Text.literal("Objeto de Los Perdidos").formatted(Formatting.YELLOW);
    }

    /**
     * Tooltip instantáneo con estadísticas dinámicas de Dead Rising 3.
     */
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            if (nbt.contains(NBT_DURABILITY) && nbt.contains(NBT_MAX_DURABILITY)) {
                int cur = nbt.getInt(NBT_DURABILITY);
                int max = nbt.getInt(NBT_MAX_DURABILITY);
                tooltip.add(Text.literal("Condición: " + cur + " / " + max).formatted(Formatting.GRAY));
            }
            if (nbt.contains(NBT_AMMO) && nbt.getInt(NBT_AMMO) > 0) {
                tooltip.add(Text.literal("Munición: " + nbt.getInt(NBT_AMMO)).formatted(Formatting.AQUA));
            }
            if (nbt.contains(NBT_ICON)) {
                tooltip.add(Text.literal("Textura VRAM: " + nbt.getString(NBT_ICON)).formatted(Formatting.DARK_GRAY));
            }
            tooltip.add(Text.literal("Dead Rising 3 Engine Entity").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
        }
        super.appendTooltip(stack, world, tooltip, context);
    }

    /**
     * Control de la barrita verde de durabilidad en la hotbar:
     * Calcula dinámicamente el progreso según NBT_DURABILITY y NBT_MAX_DURABILITY.
     */
    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_DURABILITY) && nbt.contains(NBT_MAX_DURABILITY);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return 13;
        int cur = nbt.getInt(NBT_DURABILITY);
        int max = nbt.getInt(NBT_MAX_DURABILITY);
        if (max <= 0) return 13;
        return Math.round(13.0f * Math.max(0.0f, (float) cur / (float) max));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return 0x00FF00;
        int cur = nbt.getInt(NBT_DURABILITY);
        int max = nbt.getInt(NBT_MAX_DURABILITY);
        if (max <= 0) return 0x00FF00;
        float ratio = Math.max(0.0f, (float) cur / (float) max);
        // Gradiente estándar de verde a rojo
        return MathHelper.hsvToRgb(ratio / 3.0f, 1.0f, 1.0f);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.success(user.getStackInHand(hand));
    }
}

package com.crossover.player.item;

import com.crossover.core.CrossoverMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de Texturas Dinámicas en VRAM (Pilar 3).
 * Carga imágenes originales 2D de Dead Rising 3 directamente en tiempo de ejecución (Runtime)
 * usando NativeImageBackedTexture, evitando inflar el mod con miles de archivos estáticos.
 */
public class DR3TextureCache {

    private static final Map<String, Identifier> LOADED_TEXTURES = new ConcurrentHashMap<>();

    // Rutas de búsqueda de iconos originales de DR3
    private static final String[] SEARCH_DIRECTORIES = new String[]{
        "dr3_assets/icons/",
        "dr3_icons/",
        "tools/raw_icons/",
        System.getProperty("user.home") + "/AppData/Local/Temp/DR3_Icons/",
        "C:/Program Files (x86)/Steam/steamapps/common/Dead Rising 3/data/ui/icons/"
    };

    /**
     * Obtiene o carga en VRAM la textura 2D para el ítem activo.
     */
    public static Identifier getOrCreateIcon(ItemStack stack) {
        String iconKey = "icon_default";
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(ItemDeadRising.NBT_ICON)) {
            String val = nbt.getString(ItemDeadRising.NBT_ICON);
            if (!val.isEmpty()) {
                iconKey = val;
            }
        }

        Identifier existing = LOADED_TEXTURES.get(iconKey);
        if (existing != null) {
            return existing;
        }

        Identifier newId = loadTextureIntoVram(iconKey, nbt != null ? nbt.getString(ItemDeadRising.NBT_NAME) : iconKey);
        LOADED_TEXTURES.put(iconKey, newId);
        return newId;
    }

    private static Identifier loadTextureIntoVram(String iconName, String displayName) {
        String cleanId = iconName.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
        if (cleanId.endsWith(".png")) cleanId = cleanId.substring(0, cleanId.length() - 4);
        Identifier textureIdentifier = new Identifier(CrossoverMod.MOD_ID, "dynamic/" + cleanId);

        NativeImage image = null;

        // 1. Intentar cargar el archivo original (.png/.dds) desde disco
        for (String dir : SEARCH_DIRECTORIES) {
            File candidate = new File(dir, iconName.endsWith(".png") ? iconName : iconName + ".png");
            if (candidate.exists() && candidate.isFile()) {
                try (InputStream stream = new FileInputStream(candidate)) {
                    image = NativeImage.read(stream);
                    System.out.println("[DR3TextureCache] Textura 2D cargada desde disco: " + candidate.getAbsolutePath());
                    break;
                } catch (Exception e) {
                    System.err.println("[DR3TextureCache] Error al leer imagen de: " + candidate.getAbsolutePath());
                }
            }
        }

        // 2. Si no existe en disco, generar un lienzo dinámico procedimental (16x16)
        if (image == null) {
            image = generateProceduralIcon(displayName);
        }

        // 3. Subir el mapa de píxeles a la memoria de la tarjeta gráfica (VRAM)
        NativeImageBackedTexture dynamicTexture = new NativeImageBackedTexture(image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureIdentifier, dynamicTexture);

        return textureIdentifier;
    }

    /**
     * Generador de emergencia para evitar texturas púrpuras de error.
     * Crea un icono Pixel Art 16x16 nítido con color distintivo basado en el hash del nombre.
     */
    private static NativeImage generateProceduralIcon(String name) {
        NativeImage img = new NativeImage(16, 16, true);
        int hash = Math.abs(name.hashCode());

        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);
        if (r < 80) r += 100;
        if (g < 80) g += 100;
        if (b < 80) b += 100;

        int colorPrimary = 0xFF000000 | (b << 16) | (g << 8) | r;
        int colorAccent  = 0xFFFFFFFF;
        int colorBorder  = 0xFF202020;

        // Dibujar silueta de arma en diagonal de 16x16
        for (int i = 3; i <= 12; i++) {
            img.setColor(i, 15 - i, colorPrimary);
            img.setColor(i + 1, 15 - i, colorPrimary);
            img.setColor(i, 15 - i - 1, colorAccent);
            img.setColor(i - 1, 15 - i, colorBorder);
        }

        // Empuñadura
        img.setColor(2, 13, 0xFF8B4513);
        img.setColor(3, 14, 0xFF8B4513);
        img.setColor(1, 14, colorBorder);

        return img;
    }
}

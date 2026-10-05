package com.crossover.ui;

import com.crossover.ipc.Win32SharedMemory.Kernel32;
import com.sun.jna.Pointer;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Capturador de Framebuffer en memoria compartida a 60 FPS (Pilar 1).
 * Vuelca el frame de Minecraft con Canal Alfa (RGBA) directamente a la memoria de Windows
 * para que el Present Hook de DirectX 11 de Dead Rising 3 lo superponga en tiempo real.
 */
public class OverlayFrameCapturer {

    public static final String SHARED_FRAME_NAME = "Local\\Crossover_Pixels";
    public static final String SHARED_FRAME_NAME_FALLBACK = "Global\\Orquestador_Frame_Buffer";
    public static final int MAX_WIDTH = 1920;
    public static final int MAX_HEIGHT = 1080;
    public static final int BUFFER_SIZE = (MAX_WIDTH * MAX_HEIGHT * 4) + 64; // 8.29 MB

    private static Pointer hMapFile;
    private static Pointer pBuf;
    private static long baseAddress = 0;
    private static Unsafe unsafe;
    private static int frameCounter = 0;
    private static ByteBuffer pixelBuffer;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            pixelBuffer = ByteBuffer.allocateDirect(MAX_WIDTH * MAX_HEIGHT * 4);
        } catch (Exception e) {
            System.err.println("[OverlayFrameCapturer] Error inicializando Unsafe: " + e.getMessage());
        }
    }

    public static synchronized boolean initialize() {
        if (baseAddress != 0) return true;

        System.out.println("[OverlayFrameCapturer] Vinculando Memoria Compartida para Video: " + SHARED_FRAME_NAME);
        hMapFile = Kernel32.INSTANCE.OpenFileMappingA(Kernel32.FILE_MAP_ALL_ACCESS, false, SHARED_FRAME_NAME);
        if (hMapFile == null) {
            hMapFile = Kernel32.INSTANCE.OpenFileMappingA(Kernel32.FILE_MAP_ALL_ACCESS, false, SHARED_FRAME_NAME_FALLBACK);
        }
        if (hMapFile == null) {
            // Si el orquestador aún no lo creó, podemos crearlo o seguir en standby (0x04 = PAGE_READWRITE)
            hMapFile = Kernel32.INSTANCE.CreateFileMappingA(new Pointer(-1L), null, 0x04, 0, BUFFER_SIZE, SHARED_FRAME_NAME);
        }
        if (hMapFile == null) {
            return false;
        }

        pBuf = Kernel32.INSTANCE.MapViewOfFile(hMapFile, Kernel32.FILE_MAP_ALL_ACCESS, 0, 0, BUFFER_SIZE);
        if (pBuf == null) {
            Kernel32.INSTANCE.CloseHandle(hMapFile);
            return false;
        }

        baseAddress = Pointer.nativeValue(pBuf);
        System.out.println("[OverlayFrameCapturer] Framebuffer IPC listo en: 0x" + Long.toHexString(baseAddress));
        return true;
    }

    /**
     * Captura el framebuffer activo y lo empuja a la memoria compartida con Canal Alfa.
     * Llamado en el hook de renderizado de MinecraftClient.
     */
    public static void captureFrame(Framebuffer framebuffer, boolean hasOpenScreen) {
        if (framebuffer == null) return;
        if (baseAddress == 0) {
            if (!initialize()) return;
        }

        int width = Math.min(framebuffer.textureWidth, MAX_WIDTH);
        int height = Math.min(framebuffer.textureHeight, MAX_HEIGHT);
        if (width <= 0 || height <= 0) return;

        frameCounter++;

        // 1. Escribir Encabezado de Control
        unsafe.putInt(baseAddress + 0x00, width);
        unsafe.putInt(baseAddress + 0x04, height);
        unsafe.putInt(baseAddress + 0x08, width * 4);
        unsafe.putInt(baseAddress + 0x0C, frameCounter);
        unsafe.putInt(baseAddress + 0x10, hasOpenScreen ? 1 : 0);

        // 2. Vincular explícitamente el Framebuffer de Minecraft como READ FRAMEBUFFER
        int prevFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        pixelBuffer.clear();
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevFbo);

        // 3. Copia directa a ultra-alta velocidad en memoria compartida Local\Crossover_Pixels
        // El FBO de Minecraft contiene de forma nativa Alfa 0 en el vacío y Alfa 255 en Steve, manos, HUD y menús
        long sourceAddress = org.lwjgl.system.MemoryUtil.memAddress(pixelBuffer);
        long pixelDataDestination = baseAddress + 64;
        unsafe.copyMemory(sourceAddress, pixelDataDestination, (long) width * height * 4);
    }

    public static void shutdown() {
        if (pBuf != null) Kernel32.INSTANCE.UnmapViewOfFile(pBuf);
        if (hMapFile != null) Kernel32.INSTANCE.CloseHandle(hMapFile);
        baseAddress = 0;
    }
}

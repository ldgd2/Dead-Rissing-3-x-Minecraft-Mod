package com.crossover.player.camera;

/**
 * Representa la rotación angular en radianes (Pitch, Yaw, Roll)
 * calculada por cinemática directa a partir de los cuaterniones de DR3.
 */
public class BoneRotation {
    public final float pitch;
    public final float yaw;
    public final float roll;

    public BoneRotation(float pitch, float yaw, float roll) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    public static final BoneRotation ZERO = new BoneRotation(0.0f, 0.0f, 0.0f);
}

package com.crossover.player.camera;

/**
 * Contenedor de las rotaciones de las 6 cajas articulares principales de Steve:
 * Cabeza, Torso, Brazo Derecho, Brazo Izquierdo, Pierna Derecha, Pierna Izquierda.
 */
public class BoneRetargetingData {
    public final BoneRotation head;
    public final BoneRotation torso;
    public final BoneRotation rightArm;
    public final BoneRotation leftArm;
    public final BoneRotation rightLeg;
    public final BoneRotation leftLeg;

    public BoneRetargetingData(BoneRotation head, BoneRotation torso,
                               BoneRotation rightArm, BoneRotation leftArm,
                               BoneRotation rightLeg, BoneRotation leftLeg) {
        this.head = head != null ? head : BoneRotation.ZERO;
        this.torso = torso != null ? torso : BoneRotation.ZERO;
        this.rightArm = rightArm != null ? rightArm : BoneRotation.ZERO;
        this.leftArm = leftArm != null ? leftArm : BoneRotation.ZERO;
        this.rightLeg = rightLeg != null ? rightLeg : BoneRotation.ZERO;
        this.leftLeg = leftLeg != null ? leftLeg : BoneRotation.ZERO;
    }
}

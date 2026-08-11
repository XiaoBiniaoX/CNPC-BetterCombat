package org.cnpccombat.anim;

import net.minecraft.client.model.geom.ModelPart;

public interface HumanoidModelAccess {
    ModelPart cnpc$getHead();

    ModelPart cnpc$getHat();

    ModelPart cnpc$getBody();

    ModelPart cnpc$getLeftArm();

    ModelPart cnpc$getRightArm();

    ModelPart cnpc$getLeftLeg();

    ModelPart cnpc$getRightLeg();

    HumanoidBodyPose cnpc$getInitialBodyPose();
}

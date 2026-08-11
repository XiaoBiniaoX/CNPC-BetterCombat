package org.cnpccombat.anim;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

public final class HumanoidBodyPose {
    private final PartPose headPose;
    private final PartPose bodyPose;
    private final PartPose leftArmPose;
    private final PartPose rightArmPose;
    private final PartPose leftLegPose;
    private final PartPose rightLegPose;
    private final Scale headScale;
    private final Scale bodyScale;
    private final Scale leftArmScale;
    private final Scale rightArmScale;
    private final Scale leftLegScale;
    private final Scale rightLegScale;

    public HumanoidBodyPose(
            ModelPart head,
            ModelPart body,
            ModelPart leftArm,
            ModelPart rightArm,
            ModelPart leftLeg,
            ModelPart rightLeg
    ) {
        this.headPose = head.storePose();
        this.bodyPose = body.storePose();
        this.leftArmPose = leftArm.storePose();
        this.rightArmPose = rightArm.storePose();
        this.leftLegPose = leftLeg.storePose();
        this.rightLegPose = rightLeg.storePose();
        this.headScale = Scale.of(head);
        this.bodyScale = Scale.of(body);
        this.leftArmScale = Scale.of(leftArm);
        this.rightArmScale = Scale.of(rightArm);
        this.leftLegScale = Scale.of(leftLeg);
        this.rightLegScale = Scale.of(rightLeg);
    }

    public void apply(HumanoidModelAccess model) {
        model.cnpc$getHead().loadPose(this.headPose);
        this.headScale.applyTo(model.cnpc$getHead());
        model.cnpc$getBody().loadPose(this.bodyPose);
        this.bodyScale.applyTo(model.cnpc$getBody());
        model.cnpc$getLeftArm().loadPose(this.leftArmPose);
        model.cnpc$getRightArm().loadPose(this.rightArmPose);
        this.leftArmScale.applyTo(model.cnpc$getLeftArm());
        this.rightArmScale.applyTo(model.cnpc$getRightArm());
        model.cnpc$getLeftLeg().loadPose(this.leftLegPose);
        model.cnpc$getRightLeg().loadPose(this.rightLegPose);
        this.leftLegScale.applyTo(model.cnpc$getLeftLeg());
        this.rightLegScale.applyTo(model.cnpc$getRightLeg());
    }

    private record Scale(float x, float y, float z) {
        static Scale of(ModelPart part) {
            return new Scale(part.xScale, part.yScale, part.zScale);
        }

        void applyTo(ModelPart part) {
            part.xScale = this.x;
            part.yScale = this.y;
            part.zScale = this.z;
        }
    }
}

package org.cnpccombat.mixin.client;

import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.SetableSupplier;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.impl.IPlayerModel;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.cnpccombat.anim.FirstPersonTracker;
import org.cnpccombat.anim.HumanoidBodyPose;
import org.cnpccombat.anim.HumanoidModelAccess;
import org.cnpccombat.anim.NpcAnimator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(value = HumanoidModel.class, priority = 2000)
public abstract class HumanoidModelMixin<T extends LivingEntity> extends AgeableListModel<T>
        implements IPlayerModel, IMutableModel, HumanoidModelAccess, FirstPersonTracker {

    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart hat;

    @Unique
    private HumanoidBodyPose cnpc$initialBodyPose;

    @Unique
    private final SetableSupplier<AnimationProcessor> cnpc$emoteSupplier = new SetableSupplier<>();

    @Unique
    private boolean cnpc$firstPersonNext;

    @Shadow
    public abstract ModelPart getHead();

    @Inject(
            method = "<init>(Lnet/minecraft/client/model/geom/ModelPart;Ljava/util/function/Function;)V",
            at = @At("RETURN")
    )
    private void cnpc$initModel(ModelPart root, Function<?, ?> renderType, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerModel<?>)) {
            NpcAnimator.initializeSupplier((IMutableModel) (Object) this, this.cnpc$emoteSupplier);
        }
        this.cnpc$initialBodyPose = new HumanoidBodyPose(
                this.head, this.body, this.leftArm, this.rightArm, this.leftLeg, this.rightLeg
        );
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void cnpc$restorePivots(
            T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        if (!((Object) this instanceof PlayerModel<?>)) {
            NpcAnimator.resetToBakedPose(this);
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void cnpc$applyAnim(
            T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        if (!((Object) this instanceof PlayerModel<?>)) {
            NpcAnimator.applyToModel(this, NpcAnimator.getAnimation(entity));
        }
    }

    @Override
    public void playerAnimator_prepForFirstPersonRender() {
        this.cnpc$setFirstPersonNext(true);
    }

    @Override
    public ModelPart cnpc$getHead() {
        return this.getHead();
    }

    @Override
    public ModelPart cnpc$getHat() {
        return this.hat;
    }

    @Override
    public ModelPart cnpc$getBody() {
        return this.body;
    }

    @Override
    public ModelPart cnpc$getLeftArm() {
        return this.leftArm;
    }

    @Override
    public ModelPart cnpc$getRightArm() {
        return this.rightArm;
    }

    @Override
    public ModelPart cnpc$getLeftLeg() {
        return this.leftLeg;
    }

    @Override
    public ModelPart cnpc$getRightLeg() {
        return this.rightLeg;
    }

    @Override
    public boolean cnpc$isFirstPersonNext() {
        return this.cnpc$firstPersonNext;
    }

    @Override
    public void cnpc$setFirstPersonNext(boolean firstPersonNext) {
        this.cnpc$firstPersonNext = firstPersonNext;
    }

    @Override
    public HumanoidBodyPose cnpc$getInitialBodyPose() {
        return this.cnpc$initialBodyPose;
    }
}

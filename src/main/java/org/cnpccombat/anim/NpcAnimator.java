package org.cnpccombat.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.SetableSupplier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class NpcAnimator {
    private NpcAnimator() {
    }

    @Nullable
    public static AnimationApplier getAnimation(LivingEntity entity) {
        return entity instanceof IAnimatedPlayer animated ? animated.playerAnimator_getAnimation() : null;
    }

    public static boolean isAnimating(LivingEntity entity) {
        AnimationApplier animation = getAnimation(entity);
        return animation != null && animation.isActive();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void initializeSupplier(IMutableModel model, SetableSupplier<AnimationProcessor> supplier) {
        supplier.set(null);
        model.setEmoteSupplier(supplier);
    }

    public static void resetToBakedPose(HumanoidModelAccess model) {
        HumanoidBodyPose pose = model.cnpc$getInitialBodyPose();
        if (pose != null) {
            pose.apply(model);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends HumanoidModelAccess & FirstPersonTracker & IMutableModel> void applyToModel(
            T model,
            @Nullable AnimationApplier animation
    ) {
        SetableSupplier supplier = model.getEmoteSupplier();
        if (animation == null) {
            supplier.set(null);
            resetBends(model);
            return;
        }

        if (!model.cnpc$isFirstPersonNext() && animation.isActive()) {
            supplier.set(animation);
            animation.updatePart("head", model.cnpc$getHead());
            model.cnpc$getHat().copyFrom(model.cnpc$getHead());
            animation.updatePart("torso", model.cnpc$getBody());
            animation.updatePart("leftArm", model.cnpc$getLeftArm());
            animation.updatePart("rightArm", model.cnpc$getRightArm());
            animation.updatePart("leftLeg", model.cnpc$getLeftLeg());
            animation.updatePart("rightLeg", model.cnpc$getRightLeg());
        } else {
            model.cnpc$setFirstPersonNext(false);
            supplier.set(null);
            resetBends(model);
        }
    }

    public static void applyBodyTransform(@Nullable AnimationApplier animation, PoseStack poseStack, float partialTick) {
        if (animation == null) {
            return;
        }
        animation.setTickDelta(partialTick);
        if (!animation.isActive()) {
            return;
        }
        Vec3f position = animation.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
        poseStack.translate(position.getX().doubleValue(), position.getY().doubleValue() + 0.7D, position.getZ().doubleValue());
        Vec3f rotation = animation.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
        poseStack.mulPose(Axis.ZP.rotation(rotation.getZ().floatValue()));
        poseStack.mulPose(Axis.YP.rotation(rotation.getY().floatValue()));
        poseStack.mulPose(Axis.XP.rotation(rotation.getX().floatValue()));
        poseStack.translate(0.0D, -0.7D, 0.0D);
    }

    private static void resetBends(HumanoidModelAccess model) {
        IBendHelper.INSTANCE.bend(model.cnpc$getBody(), null);
        IBendHelper.INSTANCE.bend(model.cnpc$getLeftArm(), null);
        IBendHelper.INSTANCE.bend(model.cnpc$getRightArm(), null);
        IBendHelper.INSTANCE.bend(model.cnpc$getLeftLeg(), null);
        IBendHelper.INSTANCE.bend(model.cnpc$getRightLeg(), null);
    }
}

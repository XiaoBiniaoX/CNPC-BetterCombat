package org.cnpccombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.anim.NpcAnimator;
import org.cnpccombat.api.NpcAnimationAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityRenderer.class, priority = 2000)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void cnpc$setPartialTick(
            T entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, CallbackInfo ci
    ) {
        if (entity instanceof NpcAnimationAccess animated) {
            animated.cnpc$setRenderPartialTick(partialTick);
        }
    }

    @Inject(
            method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("RETURN")
    )
    private void cnpc$bodyTransform(
            T entity, PoseStack poseStack, float ageInTicks, float bodyYaw,
            float partialTick, CallbackInfo ci
    ) {
        if (entity instanceof EntityNPCInterface && !entity.isBaby()) {
            NpcAnimator.applyBodyTransform(NpcAnimator.getAnimation(entity), poseStack, partialTick);
        }
    }
}

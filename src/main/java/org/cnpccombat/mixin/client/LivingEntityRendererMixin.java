package org.cnpccombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.anim.NpcAnimator;
import org.cnpccombat.api.NpcAnimationAccess;
import org.cnpccombat.api.NpcYsmState;
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

    /**
     * 把 BetterCombat 动画的"整体位移/旋转"部分施加到 PoseStack。
     *
     * <p>BC 的动画分两部分：骨骼旋转给各个 {@code ModelPart}，
     * 而整体的 body 位移/旋转必须走 PoseStack（因为 humanoid 模型没有根骨骼）。
     *
     * <p><b>启用了 YSM 模型的 NPC 必须跳过</b>：YSM 模型自带根骨骼，
     * PoseStack 上的变换会作用于**整个模型**，表现为"攻击时整个上半身在摆动"。
     * YSM 侧的攻击动作由它自己的 {@code swing} 槽位负责，不需要我们插手。
     *
     * <p>这里用 {@link NpcYsmState} 判定（只读 DataDisplay 上的字符串，
     * 不引用任何 YSM 类型），所以未装 YSM 时这个 mixin 照常工作。
     */
    @Inject(
            method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("RETURN")
    )
    private void cnpc$bodyTransform(
            T entity, PoseStack poseStack, float ageInTicks, float bodyYaw,
            float partialTick, CallbackInfo ci
    ) {
        if (entity instanceof EntityNPCInterface
                && !entity.isBaby()
                && !NpcYsmState.hasYsmModel(entity)) {
            NpcAnimator.applyBodyTransform(NpcAnimator.getAnimation(entity), poseStack, partialTick);
        }
    }
}

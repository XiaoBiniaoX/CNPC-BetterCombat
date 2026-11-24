package org.cnpccombat.mixin.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.anim.NpcAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, priority = 2000)
public abstract class PlayerModelMixinNpc<T extends LivingEntity> extends HumanoidModelMixin<T> {
    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void cnpc$applyToNpcPlayerModel(
            T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        // 在 playerAnimator 的非玩家清理和 vanilla 外层复制之后应用完整姿态。
        if (entity instanceof EntityNPCInterface) {
            NpcAnimator.applyToModel(this, NpcAnimator.getAnimation(NpcAnimator.resolveRenderTarget(entity)));
        }
    }
}

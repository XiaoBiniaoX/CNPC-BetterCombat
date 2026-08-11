package org.cnpccombat.mixin.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
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
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;copyFrom(Lnet/minecraft/client/model/geom/ModelPart;)V",
                    ordinal = 0
            )
    )
    private void cnpc$applyToNpcPlayerModel(
            T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayer) && entity instanceof EntityNPCInterface) {
            NpcAnimator.applyToModel(this, NpcAnimator.getAnimation(entity));
        }
    }
}

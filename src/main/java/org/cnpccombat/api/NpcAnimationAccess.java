package org.cnpccombat.api;

import dev.kosmx.playerAnim.api.layered.AnimationStack;

public interface NpcAnimationAccess {
    AnimationStack cnpc$getAnimationStack();

    float cnpc$getRenderPartialTick();

    void cnpc$setRenderPartialTick(float partialTick);

    boolean cnpc$isAttackAnimationActive();

    boolean cnpc$isArmAnimationActive();

    void cnpc$playAttackAnimation(
            String animationId,
            boolean offHand,
            boolean twoHanded,
            float length,
            float animationUpswing,
            float damageUpswing
    );
}

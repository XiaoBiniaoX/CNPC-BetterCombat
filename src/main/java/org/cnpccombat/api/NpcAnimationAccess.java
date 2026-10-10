package org.cnpccombat.api;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import net.bettercombat.api.fx.ParticlePlacement;
import net.bettercombat.api.fx.TrailAppearance;

import java.util.List;

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

    /** 定时在伤害帧生成 BetterCombat 斩击拖尾粒子（复刻 BC 玩家侧 playAttackParticles）。 */
    void cnpc$playAttackParticles(
            boolean isOffhand,
            float weaponRange,
            int delay,
            List<ParticlePlacement> particles,
            TrailAppearance appearance
    );
}

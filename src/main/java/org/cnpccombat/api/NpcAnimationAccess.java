package org.cnpccombat.api;

import dev.kosmx.playerAnim.api.layered.AnimationStack;

public interface NpcAnimationAccess {
    AnimationStack cnpc$getAnimationStack();

    float cnpc$getRenderPartialTick();

    void cnpc$setRenderPartialTick(float partialTick);

    boolean cnpc$isAttackAnimationActive();

    boolean cnpc$isArmAnimationActive();

    /**
     * 当前正在播放的 BetterCombat 攻击动画名，没有则返回 null。
     *
     * <p>返回的是<b>不带命名空间的动画名</b>（例如
     * {@code one_handed_slash_horizontal_right}），与 YSM 的
     * {@code ctrl.bcombat_attack_animation} 约定一致 —— YSM 那个变量读的是
     * BC 动画数据里的 {@code extraData["name"]}，也是不带命名空间的。
     *
     * <p>用途：让 YSM 模型作者写的
     * {@code ctrl.set_animation(ctrl.bcombat_attack_animation)} 脚本
     * 在 NPC 上也能生效（见 {@code YsmBetterCombatBinding}）。
     */
    @org.jetbrains.annotations.Nullable
    String cnpc$getCurrentAttackAnimation();

    void cnpc$playAttackAnimation(
            String animationId,
            boolean offHand,
            boolean twoHanded,
            float length,
            float animationUpswing,
            float damageUpswing
    );
}

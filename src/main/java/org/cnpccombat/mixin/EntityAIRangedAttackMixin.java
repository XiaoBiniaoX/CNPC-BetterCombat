package org.cnpccombat.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.EntityAIRangedAttack;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.logic.NpcRangedFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 CNPC 的"瞬发"远程攻击改造成真实的拉弓 / 上弹 / 射击循环。
 *
 * <p>CNPC 原始 tick 尾部的逻辑是：冷却到了就直接
 * {@code npc.performRangedAttack(target, indirect ? 1 : 0)}，NPC 从不进入物品使用状态。
 * 这里做三件事：
 * <ol>
 *   <li>{@code tick} HEAD：推进蓄力状态机。</li>
 *   <li>{@code stop} HEAD：AI 结束时复位，避免存档残留已上弹的弩。</li>
 *   <li>{@code @Redirect} 射击调用：没蓄满就跳过，并把 CNPC 的连发计数回退一格，
 *       让下一 tick 重试而不是白丢一发。</li>
 * </ol>
 *
 * <p>投掷物属性完全由 CNPC 负责：这里只是把它自己的
 * {@code performRangedAttack} 延后到合适的帧调用，参数原样转发，
 * 所以 DataRanged 的伤害/速度/精度/效果/音效和投掷物栏物品全部生效。
 */
@Mixin(EntityAIRangedAttack.class)
public abstract class EntityAIRangedAttackMixin {
    @Shadow(remap = false)
    private EntityNPCInterface npc;

    /** CNPC 自己的连发计数器，用于蓄力未完成时回退。 */
    @Shadow(remap = false)
    private int burstCount;

    /** CNPC 自己的射击冷却计数器。 */
    @Shadow(remap = false)
    private int rangedAttackTime;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpc$driveDraw(CallbackInfo ci) {
        NpcRangedFlow.tickDraw(this.npc);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void cnpc$releaseDraw(CallbackInfo ci) {
        NpcRangedFlow.reset(this.npc);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"
            )
    )
    private void cnpc$fireWhenDrawn(EntityNPCInterface target, LivingEntity victim, float distanceFactor) {
        if (!NpcRangedFlow.readyToFire(target)) {
            // 还在蓄力：回退 CNPC 的连发计数并把冷却清零，下一 tick 原地重试。
            // 这样射速仍然完全由 CNPC 的 delay/burst 决定，不会因为蓄力丢发。
            if (this.burstCount > 0) {
                this.burstCount--;
            }
            this.rangedAttackTime = 0;
            return;
        }
        target.performRangedAttack(victim, distanceFactor);
        NpcRangedFlow.onFired(target);
    }
}

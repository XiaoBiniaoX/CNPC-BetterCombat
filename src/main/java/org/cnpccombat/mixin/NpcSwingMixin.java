package org.cnpccombat.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.api.NpcYsmState;
import org.cnpccombat.logic.NpcCombatLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抑制 vanilla 的挥手，避免它和 BetterCombat 的攻击动画抢手臂。
 *
 * <p>vanilla 的 {@code swing()} 会把 {@code swinging}/{@code swingTime} 置起来，
 * 于是 {@code HumanoidModel} 在 {@code setupAnim} 里叠加一段"抬手挥击"，
 * 而 BC 的动画也在动同一批 {@code ModelPart} → 两者打架、动作错乱。
 * 所以对走 BC 动画的 NPC 要取消掉。
 *
 * <p><b>但启用了 YSM 模型的 NPC 必须放行</b>（第十一轮修的 bug）：
 * YSM 模型不用 vanilla 的 {@code HumanoidModel} 骨骼，不存在抢手臂问题；
 * 相反，YSM 的 {@code swing} 槽位 predicate 判定的正是
 * {@code entity.swinging}。取消 vanilla swing 会导致：
 * <ul>
 *   <li>服务端不发挥手同步包（vanilla swing 内部会发
 *       {@code ClientboundAnimatePacket}）；</li>
 *   <li>客户端 {@code entity.swinging} 永远是 false；</li>
 *   <li>YSM 的 swing 槽位永不触发 → <b>NPC 攻击时连挥手动作都没有</b>。</li>
 * </ul>
 * 这就是用户报的"NPC 正常攻击但完全没有攻击动画"的直接原因。
 *
 * <p>判定用 {@link NpcYsmState}（只读 DataDisplay 上的字符串，
 * 不引用任何 YSM 类型），所以这个 mixin 在未装 YSM 时行为不变。
 * 注意这个 mixin 作用于 {@code LivingEntity}（服务端也会加载），
 * 所以更不能碰 YSM 的客户端类。
 */
@Mixin(LivingEntity.class)
public abstract class NpcSwingMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void cnpc$suppressVanillaSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if (!((Object) this instanceof EntityNPCInterface)) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        // 用 YSM 模型渲染的 NPC：放行 vanilla swing，YSM 的 swing 槽位要靠它。
        if (NpcYsmState.hasYsmModel(self)) {
            return;
        }
        Mob mob = (Mob) (Object) this;
        if (NpcCombatLogic.isEligible(mob)) {
            ci.cancel();
        }
    }
}

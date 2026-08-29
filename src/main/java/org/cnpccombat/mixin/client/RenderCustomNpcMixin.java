package org.cnpccombat.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.cnpccombat.logic.NpcRangedFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 补上 CNPC 渲染器缺失的弩姿态，并修掉双手武器时副手姿态的问题。
 *
 * <p>CNPC 的 {@code RenderCustomNpc.getPose} 只处理 EMPTY / ITEM / BLOCK /
 * BOW_AND_ARROW，完全没有 {@code CROSSBOW_CHARGE} 和 {@code CROSSBOW_HOLD} 分支
 * （vanilla {@code PlayerRenderer.getArmPose} 是有的）。而且它也没有 vanilla
 * "主手是双手姿态时副手强制 EMPTY" 的处理，会导致副手手臂穿模。
 *
 * <p>弓不需要在这里补：CNPC 原有的 {@code getUseItemRemainingTicks() > 0 && UseAnim.BOW
 * -> BOW_AND_ARROW} 分支在我们调用 {@code startUsingItem} 之后自然就生效了。
 *
 * <p>注意 {@code getPose} 会被调用两次（主手 -> rightArmPose，副手 -> leftArmPose），
 * 签名本身分不出是哪只手。靠 ItemStack 引用相等来判断：CNPC 的
 * {@code getMainHandItem()} 每次返回的是 weapons 表里同一个 ItemStack 实例。
 */
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class RenderCustomNpcMixin {
    @Inject(method = "getPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$crossbowPose(
            EntityCustomNpc npc,
            ItemStack item,
            CallbackInfoReturnable<HumanoidModel.ArmPose> cir
    ) {
        if (npc == null || item == null || item.isEmpty()) {
            return;
        }
        ItemStack mainHand = npc.getMainHandItem();
        boolean isMainHandCall = item == mainHand;

        if (isMainHandCall) {
            HumanoidModel.ArmPose pose = cnpc$posefor(npc, item);
            if (pose != null) {
                cir.setReturnValue(pose);
            }
            return;
        }

        // 副手调用：主手若是双手姿态（拉弓/上弩/持弩待发），副手必须 EMPTY，
        // 否则两只手臂会各自摆到不同位置。这是 vanilla PlayerRenderer 的行为。
        HumanoidModel.ArmPose mainPose = cnpc$posefor(npc, mainHand);
        if (mainPose == null && NpcRangedFlow.isBow(mainHand) && npc.getUseItemRemainingTicks() > 0) {
            mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        if (mainPose != null && mainPose.isTwoHanded()) {
            cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
        }
    }

    /** 返回 null 表示"不接管，交回 CNPC 原逻辑"。 */
    private static HumanoidModel.ArmPose cnpc$posefor(EntityCustomNpc npc, ItemStack stack) {
        if (!NpcRangedFlow.isCrossbow(stack)) {
            return null;
        }
        if (npc.getUseItemRemainingTicks() > 0) {
            // 正在上弹。
            return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
        }
        if (net.minecraft.world.item.CrossbowItem.isCharged(stack) && !npc.swinging) {
            // 已上弹待发。挥击中不接管，否则会和攻击动画抢手臂。
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return null;
    }
}

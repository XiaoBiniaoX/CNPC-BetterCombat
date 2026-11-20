package org.cnpccombat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.cnpccombat.api.NpcAnimationAccess;
import org.cnpccombat.api.NpcYsmState;
import org.cnpccombat.compat.bcp.BcParticleClientBridge;
import org.cnpccombat.compat.bcp.BcParticleCompat;
import org.cnpccombat.compat.ysm.YsmCompat;
import org.cnpccombat.logic.AnimationGroupRegistry;
import org.cnpccombat.network.AnimGroupListPayload;
import org.cnpccombat.network.NpcAttackPayload;
import org.cnpccombat.network.NpcBcParticlePayload;

@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void apply(NpcAttackPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(payload.entityId);
        if (entity instanceof NpcAnimationAccess animated) {
            animated.cnpc$playAttackAnimation(
                    payload.animationId,
                    payload.offHand,
                    payload.twoHanded,
                    payload.length,
                    payload.animationUpswing,
                    payload.damageUpswing
            );
            if (YsmCompat.isLoaded() && entity instanceof LivingEntity npc && NpcYsmState.hasYsmModel(npc)) {
                // YSM 玩家侧在 BC 起手时重置挥击时钟，并使用本次攻击的手。
                npc.swingingArm = payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                npc.swingTime = -1;
                npc.swinging = true;
            }
        }
    }

    /**
     * 收到服务端下发的攻击动画组列表 + 持握姿态表。
     * 单机时客户端与服务端同 JVM，注册表已经填好了；这里再赋一次也无害（内容相同）。
     */
    public static void applyAnimGroups(AnimGroupListPayload payload) {
        AnimationGroupRegistry.acceptFromServer(payload.groupIds, payload.poses, payload.twoHanded);
    }

    /** 装了 bc_particle 时，用它的粒子数据渲染 NPC 刀光。 */
    public static void applyBcParticle(NpcBcParticlePayload payload) {
        if (!BcParticleCompat.isLoaded()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(payload.entityId);
        if (entity instanceof LivingEntity attacker) {
            BcParticleClientBridge.spawn(minecraft.level, attacker, payload.animationId,
                    payload.offHand, payload.range);
        }
    }
}

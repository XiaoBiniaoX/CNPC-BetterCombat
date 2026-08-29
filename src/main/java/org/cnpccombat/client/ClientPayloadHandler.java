package org.cnpccombat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.cnpccombat.api.NpcAnimationAccess;
import org.cnpccombat.logic.AnimationGroupRegistry;
import org.cnpccombat.network.AnimGroupListPayload;
import org.cnpccombat.network.NpcAttackPayload;

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
        }
    }

    /**
     * 收到服务端下发的攻击动画组列表。
     * 单机时客户端与服务端同 JVM，注册表已经填好了；这里再赋一次也无害（内容相同）。
     */
    public static void applyAnimGroups(AnimGroupListPayload payload) {
        AnimationGroupRegistry.acceptFromServer(payload.groupIds);
    }
}

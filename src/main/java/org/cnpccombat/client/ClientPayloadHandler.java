package org.cnpccombat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.cnpccombat.api.NpcAnimationAccess;
import org.cnpccombat.network.NpcAttackPayload;

@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleNpcAttack(NpcAttackPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(payload.entityId());
        if (entity instanceof NpcAnimationAccess animated) {
            animated.cnpc$playAttackAnimation(
                    payload.animationId(),
                    payload.offHand(),
                    payload.twoHanded(),
                    payload.length(),
                    payload.animationUpswing(),
                    payload.damageUpswing()
            );
        }
    }
}

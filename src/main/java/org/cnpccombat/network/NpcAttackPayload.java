package org.cnpccombat.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 服务端 -> 客户端 的攻击动画包。
 * 双端通用（不含客户端专用类）；实际动画播放仅在客户端进行。
 */
public final class NpcAttackPayload {
    public final int entityId;
    public final String animationId;
    public final boolean offHand;
    public final boolean twoHanded;
    public final float length;
    public final float animationUpswing;
    public final float damageUpswing;

    public NpcAttackPayload(
            int entityId,
            String animationId,
            boolean offHand,
            boolean twoHanded,
            float length,
            float animationUpswing,
            float damageUpswing
    ) {
        this.entityId = entityId;
        this.animationId = animationId;
        this.offHand = offHand;
        this.twoHanded = twoHanded;
        this.length = length;
        this.animationUpswing = animationUpswing;
        this.damageUpswing = damageUpswing;
    }

    public static void encode(NpcAttackPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.animationId, 256);
        buf.writeBoolean(msg.offHand);
        buf.writeBoolean(msg.twoHanded);
        buf.writeFloat(msg.length);
        buf.writeFloat(msg.animationUpswing);
        buf.writeFloat(msg.damageUpswing);
    }

    public static NpcAttackPayload decode(FriendlyByteBuf buf) {
        return new NpcAttackPayload(
                buf.readVarInt(),
                buf.readUtf(256),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat()
        );
    }
}

package org.cnpccombat.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 服务端 -> 客户端 的 NPC 刀光请求包（仅在安装 bc_particle 时使用）。
 *
 * <p>只带"谁、什么动画、哪只手、多长攻击距离"；动画 → 粒子的映射由客户端
 * bc_particle 的 {@code ParticleSettingsLoader} 解析，因而能自动跟随
 * Malfu Combat Animation 等资源包对 {@code particle_settings.json} 的覆盖。
 */
public final class NpcBcParticlePayload {
    public final int entityId;
    public final String animationId;
    public final boolean offHand;
    public final float range;

    public NpcBcParticlePayload(int entityId, String animationId, boolean offHand, float range) {
        this.entityId = entityId;
        this.animationId = animationId;
        this.offHand = offHand;
        this.range = range;
    }

    public static void encode(NpcBcParticlePayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.animationId, 256);
        buf.writeBoolean(msg.offHand);
        buf.writeFloat(msg.range);
    }

    public static NpcBcParticlePayload decode(FriendlyByteBuf buf) {
        return new NpcBcParticlePayload(
                buf.readVarInt(),
                buf.readUtf(256),
                buf.readBoolean(),
                buf.readFloat()
        );
    }
}

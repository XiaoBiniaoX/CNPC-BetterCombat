package org.cnpccombat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.client.ClientPayloadHandler;

public record NpcAttackPayload(
        int entityId,
        String animationId,
        boolean offHand,
        boolean twoHanded,
        float length,
        float animationUpswing,
        float damageUpswing
) implements CustomPacketPayload {
    public static final Type<NpcAttackPayload> TYPE = new Type<>(CnpcCombat.id("npc_attack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcAttackPayload> STREAM_CODEC =
            StreamCodec.ofMember(NpcAttackPayload::write, NpcAttackPayload::decode);

    private static NpcAttackPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcAttackPayload(
                buffer.readVarInt(),
                buffer.readUtf(256),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeUtf(animationId, 256);
        buffer.writeBoolean(offHand);
        buffer.writeBoolean(twoHanded);
        buffer.writeFloat(length);
        buffer.writeFloat(animationUpswing);
        buffer.writeFloat(damageUpswing);
    }

    public static void handle(NpcAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadHandler.handleNpcAttack(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

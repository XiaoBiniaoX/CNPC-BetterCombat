package org.cnpccombat.network;

import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CnpcNetwork {
    private CnpcNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(NpcAttackPayload.TYPE, NpcAttackPayload.STREAM_CODEC, NpcAttackPayload::handle);
    }

    public static void sendAttackAnimation(
            Mob mob,
            String animationId,
            boolean offHand,
            boolean twoHanded,
            float length,
            float animationUpswing,
            float damageUpswing
    ) {
        PacketDistributor.sendToPlayersTrackingEntity(
                mob,
                new NpcAttackPayload(
                        mob.getId(),
                        animationId,
                        offHand,
                        twoHanded,
                        length,
                        animationUpswing,
                        damageUpswing
                )
        );
    }
}

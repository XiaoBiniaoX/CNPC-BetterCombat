package org.cnpccombat.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.client.ClientPayloadHandler;
import org.cnpccombat.logic.AnimationGroupRegistry;

public final class CnpcNetwork {
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CnpcCombat.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private CnpcNetwork() {
    }

    public static void init() {
        CHANNEL.messageBuilder(NpcAttackPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NpcAttackPayload::encode)
                .decoder(NpcAttackPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPayloadHandler.apply(msg));
                        }
                    });
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(AnimGroupListPayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnimGroupListPayload::encode)
                .decoder(AnimGroupListPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPayloadHandler.applyAnimGroups(msg));
                        }
                    });
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    public static ResourceLocation channelId() {
        return CnpcCombat.id("main");
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
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                new NpcAttackPayload(mob.getId(), animationId, offHand, twoHanded, length, animationUpswing, damageUpswing)
        );
    }

    /** 玩家进服时把动画组列表发给他，GUI 才有可选项。 */
    public static void sendAnimGroups(ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new AnimGroupListPayload(AnimationGroupRegistry.exportIds())
        );
    }

    /** 数据包重载后广播给所有在线玩家。 */
    public static void broadcastAnimGroups() {
        CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new AnimGroupListPayload(AnimationGroupRegistry.exportIds())
        );
    }
}

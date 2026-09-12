package org.cnpccombat.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.cnpccombat.logic.AnimationGroupRegistry;

public final class CnpcNetwork {
    private CnpcNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // 协议版本从 "1" 提到 "2"：本轮新增了 AnimGroupListPayload。
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(NpcAttackPayload.TYPE, NpcAttackPayload.STREAM_CODEC, NpcAttackPayload::handle);
        registrar.playToClient(AnimGroupListPayload.TYPE, AnimGroupListPayload.STREAM_CODEC, AnimGroupListPayload::handle);
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

    /** 玩家进服时把动画组列表发给他，GUI 才有可选项、持握姿态才算得出来。 */
    public static void sendAnimGroups(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, newAnimGroupPayload());
    }

    /** 数据包重载后广播给所有在线玩家。 */
    public static void broadcastAnimGroups() {
        PacketDistributor.sendToAllPlayers(newAnimGroupPayload());
    }

    /**
     * 组装动画组同步包：id 列表（GUI 用）+ pose 表（客户端渲染持握姿态用）。
     * 两个发送点共用，避免以后加字段时漏改一处。
     */
    private static AnimGroupListPayload newAnimGroupPayload() {
        return new AnimGroupListPayload(
                AnimationGroupRegistry.exportIds(),
                AnimationGroupRegistry.exportPoses(),
                AnimationGroupRegistry.exportTwoHanded()
        );
    }
}

package org.cnpccombat;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.cnpccombat.logic.AnimationGroupRegistry;
import org.cnpccombat.network.CnpcNetwork;
import org.slf4j.Logger;

@Mod(CnpcCombat.MOD_ID)
public final class CnpcCombat {
    public static final String MOD_ID = "cnpccombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcCombat(IEventBus modBus) {
        modBus.addListener(CnpcNetwork::registerPayloads);

        // 服务端事件走 NeoForge 游戏总线。这里只注册服务端侧监听，不触碰任何客户端类。
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(CnpcCombat::onServerStarted);
        gameBus.addListener(CnpcCombat::onDatapackSync);

        LOGGER.info("CNPC BetterCombat loaded");
    }

    /**
     * 服务器启动完成后扫描攻击动画组。
     * 放在 ServerStartedEvent 而非 AddReloadListener，是为了保证 BetterCombat 自己的
     * {@code loadAttributes} 已经跑过（它也挂在服务器启动阶段），
     * 且此时 ResourceManager 里的数据包已完全就绪。
     */
    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            AnimationGroupRegistry.reload(server.getResourceManager());
        } catch (Throwable t) {
            // 扫描失败不能拖垮服务器启动 —— 最坏结果只是 GUI 里没有可选项。
            LOGGER.error("Failed to scan Better Combat weapon_attributes", t);
        }
    }

    /**
     * 玩家进服 / {@code /reload} 之后：重扫并把列表发给客户端。
     * {@code OnDatapackSyncEvent} 在这两种情况下都会触发，player 为 null 表示 reload 广播。
     */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();

        if (player == null) {
            // /reload：数据包可能变了，重扫后广播。
            MinecraftServer server = event.getPlayerList().getServer();
            try {
                AnimationGroupRegistry.reload(server.getResourceManager());
            } catch (Throwable t) {
                LOGGER.error("Failed to reload Better Combat weapon_attributes", t);
            }
            CnpcNetwork.broadcastAnimGroups();
            return;
        }

        CnpcNetwork.sendAnimGroups(player);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

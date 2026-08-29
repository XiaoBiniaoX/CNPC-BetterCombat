package org.cnpccombat;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.cnpccombat.logic.AnimationGroupRegistry;
import org.cnpccombat.network.CnpcNetwork;
import org.slf4j.Logger;

@Mod(CnpcCombat.MOD_ID)
public final class CnpcCombat {
    public static final String MOD_ID = "cnpccombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcCombat() {
        // Forge 1.20.1: @Mod 主类必须提供无参构造，通过 FMLJavaModLoadingContext 获取 mod 事件总线。
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(CnpcCombat::onCommonSetup);

        // 服务端事件走 Forge 主总线。这里只注册服务端侧监听，不触碰任何客户端类。
        IEventBus forgeBus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        forgeBus.addListener(CnpcCombat::onServerStarted);
        forgeBus.addListener(CnpcCombat::onDatapackSync);

        // 启动横幅不打日志（用户要求只保留 ERROR 级）。
        // Forge 自己会在 mod 列表里显示本 mod，无需重复告知。
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CnpcNetwork::init);
    }

    /**
     * 服务器启动完成后扫描攻击动画组。
     * 放在 ServerStartedEvent 而非 AddReloadListener，是为了保证 BetterCombat 自己的
     * {@code loadAttributes} 已经跑过（它也挂在 ServerStartedEvent），
     * 且此时 ResourceManager 里的数据包已完全就绪。
     */
    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            AnimationGroupRegistry.reload(server.getResourceManager());
        } catch (Throwable t) {
            // 扫描失败不能拖垮服务器启动 —— 最坏结果只是 GUI 里没有可选项。
            LOGGER.error("扫描 BetterCombat 攻击动画组失败", t);
        }
    }

    /**
     * 玩家进服 / {@code /reload} 之后：重扫并把列表发给客户端。
     * {@code OnDatapackSyncEvent} 在这两种情况下都会触发，player 为 null 表示 reload 广播。
     */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        ServerPlayer player = event.getPlayer();

        if (player == null) {
            // /reload：数据包可能变了，重扫后广播。
            try {
                AnimationGroupRegistry.reload(server.getResourceManager());
            } catch (Throwable t) {
                LOGGER.error("重载 BetterCombat 攻击动画组失败", t);
            }
            CnpcNetwork.broadcastAnimGroups();
            return;
        }

        CnpcNetwork.sendAnimGroups(player);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}

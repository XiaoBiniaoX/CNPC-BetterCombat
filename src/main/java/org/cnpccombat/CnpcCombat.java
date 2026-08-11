package org.cnpccombat;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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
        LOGGER.info("CNPC BetterCombat loaded");
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CnpcNetwork::init);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}

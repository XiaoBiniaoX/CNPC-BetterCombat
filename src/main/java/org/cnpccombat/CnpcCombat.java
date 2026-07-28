package org.cnpccombat;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.cnpccombat.network.CnpcNetwork;
import org.slf4j.Logger;

@Mod(CnpcCombat.MOD_ID)
public final class CnpcCombat {
    public static final String MOD_ID = "cnpccombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcCombat(IEventBus modBus) {
        modBus.addListener(CnpcNetwork::registerPayloads);
        LOGGER.info("CNPC BetterCombat loaded");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

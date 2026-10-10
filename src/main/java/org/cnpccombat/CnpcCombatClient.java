package org.cnpccombat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端专用入口。Mods 列表里 Config 按钮亮起的条件是本 mod 注册了
 * {@link IConfigScreenFactory} 扩展点 —— 不注册则按钮置灰。
 * 参考 CNPCplus-Fix 的同款实现。
 */
@Mod(value = CnpcCombat.MOD_ID, dist = Dist.CLIENT)
public final class CnpcCombatClient {

    public CnpcCombatClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}

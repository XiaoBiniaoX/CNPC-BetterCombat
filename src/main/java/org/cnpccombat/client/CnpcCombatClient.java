package org.cnpccombat.client;

import net.bettercombat.api.client.BetterCombatClientEvents;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.client.gui.CnpcCombatConfigScreen;
import org.cnpccombat.client.particle.SlashParticle;
import org.cnpccombat.compat.bcp.BcParticleCompat;
import org.cnpccombat.particle.CnpcCombatParticles;
import org.cnpccombat.particle.SlashParticleEffect;
import org.cnpccombat.particle.SlashTrailUtil;

/**
 * 仅客户端：刀光粒子 Provider 注册 + 玩家命中时的本地尾迹触发。
 * NPC 的尾迹走服务端（{@code NpcCombatLogic#performAttack}），此文件只管玩家侧。
 */
@Mod.EventBusSubscriber(modid = CnpcCombat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CnpcCombatClient {
    private CnpcCombatClient() {
    }

    /** 12 个刀光类型都按 SpriteSet 注册（贴图是多帧序列，粒子内按 age 切帧）。 */
    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        for (String name : CnpcCombatParticles.NAMES) {
            event.registerSpriteSet(CnpcCombatParticles.TYPES.get(name), sprites ->
                    (ParticleProvider<SlashParticleEffect>) (options, level, x, y, z, xs, ys, zs) ->
                            new SlashParticle(level, x, y, z, options, sprites));
        }
    }

    /** 本地玩家命中目标时生成刀光（与 BC 原版一致，挥击瞬间触发，挥空不触发）。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 装了「更好的战斗粒子」时，玩家侧完全交给它渲染，避免同一刀光画两次。
            if (BcParticleCompat.isLoaded()) {
                return;
            }
            BetterCombatClientEvents.ATTACK_HIT.register((player, hand, targets, cursorTarget) ->
                    SlashTrailUtil.spawnClient(player.level(), player, hand.isOffHand(),
                            (float) hand.attributes().attackRange(),
                            hand.attack().animation(), hand.itemStack()));
        });
    }

    /**
     * 注册 Mod 列表里的 Config 屏幕（对齐 MouseTweaks 的做法）。
     * 必须在 mod 构造期调用；用自建 Screen，不硬依赖 Cloth Config。
     */
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new CnpcCombatConfigScreen(parent)));
    }
}

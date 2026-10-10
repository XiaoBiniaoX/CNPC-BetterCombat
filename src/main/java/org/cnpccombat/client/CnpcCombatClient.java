package org.cnpccombat.client;

import net.bettercombat.api.client.BetterCombatClientEvents;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.client.particle.SlashParticle;
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
        event.enqueueWork(() -> BetterCombatClientEvents.ATTACK_HIT.register((player, hand, targets, cursorTarget) ->
                SlashTrailUtil.spawnClient(player.level(), player, hand.isOffHand(),
                        (float) hand.attributes().attackRange(),
                        hand.attack().animation(), hand.itemStack())));
    }
}

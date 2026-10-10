package org.cnpccombat.particle;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegisterEvent;
import org.cnpccombat.CnpcCombat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 12 个刀光粒子类型注册（移植自 BC 1.21.1 的 BetterCombatParticles）。
 * 纹理与 particles/*.json 从 BC 拷贝到本 mod 命名空间 {@code cnpccombat}。
 *
 * <p>注册时机：{@link CnpcCombat} 构造函数里订阅的 {@link RegisterEvent}（构造期注册表已锁）。
 */
public final class CnpcCombatParticles {
    /** 顺序即注册顺序，客户端工厂注册也按此遍历。 */
    public static final List<String> NAMES = List.of(
            "topslash45", "topslash90", "topslash180", "topslash270", "topslash360", "topstab",
            "botslash45", "botslash90", "botslash180", "botslash270", "botslash360", "botstab");

    /** name -> 已注册的粒子类型；客户端注册 Provider 时读取。 */
    public static final Map<String, ParticleType<SlashParticleEffect>> TYPES = new LinkedHashMap<>();

    private CnpcCombatParticles() {
    }

    /**
     * 必须走 Forge 的 RegisterEvent：mod 构造期原版注册表已锁定，
     * 直接 {@code Registry.register} 会抛 "Can not register to a locked registry"。
     * 该事件在每个注册表注册期分发，此刻注册表处于解锁状态。
     */
    public static void onRegister(RegisterEvent event) {
        if (!Registries.PARTICLE_TYPE.equals(event.getRegistryKey())) {
            return;
        }
        for (String name : NAMES) {
            ParticleType<SlashParticleEffect> type = new SlashType();
            event.register(Registries.PARTICLE_TYPE, new ResourceLocation(CnpcCombat.MOD_ID, name), () -> type);
            TYPES.put(name, type);
        }
    }

    /**
     * 1.20.1 的 ParticleType 构造只收 Deserializer；codec() 是独立的抽象方法，
     * 每次调用时可以用 {@code this} 构造 codec，不存在 1.21.1 那种先有鸡还是先有蛋的问题。
     */
    private static final class SlashType extends ParticleType<SlashParticleEffect> {
        SlashType() {
            super(false, SlashParticleEffect.DESERIALIZER);
        }

        @Override
        public Codec<SlashParticleEffect> codec() {
            return SlashParticleEffect.createCodec(this);
        }
    }
}

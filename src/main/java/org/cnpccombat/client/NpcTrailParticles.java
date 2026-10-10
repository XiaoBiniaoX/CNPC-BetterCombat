package org.cnpccombat.client;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.fx.ConditionalTrailAppearance;
import net.bettercombat.api.fx.ParticlePlacement;
import net.bettercombat.api.fx.TrailAppearance;
import net.bettercombat.client.particle.TrailParticles;
import net.bettercombat.config.TrailConfig;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.particle.SlashParticleEffect;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.cnpccombat.api.NpcAnimationAccess;
import org.cnpccombat.logic.NpcCombatMath;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * NPC 的 BetterCombat 斩击拖尾粒子（客户端本地生成）。
 *
 * <p>复刻 BC 玩家侧 {@code SlashParticleUtil.spawnParticles} 的几何与外观逻辑，
 * 实体参数从 {@code AbstractClientPlayerEntity} 换成 {@link Mob}。
 * 触发链：服务端 NpcAttackPayload -> ClientPayloadHandler -> 本类解析
 * placements/appearance/weaponRange -> mixin 定时在伤害帧生成。
 *
 * <p>BC 客户端会吞掉原版 SWEEP_ATTACK 粒子包（ClientPlayNetworkHandlerMixin），
 * 所以原版粒子走不通；BC 玩家自己看到的也是这套 SlashParticleEffect 拖尾。
 */
public final class NpcTrailParticles {

    private NpcTrailParticles() {
    }

    /** 解析攻击参数并排期；实际生成在 mixin 的 tick 里按 delay 到点触发。 */
    public static void schedule(Mob npc, boolean offHand, String animationId, float length, float damageUpswing) {
        if (!isShowingWeaponTrails()) {
            return;
        }
        ItemStack stack = offHand ? npc.getOffhandItem() : npc.getMainHandItem();
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
        WeaponAttributes.Attack match = matchAttack(attributes, animationId);
        if (attributes == null && match == null) {
            return;
        }

        List<ParticlePlacement> placements = placements(match, animationId);
        if (placements.isEmpty()) {
            return;
        }
        TrailAppearance appearance = appearance(attributes, stack);
        if (appearance == null) {
            return;
        }
        float weaponRange = match != null
                ? (float) NpcCombatMath.attackRange(attributes, match)
                : 2.5F;
        int delay = Math.max(1, Math.round(length * Math.max(0.01F, damageUpswing)));
        ((NpcAnimationAccess) npc).cnpc$playAttackParticles(offHand, weaponRange, delay, placements, appearance);
    }

    /** 与 SlashParticleUtil.spawnParticles 同几何；entity 侧改用 Mob。 */
    public static void spawn(Mob entity, boolean isOffhand, float weaponRange, List<ParticlePlacement> settingsList, TrailAppearance appearance) {
        if (settingsList == null || settingsList.isEmpty() || appearance == null) {
            return;
        }
        boolean isLeftHanded = entity.isLeftHanded();
        boolean mirror = isOffhand;
        if (isLeftHanded) {
            mirror = !mirror;
        }
        weaponRange += 0.25F; // BC: weaponRange -= -0.25F
        for (ParticlePlacement settings : settingsList) {
            List<TrailParticles.Entry> trails = TrailParticles.ENTRIES.get(settings.particle_type());
            if (trails == null) {
                continue;
            }
            float offhandRoll = mirror ? 180.0F : 0.0F;
            float offhandFlip = mirror ? -1.0F : 1.0F;
            float yaw = entity.getYRot();
            float pitch = entity.getXRot();
            Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F).normalize();
            Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();
            double baseY = entity.getEyeY() - 0.25 + settings.y_addition();
            Vec3 finalPosition = new Vec3(entity.getX(), baseY, entity.getZ())
                    .add(forward.scale(settings.z_addition()))
                    .add(right.scale(settings.x_addition() * offhandFlip));
            Vec3 stabFinalPosition = finalPosition.add(forward.scale(weaponRange - 1.5));

            for (TrailParticles.Entry trail : trails) {
                Vec3 usePosition = trail.stabPosition() ? stabFinalPosition : finalPosition;
                for (TrailParticles.LayeredParticle layeredParticle : trail.particles()) {
                    if (appearance.primary != null) {
                        entity.level().addParticle(new SlashParticleEffect(
                                        layeredParticle.bottom(), weaponRange,
                                        pitch + settings.pitch_addition(), yaw,
                                        settings.local_yaw() * offhandFlip,
                                        (settings.roll_set() + trail.rollOffset() + offhandRoll) * offhandFlip,
                                        appearance.primary.glows(), appearance.primary.color_rgba()),
                                usePosition.x, usePosition.y, usePosition.z, 0.0, 0.0, 0.0);
                    }
                    if (appearance.secondary != null) {
                        entity.level().addParticle(new SlashParticleEffect(
                                        layeredParticle.top(), weaponRange,
                                        pitch + settings.pitch_addition(), yaw,
                                        settings.local_yaw() * offhandFlip,
                                        (settings.roll_set() + trail.rollOffset() + offhandRoll) * offhandFlip,
                                        appearance.secondary.glows(), appearance.secondary.color_rgba()),
                                usePosition.x, usePosition.y, usePosition.z, 0.0, 0.0, 0.0);
                    }
                }
            }
        }
    }

    @Nullable
    private static WeaponAttributes.Attack matchAttack(@Nullable WeaponAttributes attributes, String animationId) {
        if (attributes == null) {
            return null;
        }
        for (WeaponAttributes.Attack attack : attributes.attacks()) {
            if (animationId.equals(attack.animation())) {
                return attack;
            }
        }
        return null;
    }

    /** 与 BC trailParticlesFromAttack 同优先级：攻击自带 > animation_based 配置。 */
    private static List<ParticlePlacement> placements(@Nullable WeaponAttributes.Attack match, String animationId) {
        if (match != null && !match.trailParticles().isEmpty()) {
            return match.trailParticles();
        }
        TrailConfig config = trailConfig();
        if (config != null && config.animation_based != null) {
            List<ParticlePlacement> specific = config.animation_based.get(animationId);
            if (specific != null) {
                return specific;
            }
        }
        return List.of();
    }

    /** 与 SlashParticleUtil.appearanceFor 同逻辑，去掉 TrailAppearanceOverride（那是玩家侧 API 钩子）。 */
    @Nullable
    private static TrailAppearance appearance(@Nullable WeaponAttributes attributes, ItemStack stack) {
        TrailConfig config = trailConfig();
        if (config == null || config.trail_appearance == null) {
            return TrailAppearance.DEFAULT;
        }
        ConditionalTrailAppearance defaults = config.trail_appearance;
        if (attributes != null && attributes.trailAppearance() != null) {
            return defaults.merge(attributes.trailAppearance()).resolve(stack);
        }
        return defaults.resolve(stack);
    }

    /**
     * BetterCombatMod.trailConfig 的类型是 tiny-config 的 ConfigManager ——
     * 那是 BC jar 里的 JiJ 嵌套 jar，不在本 mod 编译 classpath，只能反射。
     */
    @Nullable
    private static TrailConfig trailConfig() {
        try {
            Object manager = Class.forName("net.bettercombat.BetterCombatMod").getField("trailConfig").get(null);
            if (manager != null) {
                Object value = manager.getClass().getField("value").get(manager);
                if (value instanceof TrailConfig config) {
                    return config;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** ClientConfig 继承 cloth-config 的 ConfigData，同样不在编译 classpath，反射读取。 */
    private static boolean isShowingWeaponTrails() {
        try {
            Object config = Class.forName("net.bettercombat.client.BetterCombatClientMod").getField("config").get(null);
            if (config != null) {
                Object value = config.getClass().getField("isShowingWeaponTrails").get(config);
                if (value instanceof Boolean enabled) {
                    return enabled;
                }
            }
        } catch (Throwable ignored) {
        }
        return true; // BC 默认开
    }
}

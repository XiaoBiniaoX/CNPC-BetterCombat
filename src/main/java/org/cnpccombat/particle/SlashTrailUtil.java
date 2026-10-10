package org.cnpccombat.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.cnpccombat.config.CnpcCombatConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 刀光粒子生成（移植自 BC 1.21.1 的 SlashParticleUtil + TrailParticles）。
 *
 * <p>动画名 → particle placement 表逐条拷贝自 BC {@code TrailParticles.defaults()}
 * （BC 1.9.0 没有 trail config，直接把 1.21.1 内置默认值内置到本类）。
 * 玩家与 NPC 的攻击动画名都形如 {@code bettercombat:one_handed_slash_horizontal_right}，直接查表。
 *
 * <p>坐标/角度计算与 BC 完全一致：以攻击者 yaw/pitch 为基准，按 placement 偏移
 * 沿前向/右向推出生成点；stab 类 placement 沿前向再推 {@code range - 1.5} 格。
 */
public final class SlashTrailUtil {

    private SlashTrailUtil() {
    }

    /** 一个粒子图层对：primary 色画 bottom 贴图，secondary 色画 top 贴图（BC 层叠设计）。 */
    private record ParticleLayer(String top, String bottom) {
    }

    /** TRAILS 表：placement 类型键 → 图层 + roll 偏移 + 是否 stab 位置。 */
    private record TrailSpec(List<ParticleLayer> layers, float rollOffset, boolean stabPosition) {
    }

    /** placement：字段顺序与 BC ParticlePlacement record 一致（注意第4个是 local_yaw 不是 pitch）。 */
    private record Placement(String trailKey, float x, float y, float z,
                             float localYaw, float pitchAdd, float rollSet) {
    }

    private record TrailPart(long colorRGBA, boolean glow) {
    }

    private record Appearance(TrailPart primary, TrailPart secondary) {
    }

    private record Spawn(SlashParticleEffect effect, double x, double y, double z) {
    }

    // ---- BC TrailParticles.ENTRIES（键 → 图层/rollOffset/stabPosition）----

    private static final Map<String, List<TrailSpec>> TRAILS = Map.of(
            "stab", List.of(
                    new TrailSpec(List.of(new ParticleLayer("topstab", "botstab")), -45.0F, true),
                    new TrailSpec(List.of(new ParticleLayer("topstab", "botstab")), 45.0F, true)),
            "slash45", List.of(
                    new TrailSpec(List.of(new ParticleLayer("topslash45", "botslash45")), 0.0F, false)),
            "slash90", List.of(
                    new TrailSpec(List.of(new ParticleLayer("topslash90", "botslash90")), 0.0F, false)),
            "slash180", List.of(
                    new TrailSpec(List.of(new ParticleLayer("topslash180", "botslash180")), 0.0F, false)),
            "slash270", List.of(
                    new TrailSpec(List.of(new ParticleLayer("topslash270", "botslash270")), 0.0F, false)),
            "slash360", List.of(
                    new TrailSpec(List.of(new ParticleLayer("topslash360", "botslash360")), 0.0F, false)));

    // ---- BC TrailParticles.defaults()：animation → placements（逐条拷贝）----

    private static final Map<String, List<Placement>> ANIMATIONS = Map.ofEntries(
            Map.entry("bettercombat:one_handed_slash_horizontal_right", List.of(
                    new Placement("slash90", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:one_handed_slash_horizontal_left", List.of(
                    new Placement("slash90", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 180.0F))),
            Map.entry("bettercombat:one_handed_uppercut_right", List.of(
                    new Placement("slash90", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 75.0F))),
            Map.entry("bettercombat:one_handed_swipe_horizontal_right", List.of(
                    new Placement("slash90", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, -0.10F))),
            Map.entry("bettercombat:one_handed_slam", List.of(
                    new Placement("slash90", 0.05F, -0.1F, 0.0F, 45.0F, 0.0F, -85.0F))),
            Map.entry("bettercombat:one_handed_stab", List.of(
                    new Placement("stab", 0.0F, -0.13F, 0.20F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:one_handed_stab_mounted", List.of(
                    new Placement("stab", 0.0F, 0.15F, 0.0F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:one_handed_punch", List.of(
                    new Placement("stab", 0.0F, -0.1F, 0.15F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:dual_handed_slash_cross", List.of(
                    new Placement("slash180", 0.2F, -0.15F, 0.0F, 0.0F, 0.0F, -120.0F),
                    new Placement("slash180", -0.2F, -0.15F, 0.0F, 0.0F, 0.0F, -60.0F))),
            Map.entry("bettercombat:dual_handed_slash_uncross", List.of(
                    new Placement("slash180", 0.2F, -0.15F, 0.0F, 0.0F, 0.0F, 240.0F),
                    new Placement("slash180", -0.2F, -0.15F, 0.0F, 0.0F, 0.0F, 300.0F))),
            Map.entry("bettercombat:dual_handed_stab", List.of(
                    new Placement("stab", 0.4F, -0.3F, 0.0F, 0.0F, 0.0F, 0.0F),
                    new Placement("stab", -0.4F, -0.3F, 0.0F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:two_handed_stab_left", List.of(
                    new Placement("stab", 0.0F, -0.15F, 0.0F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:two_handed_stab_right", List.of(
                    new Placement("stab", 0.0F, -0.15F, 0.0F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:two_handed_slash_horizontal_right", List.of(
                    new Placement("slash180", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, -5.0F))),
            Map.entry("bettercombat:two_handed_slash_horizontal_left", List.of(
                    new Placement("slash180", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 180.0F))),
            Map.entry("bettercombat:one_handed_slash_switch_blade_right", List.of(
                    new Placement("slash180", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 0.0F))),
            Map.entry("bettercombat:one_handed_slash_switch_blade_left", List.of(
                    new Placement("slash180", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 180.0F))),
            Map.entry("bettercombat:two_handed_spin", List.of(
                    new Placement("slash360", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 180.0F))),
            Map.entry("bettercombat:two_handed_slash_vertical_right", List.of(
                    new Placement("slash90", 0.0F, -0.1F, 0.0F, 45.0F, 0.0F, -80.0F))),
            Map.entry("bettercombat:two_handed_slash_vertical_left", List.of(
                    new Placement("slash90", 0.0F, -0.1F, 0.0F, 45.0F, 0.0F, -100.0F))),
            Map.entry("bettercombat:two_handed_slam", List.of(
                    new Placement("slash180", 0.1F, -0.1F, 0.0F, 45.0F, 0.0F, -86.0F))),
            Map.entry("bettercombat:two_handed_slam_heavy", List.of(
                    new Placement("slash180", 0.1F, -0.1F, 0.0F, 45.0F, 0.0F, -86.0F))));

    /** 未知动画名的回退：stab/punch 类用 stab 图，其余用 slash90。 */
    private static List<Placement> placementsFor(String animation) {
        List<Placement> placements = ANIMATIONS.get(animation);
        if (placements != null) {
            return placements;
        }
        if (animation != null && (animation.contains("stab") || animation.contains("punch"))) {
            return List.of(new Placement("stab", 0.0F, -0.15F, 0.0F, 0.0F, 0.0F, 0.0F));
        }
        return List.of(new Placement("slash90", 0.0F, -0.1F, 0.0F, 0.0F, 0.0F, 0.0F));
    }

    /**
     * 外观：默认白 0.6 / 灰 0.4；附魔时换成 BC 的蓝色发光款（拷贝自 BC TrailParticles.defaults()
     * 的 defaultTrail/enchantedTrail + is_enchanted 条件）。RGBA 打包与 BC Color.toRGBA 一致。
     */
    private static Appearance appearanceFor(ItemStack stack) {
        if (stack.isEnchanted()) {
            return new Appearance(
                    new TrailPart(rgba(0x66, 0xD9, 0xFF, 0.6F), true),
                    new TrailPart(rgba(0x99, 0xE6, 0xFF, 0.3F), true));
        }
        return new Appearance(
                new TrailPart(rgba(0xFF, 0xFF, 0xFF, 0.6F), false),
                new TrailPart(rgba(0x99, 0x99, 0x99, 0.4F), false));
    }

    private static long rgba(int r, int g, int b, float alpha) {
        return ((long) r << 24) | ((long) g << 16) | ((long) b << 8) | (long) (alpha * 255.0F);
    }

    /** 玩家客户端命中时触发（BC ATTACK_HIT 事件）。 */
    public static void spawnClient(Level level, LivingEntity attacker, boolean isOffhand,
                                   float weaponRange, String animation, ItemStack stack) {
        if (!CnpcCombatConfig.COMMON.enableWeaponTrails.get()) {
            return;
        }
        for (Spawn spawn : build(attacker, isOffhand, weaponRange, animation, stack)) {
            level.addParticle(spawn.effect(), spawn.x(), spawn.y(), spawn.z(), 0.0D, 0.0D, 0.0D);
        }
    }

    /** NPC 服务端挥击时触发（所有客户端都可见）。 */
    public static void spawnServer(ServerLevel level, LivingEntity attacker, boolean isOffhand,
                                   float weaponRange, String animation, ItemStack stack) {
        if (!CnpcCombatConfig.COMMON.enableWeaponTrails.get()) {
            return;
        }
        for (Spawn spawn : build(attacker, isOffhand, weaponRange, animation, stack)) {
            level.sendParticles(spawn.effect(), spawn.x(), spawn.y(), spawn.z(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 与 BC SlashParticleUtil.spawnParticles 相同的坐标/角度计算。 */
    private static List<Spawn> build(LivingEntity attacker, boolean isOffhand,
                                     float weaponRange, String animation, ItemStack stack) {
        List<Placement> settingsList = placementsFor(animation);
        List<Spawn> result = new ArrayList<>();
        if (settingsList.isEmpty()) {
            return result;
        }
        Appearance appearance = appearanceFor(stack);

        boolean mirror = isOffhand;
        if (attacker.getMainArm() == HumanoidArm.LEFT) {
            mirror = !mirror;
        }
        // BC：weaponRange -= -0.25F
        float range = weaponRange + 0.25F;
        float offhandRoll = mirror ? 180.0F : 0.0F;
        float offhandFlip = mirror ? -1.0F : 1.0F;

        float yaw = attacker.getYRot();
        float pitch = attacker.getXRot();
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F).normalize();
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();

        for (Placement settings : settingsList) {
            List<TrailSpec> trails = TRAILS.get(settings.trailKey());
            if (trails == null) {
                continue;
            }

            double baseX = attacker.getX();
            double baseY = attacker.getEyeY() - 0.25 + settings.y();
            double baseZ = attacker.getZ();
            Vec3 finalPosition = new Vec3(baseX, baseY, baseZ)
                    .add(forward.scale(settings.z()))
                    .add(right.scale(settings.x() * offhandFlip));
            Vec3 stabPosition = finalPosition.add(forward.scale(range - 1.5));

            for (TrailSpec trail : trails) {
                Vec3 pos = trail.stabPosition() ? stabPosition : finalPosition;
                float localYaw = settings.localYaw() * offhandFlip;
                float roll = (settings.rollSet() + trail.rollOffset() + offhandRoll) * offhandFlip;
                float effectPitch = pitch + settings.pitchAdd();

                for (ParticleLayer layer : trail.layers()) {
                    // primary 色 → bottom 粒子，secondary 色 → top 粒子（BC 层叠顺序）
                    add(result, layer.bottom(), range, effectPitch, yaw, localYaw, roll,
                            appearance.primary(), pos);
                    add(result, layer.top(), range, effectPitch, yaw, localYaw, roll,
                            appearance.secondary(), pos);
                }
            }
        }
        return result;
    }

    private static void add(List<Spawn> out, String typeKey, float scale, float pitch, float yaw,
                            float localYaw, float roll, TrailPart part, Vec3 pos) {
        ParticleType<SlashParticleEffect> type = CnpcCombatParticles.TYPES.get(typeKey);
        if (type == null) {
            return;
        }
        out.add(new Spawn(
                new SlashParticleEffect(type, scale, pitch, yaw, localYaw, roll, part.glow(), part.colorRGBA()),
                pos.x, pos.y, pos.z));
    }
}

package org.cnpccombat.logic;

import net.bettercombat.api.AttackHand;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.NpcDamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.config.CnpcCombatConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Better Combat reworked sweeping (cleave) for NPC attacks.
 *
 * <p>BC player flow: the client finds extra hit targets, the server applies
 * one damage multiplier plus a sweep penalty, then plays sweep sound.
 * For NPCs we find extras server-side: living entities within weapon range in
 * front of the NPC, capped by BC's extra-target count. Extras take the same
 * strength scaled by BC's penalty formula (NPCs have no sweeping-ratio
 * attribute, so their sweep ratio reads as 0 -> full penalty).
 */
public final class NpcSweep {
    /** BC ServerConfig defaults, used when reflection fails. */
    private static final int DEFAULT_EXTRA_TARGETS = 4;
    private static final float DEFAULT_MAX_PENALTY = 0.5F;

    /** Front-cone gate: target must be within ~120 degrees of NPC facing. */
    private static final double CONE_DOT_THRESHOLD = -0.5D;

    private NpcSweep() {
    }

    /**
     * Apply sweep damage, sound and particles if BC reworked sweeping is on
     * and at least one extra target is found next to the intended target.
     */
    public static void sweep(Mob mob, AttackHand hand, LivingEntity intendedTarget) {
        if (!CnpcCombatConfig.npcSweepingEnabled()) {
            return;
        }
        if (!bcConfigBoolean("allow_reworked_sweeping", true)) {
            return;
        }
        int extraCap = Math.max(0, bcConfigInt("reworked_sweeping_extra_target_count", DEFAULT_EXTRA_TARGETS));
        if (extraCap == 0) {
            return;
        }

        List<LivingEntity> extras = findExtras(mob, hand, intendedTarget, extraCap);
        if (extras.isEmpty()) {
            return;
        }

        float maxPenalty = bcConfigFloat("reworked_sweeping_maximum_damage_penalty", DEFAULT_MAX_PENALTY);
        // BC formula: -(maxPenalty / extraCap) * min(extraCap, extras). NPC sweep ratio is 0.
        double multiplier = -(maxPenalty / extraCap) * Math.min(extraCap, extras.size());
        double factor = Math.max(0.0D, 1.0D + multiplier);

        float baseDamage = ((EntityNPCInterface) mob).stats.melee.getStrength();
        DamageSource source = npcDamageSource(mob);
        if (source == null) {
            return;
        }
        for (LivingEntity extra : extras) {
            extra.hurt(source, (float) (baseDamage * factor));
        }

        boolean forSwordsOnly = bcConfigBoolean("reworked_sweeping_sound_and_particles_only_for_swords", true);
        boolean playEffects = !forSwordsOnly || isSword(hand);
        if (playEffects && bcConfigBoolean("reworked_sweeping_plays_sound", true)) {
            mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, mob.getSoundSource(), 1.0F, 1.0F);
        }
        // 斩击拖尾粒子由客户端在攻击动画伤害帧生成（NpcTrailParticles），服务端不再发原版 SWEEP_ATTACK 粒子包。
    }

    /** Living entities in front of the NPC within weapon range, excluding the intended target. */
    private static List<LivingEntity> findExtras(Mob mob, AttackHand hand, LivingEntity intendedTarget, int cap) {
        double range = NpcCombatMath.attackRange(mob, hand);
        AABB box = mob.getBoundingBox().inflate(range, range * 0.5D, range);
        Vec3 look = Vec3.directionFromRotation(0.0F, mob.getYRot());
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == mob || candidate == intendedTarget || !candidate.isAlive()) {
                continue;
            }
            if (mob.distanceTo(candidate) > range + candidate.getBbWidth() * 0.5D) {
                continue;
            }
            Vec3 toTarget = candidate.position().subtract(mob.position()).normalize();
            if (look.dot(toTarget) < CONE_DOT_THRESHOLD) {
                continue;
            }
            if (candidate instanceof EntityNPCInterface npcTarget && npcTarget.faction.id == ((EntityNPCInterface) mob).faction.id) {
                // Same-faction NPCs are never sweep collateral even with the config off:
                // CNPC's own hurt() already rejects that, keep the loop lean.
                continue;
            }
            if (!FriendlyFireGuard.canDamage(mob, candidate)) {
                continue;
            }
            found.add(candidate);
            if (found.size() >= cap) {
                break;
            }
        }
        found.sort(Comparator.comparingDouble(mob::distanceTo));
        return found;
    }

    /** Same damage type CNPC's own doHurtTarget uses, so resistances and events still apply. */
    private static DamageSource npcDamageSource(Mob mob) {
        var holder = mob.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(NpcDamageSource.NPC);
        return new DamageSource(holder, mob);
    }

    private static boolean isSword(AttackHand hand) {
        return hand.itemStack().getItem() instanceof net.minecraft.world.item.SwordItem;
    }

    // --- BetterCombat ServerConfig reflection (same pattern as NpcCombatMath) ---

    private static boolean bcConfigBoolean(String field, boolean fallback) {
        Object value = bcConfigField(field);
        return value instanceof Boolean b ? b : fallback;
    }

    private static int bcConfigInt(String field, int fallback) {
        Object value = bcConfigField(field);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static float bcConfigFloat(String field, float fallback) {
        Object value = bcConfigField(field);
        return value instanceof Number n ? n.floatValue() : fallback;
    }

    private static Object bcConfigField(String field) {
        try {
            Object config = Class.forName("net.bettercombat.BetterCombatMod")
                    .getMethod("getConfig")
                    .invoke(null);
            if (config != null) {
                return config.getClass().getField(field).get(config);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}

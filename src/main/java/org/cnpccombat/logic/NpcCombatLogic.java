package org.cnpccombat.logic;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.api.NpcCombatState;
import org.cnpccombat.config.CnpcCombatConfig;
import org.cnpccombat.network.CnpcNetwork;
import org.cnpccombat.particle.SlashTrailUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class NpcCombatLogic {
    private static final int COMBO_RESET_TICKS = 40;

    private NpcCombatLogic() {
    }

    public static boolean isEligible(Mob mob) {
        if (mob.level().isClientSide || !mob.isAlive()) {
            return false;
        }
        // 手持弓/弩时走远程流程（NpcRangedFlow），近战动画不介入。
        if (mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem) {
            return false;
        }
        return NpcAttackSelector.hasCombatWeapon(mob);
    }

    public static void playFallbackMeleeAnimation(Mob mob) {
        if (mob.level().isClientSide || !mob.isAlive()) {
            return;
        }
        ItemStack stack = mob.getMainHandItem();
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return;
        }
        String animation = stack.isEmpty()
                ? "bettercombat:one_handed_punch"
                : "bettercombat:one_handed_slash_horizontal_right";
        CnpcNetwork.sendAttackAnimation(mob, animation, false, false, 12.0F, 0.5F, 0.06F);
    }

    public static boolean beginAttack(Mob mob, Entity intendedTarget) {
        if (!isEligible(mob)) {
            return false;
        }
        NpcCombatState state = (NpcCombatState) mob;
        if (state.cnpc$getPendingAttack() != null || state.cnpc$getAttackCooldown() > 0) {
            return true;
        }

        AttackHand hand = NpcAttackSelector.select(mob, state.cnpc$getComboCount());
        if (hand == null || hand.attack() == null
                || hand.attack().animation() == null || hand.attack().animation().isBlank()) {
            return false;
        }
        if (!(intendedTarget instanceof LivingEntity livingTarget)
                || !validTarget(mob, livingTarget)) {
            return true;
        }

        state.cnpc$setPendingAttack(hand);
        state.cnpc$setIntendedTargetId(intendedTarget.getId());
        state.cnpc$setComboResetTicks(COMBO_RESET_TICKS);
        startPendingAttack(mob, state, hand);
        return true;
    }

    public static void tick(Mob mob) {
        NpcCombatState state = (NpcCombatState) mob;

        if (state.cnpc$getAttackCooldown() > 0) {
            state.cnpc$setAttackCooldown(state.cnpc$getAttackCooldown() - 1);
        }
        if (state.cnpc$getComboResetTicks() > 0) {
            state.cnpc$setComboResetTicks(state.cnpc$getComboResetTicks() - 1);
            if (state.cnpc$getComboResetTicks() == 0 && state.cnpc$getPendingAttack() == null) {
                state.cnpc$setComboCount(0);
            }
        }

        AttackHand pending = state.cnpc$getPendingAttack();
        if (pending == null) {
            return;
        }

        ItemStack current = pending.isOffHand() ? mob.getOffhandItem() : mob.getMainHandItem();
        if (!ItemStack.isSameItem(current, pending.itemStack()) || mob.isDeadOrDying()) {
            cancelPending(state);
            return;
        }

        Entity intended = state.cnpc$getIntendedTargetId() < 0
                ? null
                : mob.level().getEntity(state.cnpc$getIntendedTargetId());
        if (intended instanceof LivingEntity living && living.isAlive()) {
            mob.getLookControl().setLookAt(living, 30.0F, 30.0F);
        }

        // windup N means wait N full ticks then hit (matches BC: upswingTicks countdown).
        int windup = state.cnpc$getWindupTicks();
        if (windup > 0) {
            state.cnpc$setWindupTicks(windup - 1);
            if (windup - 1 > 0) {
                return;
            }
        }

        performAttack(mob, pending, intended);
        state.cnpc$setPendingAttack(null);
        state.cnpc$setIntendedTargetId(-1);
        state.cnpc$setComboCount(state.cnpc$getComboCount() + 1);
        state.cnpc$setComboResetTicks(COMBO_RESET_TICKS);
    }

    public static boolean isWithinWeaponRange(Mob mob, LivingEntity target) {
        AttackHand hand = NpcAttackSelector.select(mob, ((NpcCombatState) mob).cnpc$getComboCount());
        return hand != null && isWithinRange(mob, target, hand);
    }

    private static boolean isWithinRange(Mob mob, LivingEntity target, AttackHand hand) {
        double range = NpcCombatMath.attackRange(mob, hand);
        return mob.distanceTo(target) <= range;
    }

    private static void cancelPending(NpcCombatState state) {
        state.cnpc$setPendingAttack(null);
        state.cnpc$setWindupTicks(0);
        state.cnpc$setIntendedTargetId(-1);
    }

    private static void startPendingAttack(Mob mob, NpcCombatState state, AttackHand hand) {
        float length = NpcCombatMath.attackDurationTicks(mob, hand);
        int interval = NpcCombatMath.attackIntervalTicks(mob, hand);
        int impact = NpcCombatMath.impactTick(mob, hand);
        // Same value BC player sends: upswingRate = attack.upswing * config.upswing_multiplier
        float upswingRate = NpcCombatMath.animationUpswingRate(hand);
        state.cnpc$setAttackCooldown(interval);
        state.cnpc$setWindupTicks(impact);
        CnpcNetwork.sendAttackAnimation(
                mob,
                hand.attack().animation(),
                hand.isOffHand(),
                hand.attributes().isTwoHanded(),
                length,
                upswingRate,
                upswingRate
        );
        playSound(mob, hand.attack().swingSound());
    }

    private static void performAttack(Mob mob, AttackHand hand, @Nullable Entity intendedTarget) {
        // 刀光粒子：每次完成蓄力的挥击都显示（与 BC 一致，挥空也有），走服务端广播全端可见。
        if (mob.level() instanceof ServerLevel serverLevel) {
            SlashTrailUtil.spawnServer(serverLevel, mob, hand.isOffHand(),
                    (float) NpcCombatMath.attackRange(mob, hand),
                    hand.attack().animation(), hand.itemStack());
        }
        if (!(intendedTarget instanceof LivingEntity target)
                || !validTarget(mob, target)
                || !isWithinRange(mob, target, hand)) {
            return;
        }
        if (!NpcFactionHelper.canDamage(mob, target)) {
            return;
        }

        // Blocking timing fix: vanilla isBlocking() requires shield raised >=5 ticks.
        // NPC windup is typically ~10 ticks; player raising shield mid-windup
        // would not count as blocking when damage lands. Honor block if shield is up.
        if (isTargetBlockingShield(target)) {
            playBlockEffects(mob, target);
            mob.setNoActionTime(0);
            return;
        }

        boolean primaryHit = invokeVanillaAttack(mob, target, hand);

        List<LivingEntity> sweepTargets = new ArrayList<>();
        if (CnpcCombatConfig.COMMON.enableSweep.get()) {
            sweepTargets = findSweepTargets(mob, hand, target);
        }

        if (!sweepTargets.isEmpty()) {
            double multiplier = sweepDamageMultiplier(sweepTargets.size());
            for (LivingEntity sweepTarget : sweepTargets) {
                if (!NpcFactionHelper.canDamage(mob, sweepTarget)) {
                    continue;
                }
                if (isTargetBlockingShield(sweepTarget)) {
                    playBlockEffects(mob, sweepTarget);
                    continue;
                }
                invokeVanillaAttack(mob, sweepTarget, hand);
            }
            playSweepEffects(mob, hand);
        }

        if (primaryHit) {
            playSound(mob, hand.attack().impactSound());
        }
        mob.setNoActionTime(0);
    }

    private static boolean validTarget(Mob mob, LivingEntity target) {
        return target != mob && target.isAlive() && !target.isSpectator() && !mob.isAlliedTo(target);
    }

    private static boolean invokeVanillaAttack(Mob mob, LivingEntity target, AttackHand hand) {
        NpcCombatState state = (NpcCombatState) mob;
        try {
            state.cnpc$setCallingVanillaAttack(true);
            return mob.doHurtTarget(target);
        } finally {
            state.cnpc$setCallingVanillaAttack(false);
        }
    }

    /**
     * Vanilla isBlocking() requires getUseDuration - useItemRemaining >= 5.
     * For our NPC attack path we treat any active shield raise as a valid block
     * so players can react during the windup.
     */
    private static boolean isTargetBlockingShield(LivingEntity target) {
        return target.isUsingItem()
                && target.getUseItem().getUseAnimation() == UseAnim.BLOCK;
    }

    private static void playBlockEffects(Mob mob, LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5,
                target.getZ(),
                8,
                0.15, 0.25, 0.15,
                0.05
        );
        SoundEvent blockSound = BuiltInRegistries.SOUND_EVENT
                .getOptional(new ResourceLocation("minecraft", "item.shield.block"))
                .orElse(null);
        if (blockSound != null) {
            target.level().playSound(
                    null,
                    target.getX(), target.getY(), target.getZ(),
                    blockSound,
                    target.getSoundSource(),
                    1.0F,
                    0.8F + target.getRandom().nextFloat() * 0.4F
            );
        }
    }

    private static List<LivingEntity> findSweepTargets(Mob mob, AttackHand hand, LivingEntity primaryTarget) {
        double range = NpcCombatMath.attackRange(mob, hand);
        double angle = hand.attack().angle();
        double maxAngleHalf = Math.min(angle, 360.0) * 0.5;

        Vec3 look = mob.getLookAngle();
        AABB box = mob.getBoundingBox().inflate(range + 1.0);
        List<LivingEntity> result = new ArrayList<>();

        for (LivingEntity other : mob.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (other == mob || other == primaryTarget || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (mob.isAlliedTo(other)) {
                continue;
            }
            if (mob.distanceTo(other) > range) {
                continue;
            }
            if (!NpcFactionHelper.canDamage(mob, other)) {
                continue;
            }

            Vec3 toOther = other.position()
                    .add(0, other.getBbHeight() * 0.5, 0)
                    .subtract(mob.position().add(0, mob.getBbHeight() * 0.5, 0));
            if (toOther.lengthSqr() < 1.0E-4) {
                result.add(other);
                continue;
            }
            double cosAngle = look.dot(toOther) / (look.length() * toOther.length());
            double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosAngle))));
            if (angle <= 0.0 || angleDeg <= maxAngleHalf) {
                result.add(other);
            }
        }
        return result;
    }

    private static double sweepDamageMultiplier(int extraTargetCount) {
        double maxPenalty = CnpcCombatConfig.COMMON.sweepMaxDamagePenalty.get();
        int extraCap = CnpcCombatConfig.COMMON.sweepExtraTargetCount.get();
        return 1.0 - (maxPenalty / extraCap) * Math.min(extraCap, extraTargetCount);
    }

    private static void playSweepEffects(Mob mob, AttackHand hand) {
        ItemStack stack = hand.itemStack();
        boolean isSword = stack.getItem() instanceof net.minecraft.world.item.SwordItem;
        // Match BC default: particles/sound only for swords unless config changes it (BC config not read here; keep sword-only like vanilla).
        if (!isSword) {
            return;
        }
        // 原版 SWEEP_ATTACK 粒子已移除：刀光由 SlashTrailUtil（BC 1.21.1 移植）渲染，
        // 保留扫击音效。
        mob.level().playSound(
                null,
                mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                mob.getSoundSource(),
                1.0F,
                1.0F
        );
    }

    private static void playSound(Mob mob, @Nullable WeaponAttributes.Sound soundData) {
        if (soundData == null || soundData.id() == null || soundData.id().isBlank()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(soundData.id());
        if (id == null) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) {
            return;
        }
        float randomness = Math.max(0.0F, soundData.randomness());
        float pitch = soundData.pitch() + (mob.getRandom().nextFloat() * 2.0F - 1.0F) * randomness;
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, mob.getSoundSource(), soundData.volume(), pitch);
    }
}

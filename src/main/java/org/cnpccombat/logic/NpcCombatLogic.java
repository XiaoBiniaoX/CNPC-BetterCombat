package org.cnpccombat.logic;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.api.NpcCombatState;
import org.cnpccombat.network.CnpcNetwork;
import org.jetbrains.annotations.Nullable;

public final class NpcCombatLogic {
    private static final ResourceLocation DAMAGE_MODIFIER_ID = CnpcCombat.id("attack_damage_multiplier");
    private static final int COMBO_RESET_TICKS = 40;

    private NpcCombatLogic() {
    }

    public static boolean isEligible(Mob mob) {
        if (mob.level().isClientSide || !mob.isAlive()) {
            return false;
        }
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
                || !validTarget(mob, livingTarget)
                || !isWithinRange(mob, livingTarget, hand)) {
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
        if (!ItemStack.isSameItemSameComponents(current, pending.itemStack()) || mob.isDeadOrDying()) {
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
        return mob.distanceTo(target) <= range + target.getBbWidth() * 0.5D;
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
        if (!(intendedTarget instanceof LivingEntity target)
                || !validTarget(mob, target)
                || !isWithinRange(mob, target, hand)) {
            return;
        }
        if (invokeVanillaAttack(mob, target, hand)) {
            playSound(mob, hand.attack().impactSound());
        }
        mob.setNoActionTime(0);
    }

    private static boolean validTarget(Mob mob, LivingEntity target) {
        return target != mob && target.isAlive() && !target.isSpectator() && !mob.isAlliedTo(target);
    }

    private static boolean invokeVanillaAttack(Mob mob, LivingEntity target, AttackHand hand) {
        NpcCombatState state = (NpcCombatState) mob;
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        double multiplier = Math.max(0.0D, hand.attack().damageMultiplier());
        if (damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
            if (multiplier != 1.0D) {
                damage.addTransientModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_ID,
                        multiplier - 1.0D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            }
        }
        try {
            state.cnpc$setCallingVanillaAttack(true);
            return mob.doHurtTarget(target);
        } finally {
            state.cnpc$setCallingVanillaAttack(false);
            if (damage != null) {
                damage.removeModifier(DAMAGE_MODIFIER_ID);
            }
        }
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

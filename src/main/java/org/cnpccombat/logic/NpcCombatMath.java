package org.cnpccombat.logic;

import net.bettercombat.BetterCombat;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

public final class NpcCombatMath {
    /** CNPC melee delay default; scale = delay / this so 20 keeps pure weapon speed. */
    private static final double CNPC_DEFAULT_DELAY = 20.0D;

    private NpcCombatMath() {
    }

    /**
     * Attack/animation length in ticks (matches BC player cooldown length idea).
     * weaponDuration from held weapon attack speed (item modifiers — CNPC may not mirror them on attributes).
     * final = weaponDuration * (cnpcMeleeDelay / 20). No min-10 cap.
     */
    public static float attackDurationTicks(Mob mob, AttackHand hand) {
        double attackSpeed = itemAttackSpeed(hand.itemStack());
        if (!hand.isOffHand() && mob.getAttribute(Attributes.ATTACK_SPEED) != null) {
            double attr = mob.getAttributeValue(Attributes.ATTACK_SPEED);
            // Prefer the more specific of the two when both are valid (item-driven usually wins if attr is bare default)
            if (attr > 0.1D && Math.abs(attr - 4.0D) > 0.001D) {
                attackSpeed = attr;
            }
        }
        double dualMult = dualWieldingSpeedMultiplier(mob, hand);
        attackSpeed *= dualMult;
        double weaponDuration = 20.0D / Math.max(0.1D, attackSpeed);
        double delayScale = cnpcDelayScale(mob);
        return (float) Math.max(1.0D, weaponDuration * delayScale);
    }

    public static int attackIntervalTicks(Mob mob, AttackHand hand) {
        return Math.max(1, Math.round(attackDurationTicks(mob, hand)));
    }

    public static int attackIntervalForMob(Mob mob) {
        if (!(mob instanceof org.cnpccombat.api.NpcCombatState state)) {
            return cnpcRawDelay(mob);
        }
        AttackHand hand = NpcAttackSelector.select(mob, state.cnpc$getComboCount());
        if (hand == null) {
            return cnpcRawDelay(mob);
        }
        return attackIntervalTicks(mob, hand);
    }

    /**
     * Server damage tick — same as BC player:
     * {@code max(round(duration * upswingRate()), 1)} where upswingRate = attack.upswing * config.upswing_multiplier.
     */
    public static int impactTick(Mob mob, AttackHand hand) {
        float duration = attackDurationTicks(mob, hand);
        return Math.max(1, Math.round(duration * (float) hand.upswingRate()));
    }

    /** Pass to client animation layer — BC player path uses this same value. */
    public static float animationUpswingRate(AttackHand hand) {
        // AttackHand.upswingRate() reads BetterCombat.config (cloth). Reflect guard to be safe.
        try {
            return (float) hand.upswingRate();
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return (float) Mth.clamp(hand.attack().upswing() * 0.5D, 0.01D, 0.99D);
        }
    }

    public static double attackRange(Mob mob, AttackHand hand) {
        // BetterCombat 1.9.0: 绝对攻击范围直接来自 weapon attributes.
        double range = hand.attributes().attackRange();
        return Math.max(0.5D, range);
    }

    private static double dualWieldingSpeedMultiplier(Mob mob, AttackHand hand) {
        if (!NpcAttackSelector.isDualWielding(mob)) {
            return 1.0D;
        }
        try {
            return Math.max(0.1D, BetterCombat.config.dual_wielding_attack_speed_multiplier);
        } catch (Throwable ignored) {
        }
        return 1.0D;
    }

    private static double cnpcDelayScale(Mob mob) {
        return Math.max(1, cnpcRawDelay(mob)) / CNPC_DEFAULT_DELAY;
    }

    private static int cnpcRawDelay(Mob mob) {
        if (mob instanceof EntityNPCInterface npc) {
            return Math.max(1, npc.stats.melee.getDelay());
        }
        return (int) CNPC_DEFAULT_DELAY;
    }

    private static double itemAttackSpeed(ItemStack stack) {
        double base = 4.0D;
        // 1.20.1: ItemStack.getAttributeModifiers(slot) → Multimap<Attribute, AttributeModifier>
        List<AttributeModifier> modifiers = new ArrayList<>(
                stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED));
        return Math.max(0.1D, applyModifiers(base, modifiers));
    }

    private static double applyModifiers(double base, List<AttributeModifier> modifiers) {
        double add = 0.0D;
        double addBase = 0.0D;
        double multiplyTotal = 1.0D;
        for (AttributeModifier modifier : modifiers) {
            switch (modifier.getOperation()) {
                case ADDITION -> add += modifier.getAmount();
                case MULTIPLY_BASE -> addBase += modifier.getAmount();
                case MULTIPLY_TOTAL -> multiplyTotal *= 1.0D + modifier.getAmount();
            }
        }
        return (base + add + base * addBase) * multiplyTotal;
    }
}

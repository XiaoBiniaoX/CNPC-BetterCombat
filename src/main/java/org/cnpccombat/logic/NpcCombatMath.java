package org.cnpccombat.logic;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.minecraft.core.Holder;
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
        // Always resolve from the attack hand's item so datapack/mod weapons stay correct
        // even when CNPC inventory doesn't push ATTACK_SPEED onto the mob attributes.
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
        // AttackHand.upswingRate() needs ServerConfig (cloth). Reflect to avoid compile dep.
        try {
            return (float) hand.upswingRate();
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return (float) Mth.clamp(hand.attack().upswing() * 0.5D, 0.01D, 0.99D);
        }
    }

    public static double attackRange(Mob mob, AttackHand hand) {
        WeaponAttributes attributes = hand.attributes();
        double rangeMultiplier = Math.max(0.05F, hand.attack().rangeMultiplier());
        double legacy = attributes.attackRange();
        double range;
        if (legacy > 0.0D) {
            range = legacy * rangeMultiplier;
        } else {
            range = (2.5D + attributes.rangeBonus()) * rangeMultiplier;
        }
        return Math.max(0.5D, range);
    }

    private static double dualWieldingSpeedMultiplier(Mob mob, AttackHand hand) {
        if (!NpcAttackSelector.isDualWielding(mob)) {
            return 1.0D;
        }
        try {
            Object config = Class.forName("net.bettercombat.BetterCombatMod")
                    .getMethod("getConfig")
                    .invoke(null);
            if (config != null) {
                Object value = config.getClass().getField("dual_wielding_attack_speed_multiplier").get(config);
                if (value instanceof Number number) {
                    return Math.max(0.1D, number.doubleValue());
                }
            }
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
        List<AttributeModifier> modifiers = new ArrayList<>();
        stack.forEachModifier(EquipmentSlot.MAINHAND, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
            if (attribute.is(Attributes.ATTACK_SPEED)) {
                modifiers.add(modifier);
            }
        });
        return Math.max(0.1D, applyModifiers(base, modifiers));
    }

    private static double applyModifiers(double base, List<AttributeModifier> modifiers) {
        double add = 0.0D;
        double addBase = 0.0D;
        double multiplyTotal = 1.0D;
        for (AttributeModifier modifier : modifiers) {
            switch (modifier.operation()) {
                case ADD_VALUE -> add += modifier.amount();
                case ADD_MULTIPLIED_BASE -> addBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multiplyTotal *= 1.0D + modifier.amount();
            }
        }
        return (base + add + base * addBase) * multiplyTotal;
    }
}

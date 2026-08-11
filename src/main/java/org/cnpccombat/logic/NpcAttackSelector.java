package org.cnpccombat.logic;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.ComboState;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class NpcAttackSelector {
    private NpcAttackSelector() {
    }

    public static boolean hasCombatWeapon(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return false;
        }
        return hasAttacks(WeaponRegistry.getAttributes(stack));
    }

    public static boolean isDualWielding(LivingEntity entity) {
        if (entity.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || entity.getOffhandItem().getItem() instanceof ProjectileWeaponItem) {
            return false;
        }
        WeaponAttributes main = WeaponRegistry.getAttributes(entity.getMainHandItem());
        WeaponAttributes off = WeaponRegistry.getAttributes(entity.getOffhandItem());
        return hasAttacks(main) && !main.isTwoHanded() && hasAttacks(off) && !off.isTwoHanded();
    }

    public static boolean isTwoHandedWielding(LivingEntity entity) {
        WeaponAttributes main = WeaponRegistry.getAttributes(entity.getMainHandItem());
        return hasAttacks(main) && main.isTwoHanded();
    }

    @Nullable
    public static AttackHand select(LivingEntity entity, int comboCount) {
        boolean dual = isDualWielding(entity);
        boolean offHand = dual && Math.floorMod(comboCount, 2) == 1;
        ItemStack stack = offHand ? entity.getOffhandItem() : entity.getMainHandItem();
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return null;
        }
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
        if (!hasAttacks(attributes)) {
            return null;
        }

        int handCombo = dual ? Math.max(0, comboCount - (offHand ? 1 : 0)) / 2 : Math.max(0, comboCount);
        WeaponAttributes.Attack[] valid = Arrays.stream(attributes.attacks())
                .filter(a -> a != null && conditionsPass(a.conditions(), entity, offHand))
                .toArray(WeaponAttributes.Attack[]::new);
        if (valid.length == 0) {
            return null;
        }
        int index = Math.floorMod(handCombo, valid.length);
        return new AttackHand(valid[index], new ComboState(index + 1, valid.length), offHand, attributes, stack);
    }

    private static boolean hasAttacks(@Nullable WeaponAttributes attributes) {
        return attributes != null && attributes.attacks() != null && attributes.attacks().length > 0;
    }

    private static boolean conditionsPass(
            @Nullable WeaponAttributes.Condition[] conditions,
            LivingEntity entity,
            boolean offHandAttack
    ) {
        if (conditions == null || conditions.length == 0) {
            return true;
        }
        return Arrays.stream(conditions).allMatch(c -> conditionPasses(c, entity, offHandAttack));
    }

    private static boolean conditionPasses(
            @Nullable WeaponAttributes.Condition condition,
            LivingEntity entity,
            boolean offHandAttack
    ) {
        if (condition == null) {
            return true;
        }
        return switch (condition) {
            case NOT_DUAL_WIELDING -> !isDualWielding(entity);
            case DUAL_WIELDING_ANY -> isDualWielding(entity);
            case DUAL_WIELDING_SAME -> isDualWielding(entity)
                    && entity.getMainHandItem().is(entity.getOffhandItem().getItem());
            case DUAL_WIELDING_SAME_CATEGORY -> {
                if (!isDualWielding(entity)) {
                    yield false;
                }
                WeaponAttributes main = WeaponRegistry.getAttributes(entity.getMainHandItem());
                WeaponAttributes off = WeaponRegistry.getAttributes(entity.getOffhandItem());
                yield main != null && off != null && main.category() != null && !main.category().isBlank()
                        && main.category().equals(off.category());
            }
            case NO_OFFHAND_ITEM -> isTwoHandedWielding(entity) || entity.getOffhandItem().isEmpty();
            case OFF_HAND_SHIELD -> !isTwoHandedWielding(entity)
                    && !isDualWielding(entity)
                    && entity.getOffhandItem().getItem() instanceof ShieldItem;
            case MAIN_HAND_ONLY -> !offHandAttack;
            case OFF_HAND_ONLY -> offHandAttack;
            case MOUNTED -> entity.isPassenger();
            case NOT_MOUNTED -> !entity.isPassenger();
        };
    }
}

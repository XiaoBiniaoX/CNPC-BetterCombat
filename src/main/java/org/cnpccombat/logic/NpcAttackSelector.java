package org.cnpccombat.logic;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.ComboState;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.api.NpcAnimGroupData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class NpcAttackSelector {
    private NpcAttackSelector() {
    }

    /**
     * 取该实体生效的近战属性：
     * 若是 CNPC 且在 AI 面板里设置了攻击动画组，优先用动画组；否则用手中武器自身的。
     *
     * <p>这样"手里拿的是镐但想让它挥剑"、"空手也能有斧攻击动作"都能实现。
     */
    @Nullable
    public static WeaponAttributes attributesFor(LivingEntity entity, ItemStack stack) {
        WeaponAttributes override = overrideAttributes(entity);
        if (override != null) {
            return override;
        }
        return WeaponRegistry.getAttributes(stack);
    }

    /** 该 NPC 设置的动画组属性；没设置 / 组无效 / 不是 CNPC 时返回 null。 */
    @Nullable
    public static WeaponAttributes overrideAttributes(LivingEntity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) {
            return null;
        }
        if (!(npc.ais instanceof NpcAnimGroupData data)) {
            return null;
        }
        return AnimationGroupRegistry.get(data.cnpc$getAttackAnimGroup());
    }

    /** 是否设置了有效的动画组覆盖。 */
    public static boolean hasOverride(LivingEntity entity) {
        return overrideAttributes(entity) != null;
    }

    public static boolean hasCombatWeapon(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return false;
        }
        // 设置了动画组时，即使空手或拿着非武器也算"能打"。
        return hasAttacks(attributesFor(entity, stack));
    }

    public static boolean isDualWielding(LivingEntity entity) {
        if (entity.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || entity.getOffhandItem().getItem() instanceof ProjectileWeaponItem) {
            return false;
        }
        // 动画组覆盖是"整个实体一套动作"，双持判定失去意义（两手会拿到同一套属性），
        // 所以覆盖生效时直接按单手处理，避免左右手动作互相打断。
        if (hasOverride(entity)) {
            return false;
        }
        WeaponAttributes main = WeaponRegistry.getAttributes(entity.getMainHandItem());
        WeaponAttributes off = WeaponRegistry.getAttributes(entity.getOffhandItem());
        return hasAttacks(main) && !main.isTwoHanded() && hasAttacks(off) && !off.isTwoHanded();
    }

    public static boolean isTwoHandedWielding(LivingEntity entity) {
        WeaponAttributes main = attributesFor(entity, entity.getMainHandItem());
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
        WeaponAttributes attributes = attributesFor(entity, stack);
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

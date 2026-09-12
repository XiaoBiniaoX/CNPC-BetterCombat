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
import org.cnpccombat.api.NpcAnimGroupMirror;
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
        return AnimationGroupRegistry.get(animGroupOf(entity));
    }

    /**
     * 该 NPC 设置的动画组 id（原始字符串，不做有效性校验）。<b>双端可用。</b>
     *
     * <p>与 {@link #overrideAttributes} 分开是因为**客户端拿不到解析后的属性**
     * （{@code AnimationGroupRegistry.RESOLVED} 只在服务端填充），
     * 但客户端渲染持握姿态需要这个 id 去查 pose 名。
     *
     * <h2>★★ 取值顺序按端区分（第17轮修的 bug，别再颠倒回去）</h2>
     * <ul>
     *   <li><b>服务端</b>：读 {@code ais}（{@code DataAI} 才是权威数据）</li>
     *   <li><b>客户端</b>：<b>只读 DataDisplay 镜像</b>，绝不读 {@code ais}</li>
     * </ul>
     *
     * <p><b>为什么客户端不能读 {@code ais}</b>：CNPC 的 {@code readSpawnData}
     * 不含 {@code ais}，所以客户端实体的 {@code ais} 平时是空的 ——
     * 但**打开一次 AI 界面就会被填上**：
     * <pre>
     * GuiNpcAI.init()  -> Packets.sendServer(new SPacketMenuGet(AI))
     * SPacketMenuGet   -> 服务端回传 ais.save(data)
     *                  -> 客户端 PacketGuiData 把它 readToNBT 进**客户端实体的 ais**
     * </pre>
     * 于是客户端 {@code ais} 里留下了**那一刻的快照**。此后脚本在服务端改了动画组、
     * 镜像也随 {@code PacketNpcUpdate} 更新了，但旧的取值顺序是"先 ais 有值就用"
     * → 那个**过期快照**一直挡在前面 → 持握姿态永远停在旧值。
     *
     * <p>这正是用户复现步骤里的现象：攻击动画立刻变 B（走服务端），
     * 持握姿态却还是 A（客户端读到 ais 快照）；再打开一次 AI 界面，
     * 快照被刷成 B，退出来姿态就变 B 了 —— 看着像"要开 GUI 才生效"，
     * 实际是"开 GUI 顺手刷新了那份过期快照"。
     *
     * <p>镜像走的是 {@code display}，由 {@code writeSpawnData}/{@code PacketNpcUpdate}
     * 同步，服务端一改就到客户端，是客户端唯一可信的来源。
     */
    @Nullable
    public static String animGroupOf(LivingEntity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) {
            return null;
        }

        // 客户端：只认镜像。客户端的 ais 可能残留着打开 AI 界面时的过期快照。
        if (npc.level().isClientSide) {
            if (npc.display instanceof NpcAnimGroupMirror mirror) {
                String group = mirror.cnpc$getAnimGroupMirror();
                if (group != null && !group.isBlank()) {
                    return group;
                }
            }
            return null;
        }

        // 服务端：DataAI 是权威。
        if (npc.ais instanceof NpcAnimGroupData data) {
            String group = data.cnpc$getAttackAnimGroup();
            if (group != null && !group.isBlank()) {
                return group;
            }
        }
        return null;
    }

    /**
     * 该实体生效的**持握姿态动画名**，没有则返回 null。<b>双端可用。</b>
     *
     * <p>优先级与攻击侧一致：先看动画组覆盖，再退回手中武器自身的 pose。
     *
     * <p>之所以不走 {@link #attributesFor}：那条路在客户端对"动画组覆盖"
     * 恒返回 null（RESOLVED 是空的），会导致设了动画组的 NPC
     * <b>重进世界后持握姿态消失</b>。这里改查 {@code POSES}
     * —— 它由 S2C 同步，双端都有。
     */
    @Nullable
    public static String poseFor(LivingEntity entity, ItemStack stack) {
        String groupPose = AnimationGroupRegistry.poseOf(animGroupOf(entity));
        if (groupPose != null && !groupPose.isBlank()) {
            return groupPose;
        }
        WeaponAttributes weapon = WeaponRegistry.getAttributes(stack);
        return weapon == null ? null : weapon.pose();
    }

    /**
     * 该实体是否按"双手武器"处理。<b>双端可用</b>，客户端也正确。
     *
     * <p>动画组覆盖生效时客户端拿不到 {@code isTwoHanded()}（RESOLVED 为空），
     * 所以这个标志<b>随 pose 一起从服务端同步</b>。
     *
     * <p>没有用"带 pose 的组一定是双手"这条捷径：虽然官方 12 个带 pose 的组
     * 实测全是 {@code two_handed=true}，但第三方数据包可以写单手 + pose，
     * 那时捷径会判错（副手姿态被错误禁用）。同步真实值成本只有 1 bit。
     */
    public static boolean isTwoHandedPose(LivingEntity entity, ItemStack stack) {
        String group = animGroupOf(entity);
        if (AnimationGroupRegistry.poseOf(group) != null) {
            return AnimationGroupRegistry.isTwoHanded(group);
        }
        WeaponAttributes weapon = WeaponRegistry.getAttributes(stack);
        return weapon != null && weapon.isTwoHanded();
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
        //
        // 用 animGroupOf() 而不是 hasOverride()：后者要查只在服务端填充的
        // RESOLVED 表，在**客户端恒为 false** -> 设了动画组的 NPC 在客户端
        // 会被误判成双持，副手姿态跟着乱。animGroupOf 读的是
        // DataAI + DataDisplay 镜像，双端一致。
        String group = animGroupOf(entity);
        if (group != null && !group.isBlank()) {
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

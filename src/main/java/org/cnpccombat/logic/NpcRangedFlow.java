package org.cnpccombat.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 服务端：让手持弓/弩的 CustomNPCs NPC 走"真实"的拉弓 / 上弹使用周期，
 * 像掠夺者那样先蓄力再射出，而不是原版 CNPC 的瞬发。
 *
 * <p>设计要点（参考 CNPC-EpicFight-Addon-CE 的 NpcBowDrawFlow）：
 * <ul>
 *   <li>本类<b>不生成任何投掷物</b>。蓄力完成后仍然调用 CNPC 自己的
 *       {@code EntityNPCInterface.performRangedAttack}，因此 CNPC 投掷物栏里的物品
 *       （箭 / 药水箭 / 光灵箭 / 烟花）以及 DataRanged 的全部属性
 *       （伤害、速度、精度、大小、效果、音效、连发数、抛物线判定）全部原样生效。</li>
 *   <li>{@code CrossbowItem.setCharged} 是私有的；而且 vanilla 的
 *       {@code CrossbowItem.releaseUsing} 会去实体自己的背包里找箭 —— NPC 的弹药在
 *       CNPC 投掷物栏里，永远找不到，会导致"一直拉弦但永不发射"。所以这里直接写
 *       {@code Charged} / {@code ChargedProjectiles} 两个 NBT 标签
 *       （跟 vanilla 方法写的是同一份字段）。</li>
 *   <li>拉弓时长取自 CNPC 自己的最小射击延迟，所以在 GUI 里调延迟就能调蓄力节奏；
 *       上弹时长取自 {@code CrossbowItem.getChargeDuration}，因此快速装填附魔有效。</li>
 * </ul>
 *
 * <p>为什么 {@code isUsingItem()} 能在 NPC 上生效：{@code startUsingItem} 写的是
 * {@code LivingEntity.DATA_LIVING_ENTITY_FLAGS}，这是 SynchedEntityData，会自动同步到
 * 客户端；客户端 {@code onSyncedDataUpdated} 会补上 {@code useItem}/{@code useItemRemaining}，
 * 所以客户端渲染器读到的 {@code getUseItemRemainingTicks() > 0} 是正确的。
 * CNPC 从未重写任何 use-item 相关方法，也没有阻断 {@code LivingEntity.tick} 里的
 * {@code updatingUsingItem()}，因此 vanilla 状态机可以直接工作。
 */
public final class NpcRangedFlow {
    private static final String TAG_CHARGED = "Charged";
    private static final String TAG_CHARGED_PROJECTILES = "ChargedProjectiles";

    /** 弓的拉满时长上限（vanilla 满蓄力 20 tick）。 */
    private static final int BOW_MAX_DRAW_TICKS = 20;
    /** 弓的拉满时长下限，太短看不出动作。 */
    private static final int BOW_MIN_DRAW_TICKS = 5;

    private NpcRangedFlow() {
    }

    /** 是否是我们要接管的远程武器。用 ProjectileWeaponItem 判定以覆盖模组弓弩。 */
    public static boolean isRangedWeapon(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ProjectileWeaponItem;
    }

    public static boolean isCrossbow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CrossbowItem;
    }

    /** 弓 = 使用动作为 BOW 的远程武器（覆盖模组弓，不局限于 BowItem）。 */
    public static boolean isBow(ItemStack stack) {
        return isRangedWeapon(stack) && stack.getUseAnimation() == UseAnim.BOW;
    }

    /** 弓的目标拉满时长：CNPC 最小射击延迟的一半，夹到 [5, 20]。 */
    public static int bowDrawTicks(EntityNPCInterface npc) {
        int delayMin = Math.max(1, npc.stats.ranged.getDelayMin());
        return Math.max(BOW_MIN_DRAW_TICKS, Math.min(BOW_MAX_DRAW_TICKS, delayMin / 2));
    }

    /** 弩的上弹时长，跟随快速装填附魔。 */
    public static int crossbowChargeTicks(ItemStack crossbow) {
        return Math.max(1, CrossbowItem.getChargeDuration(crossbow));
    }

    /** NPC 投掷物栏里是否有弹药。没弹药就不该做蓄力动作。 */
    public static boolean hasAmmo(EntityNPCInterface npc) {
        IItemStack projectile = npc.inventory.getProjectile();
        return projectile != null && !projectile.isEmpty();
    }

    /**
     * 每个远程 AI tick 调用一次，推进拉弓 / 上弹。
     * 由 {@code EntityAIRangedAttackMixin} 注入在 {@code tick} 的 HEAD。
     */
    public static void tickDraw(EntityNPCInterface npc) {
        if (npc.level().isClientSide) {
            return;
        }
        ItemStack mainHand = npc.getMainHandItem();
        if (!isRangedWeapon(mainHand) || !hasAmmo(npc)) {
            // 不是弓弩 / 没弹药：确保退出使用状态，别把 NPC 卡在蓄力里。
            stopUsing(npc);
            return;
        }

        if (isCrossbow(mainHand)) {
            tickCrossbow(npc, mainHand);
        } else if (!npc.isUsingItem()) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private static void tickCrossbow(EntityNPCInterface npc, ItemStack crossbow) {
        if (CrossbowItem.isCharged(crossbow)) {
            // 已上弹：退出使用状态，让"持弩待发"姿态显示出来。
            // 但先校正装填内容 —— 玩家可能在弩已上弹后改了投掷物栏，
            // 不刷新的话外观会停留在旧弹药（例如换成烟花后仍显示箭弩）。
            refreshChargedProjectiles(npc, crossbow);
            stopUsing(npc);
            return;
        }
        if (!npc.isUsingItem()) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        if (npc.getTicksUsingItem() >= crossbowChargeTicks(crossbow)) {
            setCharged(npc, crossbow, true);
            stopUsing(npc);
        }
    }

    /**
     * CNPC 已经决定这一帧要开火 —— 我们是否允许？
     * <p>弓：必须已拉到目标时长。弩：必须已上好弹。
     * <p>返回 false 表示还在蓄力，调用方会把 CNPC 的连发计数回退一格，
     * 下一 tick 重试，所以射速不会因为蓄力而丢发。
     */
    public static boolean readyToFire(EntityNPCInterface npc) {
        ItemStack mainHand = npc.getMainHandItem();
        if (!isRangedWeapon(mainHand) || !hasAmmo(npc)) {
            // 非弓弩（例如 CNPC 经典的"空手扔物品"玩法）：完全不干预。
            return true;
        }
        if (isCrossbow(mainHand)) {
            return CrossbowItem.isCharged(mainHand);
        }
        return npc.isUsingItem() && npc.getTicksUsingItem() >= bowDrawTicks(npc);
    }

    /** 射击已发生：清掉蓄力状态，准备下一轮。 */
    public static void onFired(EntityNPCInterface npc) {
        if (npc.level().isClientSide) {
            return;
        }
        ItemStack mainHand = npc.getMainHandItem();
        if (!isRangedWeapon(mainHand)) {
            return;
        }
        if (isCrossbow(mainHand)) {
            setCharged(npc, mainHand, false);
        }
        stopUsing(npc);
    }

    /**
     * 远程 AI 结束（丢目标 / 死亡 / 进近战范围）时复位。
     * <p>{@code Charged} 标签写在 ItemStack 上会随存档保存，而"正在使用物品"这个
     * 瞬时状态不会。若不复位，NPC 在上弹中途存档，读档后会带着状态残留。
     */
    public static void reset(EntityNPCInterface npc) {
        if (npc.level().isClientSide) {
            return;
        }
        ItemStack mainHand = npc.getMainHandItem();
        if (isCrossbow(mainHand)) {
            setCharged(npc, mainHand, false);
        }
        stopUsing(npc);
    }

    /**
     * 每个 NPC tick 调用：不在战斗中时把弩预先上好弹。
     * 这样第一次交战不用白等一轮上弹，也补齐连发之间的空隙。
     */
    public static void tickKeepLoaded(EntityNPCInterface npc) {
        if (npc.level().isClientSide) {
            return;
        }
        ItemStack mainHand = npc.getMainHandItem();
        if (!isCrossbow(mainHand) || !hasAmmo(npc)) {
            return;
        }
        if (CrossbowItem.isCharged(mainHand)) {
            // 已上弹：只校正装填内容，好让改投掷物栏后外观能跟上。
            refreshChargedProjectiles(npc, mainHand);
            return;
        }
        if (!npc.isUsingItem()) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        if (npc.getTicksUsingItem() >= crossbowChargeTicks(mainHand)) {
            setCharged(npc, mainHand, true);
            stopUsing(npc);
        }
    }

    private static void stopUsing(EntityNPCInterface npc) {
        if (npc.isUsingItem()) {
            npc.stopUsingItem();
        }
    }

    /**
     * {@code CrossbowItem.setCharged} / {@code addChargedProjectile} 都是私有的，
     * 这里直接写它们背后的两个 NBT 字段。
     *
     * <p><b>为什么必须同时写 {@code ChargedProjectiles}：</b>
     * 弩的物品模型靠两个 predicate 决定外观 ——
     * {@code charged} = {@code isCharged(stack)}，
     * {@code firework} = {@code isCharged(stack) && containsChargedProjectile(stack, FIREWORK_ROCKET)}。
     * 后者会遍历 {@code ChargedProjectiles} 这个 ListTag。只写 {@code Charged=true}
     * 而不写列表，列表就是空的，{@code containsChargedProjectile} 恒为 false，
     * 于是无论投掷物栏放什么都只会渲染成装箭的弩。
     *
     * <p>所以装填时把 CNPC 投掷物栏的物品（箭 / 药水箭 / 光灵箭 / 烟花）
     * 序列化进列表，这样烟花弩、光灵箭弩的外观都能正确显示。
     * 注意这只影响<b>显示</b>：实际发射仍然走 CNPC 的
     * {@code performRangedAttack} → {@code EntityProjectile}，弹药不会被消耗两次。
     *
     * <p>同时置 {@code updateClient}：CNPC 的 weapons 表在客户端是独立反序列化出来的副本，
     * 不重新同步的话客户端看不到 NBT 变化，弩的姿态和外观都不会更新。
     */
    private static void setCharged(EntityNPCInterface npc, ItemStack crossbow, boolean charged) {
        boolean was = CrossbowItem.isCharged(crossbow);
        if (charged) {
            CompoundTag tag = crossbow.getOrCreateTag();
            tag.putBoolean(TAG_CHARGED, true);
            writeChargedProjectiles(npc, tag);
        } else {
            CompoundTag tag = crossbow.getTag();
            if (tag == null) {
                return;
            }
            tag.putBoolean(TAG_CHARGED, false);
            tag.remove(TAG_CHARGED_PROJECTILES);
        }
        if (was != charged) {
            npc.updateClient = true;
        }
    }

    /**
     * 把 CNPC 投掷物栏的物品写进弩的 {@code ChargedProjectiles}，
     * 格式与 vanilla {@code CrossbowItem.addChargedProjectile} 完全一致
     * （ListTag&lt;CompoundTag&gt;，每项是一个完整序列化的 ItemStack）。
     */
    private static void writeChargedProjectiles(EntityNPCInterface npc, CompoundTag crossbowTag) {
        ItemStack ammo = projectileStack(npc);
        if (ammo.isEmpty()) {
            // 没弹药就别写空列表，让它退回普通"已上弹"外观。
            crossbowTag.remove(TAG_CHARGED_PROJECTILES);
            return;
        }
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        // 只装 1 发用于显示；数量不影响 predicate 判定。
        ItemStack single = ammo.copy();
        single.setCount(1);
        single.save(entry);
        list.add(entry);
        crossbowTag.put(TAG_CHARGED_PROJECTILES, list);
    }

    /**
     * 弩已上弹时校正装填内容。
     * <p>玩家可能在弩上好弹之后才去改投掷物栏（箭 → 烟花），
     * 此时 {@code Charged} 已经是 true，装填流程不会再跑，外观就会卡在旧弹药上。
     * 这里每 tick 廉价比对一次已装物品与投掷物栏是否同种，只在真的不同时才重写并同步。
     */
    private static void refreshChargedProjectiles(EntityNPCInterface npc, ItemStack crossbow) {
        CompoundTag tag = crossbow.getTag();
        if (tag == null) {
            return;
        }
        ItemStack ammo = projectileStack(npc);
        if (ammo.isEmpty()) {
            return;
        }
        if (chargedMatches(tag, ammo)) {
            return;
        }
        writeChargedProjectiles(npc, tag);
        npc.updateClient = true;
    }

    /** 已装填的第一发是否与投掷物栏同种物品。 */
    private static boolean chargedMatches(CompoundTag crossbowTag, ItemStack ammo) {
        if (!crossbowTag.contains(TAG_CHARGED_PROJECTILES, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = crossbowTag.getList(TAG_CHARGED_PROJECTILES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return false;
        }
        ItemStack loaded = ItemStack.of(list.getCompound(0));
        // 只比物品类型：药水箭的效果差异不影响弩的外观 predicate。
        return !loaded.isEmpty() && loaded.is(ammo.getItem());
    }

    /** 取 CNPC 投掷物栏的 ItemStack；没有则返回 {@link ItemStack#EMPTY}。 */
    private static ItemStack projectileStack(EntityNPCInterface npc) {
        IItemStack projectile = npc.inventory.getProjectile();
        if (projectile == null || projectile.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = projectile.getMCItemStack();
        return stack == null ? ItemStack.EMPTY : stack;
    }
}

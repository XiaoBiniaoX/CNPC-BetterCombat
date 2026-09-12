package org.cnpccombat.api;

import org.jetbrains.annotations.Nullable;

/**
 * 挂在 CNPC {@code DataAI} 上的"攻击动画组覆盖"设置。
 *
 * <p>存储位置选在 DataAI 而不是自建存储，是为了完全复用 CNPC 原生的存档 + 同步链路：
 * <ul>
 *   <li>存档：{@code EntityNPCInterface.addAdditionalSaveData} -> {@code ais.save(compound)}</li>
 *   <li>读档：{@code EntityNPCInterface.readAdditionalSaveData} -> {@code ais.readToNBT(compound)}</li>
 *   <li>GUI 保存：{@code SPacketMenuSave(EnumMenuType.AI, ais.save(new CompoundTag()))}
 *       -> 服务端 {@code npc.ais.readToNBT(data)}</li>
 * </ul>
 * 因此<b>不需要任何自定义 C2S 保存包</b>，也就不存在"客户端直接往服务端塞无效数据包"的风险
 * —— CNPC 的 SPacketMenuSave 自带手持魔杖校验和权限节点校验。
 */
public interface NpcAnimGroupData {
    /** 已设置的攻击动画组 id（如 {@code bettercombat:sword}），未设置返回 null。 */
    @Nullable
    String cnpc$getAttackAnimGroup();

    /** 传 null 或空串表示取消设置（回退到手中武器自身的动画组）。 */
    void cnpc$setAttackAnimGroup(@Nullable String groupId);

    /**
     * 请求把当前值推送给附近客户端（服务端侧有效，客户端调用是空操作）。
     *
     * <p>持握姿态由客户端自己算，所以服务端改完值必须通知客户端，
     * 否则已加载的 NPC 会一直用旧姿态。实现走 CNPC 现成的
     * {@code EntityNPCInterface.updateClient} 标志。
     */
    void cnpc$pushToClient();

    // ------------------------------------------------------------------ 脚本 API
    //
    // 下面三个是给 CNPC **脚本系统**用的门面，方法名不带 cnpc$ 前缀
    // （脚本里 `e.npc.getAi().setAttackAnimGroup(...)` 要可读）。
    //
    // 为什么这样就能被脚本调到（已实测，见 findings.md 第14轮「一」）：
    //  1. CNPC 的脚本引擎是 **Nashorn（JSR-223）**，不是 GraalJS
    //     -> 纯反射解析 public 成员，不需要 @HostAccess.Export、
    //        没有成员白名单、没有注册表要维护；
    //  2. `NPCWrapper.getAi()` **直接返回 EntityNPCInterface.ais 这个 DataAI 实例**
    //     （中间没有代理层），而 Nashorn 在**实际类**上找方法
    //     -> 我们把方法混入 DataAI，脚本立刻可见，无需改 INPCAi 接口。
    //
    // 之所以与 cnpc$ 版本并存而不是改名：cnpc$ 前缀那套是本 mod 内部
    // （GUI / NpcAttackSelector）在用的，改名要动所有调用点，收益为零。

    /**
     * 脚本 API：设置该 NPC 的攻击动画组。
     *
     * <p>脚本示例（CNPC 的脚本函数都以事件为参数）：
     * <pre>{@code
     * function init(e) {
     *     e.npc.getAi().setAttackAnimGroup("bettercombat:claymore");
     * }
     * }</pre>
     *
     * <p>传 {@code null} 或空串表示取消设置，回退到"用手里武器自己的动画组"。
     *
     * <p><b>不做合法性校验是故意的</b>：动画组来自数据包，脚本可能在
     * 资源加载完成前就执行。无效 id 在选攻击时自然回退到武器自身属性
     * （{@code AnimationGroupRegistry.get} 返回 null），不会报错也不会卡住战斗。
     * 想确认是否有效，用 {@link #getAvailableAttackAnimGroups()} 对比。
     *
     * <p><b>会立即同步到客户端</b>（{@link #cnpc$pushToClient()}）——
     * 否则已加载在客户端的 NPC 会继续用旧的持握姿态，
     * 只有重进世界才更新。这是脚本路径与 GUI 路径的关键差别：
     * GUI 保存由 CNPC 的 {@code SPacketMenuSave} 自己触发同步，脚本没有。
     */
    default void setAttackAnimGroup(@Nullable String groupId) {
        cnpc$setAttackAnimGroup(groupId);
        cnpc$pushToClient();
    }

    /** 脚本 API：读当前攻击动画组 id，未设置返回 {@code null}。 */
    @Nullable
    default String getAttackAnimGroup() {
        return cnpc$getAttackAnimGroup();
    }

    /**
     * 脚本 API：列出当前可用的攻击动画组 id。
     *
     * <p>没有这个方法，脚本作者只能靠猜 id。返回的是不可变列表，
     * 脚本里可以直接 {@code .size()} / {@code for each} 遍历。
     *
     * <p>只包含**真正带攻击动作**的组（弓弩那种没有 hitbox 的组已被过滤掉），
     * 与 AI 面板里的选择列表完全一致。
     */
    default java.util.List<String> getAvailableAttackAnimGroups() {
        return org.cnpccombat.logic.AnimationGroupRegistry.available();
    }
}

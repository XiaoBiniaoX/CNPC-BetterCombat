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
}

package org.cnpccombat.api;

import org.jetbrains.annotations.Nullable;

/**
 * 挂在 CNPC {@code DataDisplay} 上的「攻击动画组**客户端镜像**」。
 *
 * <h2>为什么需要这个镜像</h2>
 * 权威数据在 {@code DataAI}（{@link NpcAnimGroupData}），但 CNPC 的
 * spawn 同步是**手工挑字段**的：
 * <pre>
 * EntityNPCInterface.writeSpawnData()   // 只写 Speed/StandingState/Orientation/... 少数几项
 * EntityNPCInterface.readSpawnData()    // 逐字段读，**没有 ais.readToNBT**
 * </pre>
 * 所以 DataAI 上的任何新字段都**到不了客户端**。
 *
 * <p>而这两个方法里都有 {@code display.save/readToNBT}，
 * 本 mod 的 YSM 模型名一直工作正常，正是因为它存在 DataDisplay 上。
 * 这个镜像搭的就是同一条便车。
 *
 * <h2>这修的是什么 bug</h2>
 * 「持握武器动作不连续，重进世界就失效」：
 * 持握姿态由客户端每 tick 自己算（{@code cnpc$updateWeaponPoses}），
 * 需要读 NPC 的动画组 id。此前客户端读的是 {@code npc.ais}，
 * 那里恒为空 —— 只有刚用 GUI 保存过的那一会儿，客户端 DataAI 里
 * 碰巧有残留值，所以「第一次看着是好的」；一旦重进世界，实体重新
 * spawn，残留消失，持握姿态就再也出不来。
 *
 * <h2>单向数据流（重要）</h2>
 * <b>只读镜像。</b>写入一律走 {@link NpcAnimGroupData}（GUI 与脚本都是），
 * 由服务端在同步前刷新镜像。不要从客户端往镜像写值，
 * 否则两份数据会打架，且客户端数据不可信。
 */
public interface NpcAnimGroupMirror {
    /** 客户端可读的动画组 id；未设置返回 null。 */
    @Nullable
    String cnpc$getAnimGroupMirror();

    /** <b>仅服务端调用</b>：把 DataAI 上的权威值刷进镜像。 */
    void cnpc$setAnimGroupMirror(@Nullable String groupId);
}

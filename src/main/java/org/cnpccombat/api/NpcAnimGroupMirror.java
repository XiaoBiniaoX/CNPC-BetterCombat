package org.cnpccombat.api;

import org.jetbrains.annotations.Nullable;

/**
 * 挂在 CNPC {@code DataDisplay} 上的「攻击动画组<b>客户端镜像</b>」。
 *
 * <h2>为什么需要这个镜像</h2>
 * 权威数据在 {@code DataAI}（{@link NpcAnimGroupData}），但 CNPC 的
 * spawn 同步是<b>手工挑字段</b>的（1.21.1 反编译已核实）：
 * <pre>
 * EntityNPCInterface.writeSpawnData()   // :1576-1612，只挑 Speed/StandingState/Orientation/... 9 项
 * EntityNPCInterface.readSpawnData()    // :1618-1651，逐字段读，**没有 ais.readToNBT**
 * </pre>
 * 所以 DataAI 上的任何新字段都<b>到不了客户端</b>。
 *
 * <p>而这两个方法里都有 {@code display.save/readToNBT}
 * （{@code :1579} 和 {@code :1649}），这个镜像搭的就是那条便车。
 *
 * <h2>这防的是什么 bug</h2>
 * 「持握武器动作不连续，重进世界就失效」：
 * 持握姿态由客户端每 tick 自己算（{@code cnpc$updateWeaponPoses}），
 * 需要读 NPC 的动画组 id。若客户端读的是 {@code npc.ais}，
 * 那里平时是空的 —— 只有刚用 GUI 保存过的那一会儿，客户端 DataAI 里
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

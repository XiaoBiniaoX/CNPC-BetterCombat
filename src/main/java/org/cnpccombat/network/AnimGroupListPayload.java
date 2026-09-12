package org.cnpccombat.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务端 -> 客户端：可用的 BetterCombat 攻击动画组 id 列表。
 * 客户端 GUI 需要这份列表来展示可选项（BetterCombat 本体从不同步这些 id）。
 *
 * <p>双端通用，不含客户端专用类。
 *
 * <p><b>安全边界：</b>这是纯 S2C 包，客户端只读不写；不存在客户端伪造数据影响服务端的路径。
 * 解码时对条目数和字符串长度都设了上限，防御恶意/损坏的包导致内存爆掉。
 */
public final class AnimGroupListPayload {
    /** 组数量上限。正常整合包大约几十个，1024 已经非常宽松。 */
    private static final int MAX_ENTRIES = 1024;
    /** 单个 id 长度上限，与 DataAIMixin 的写入上限一致。 */
    private static final int MAX_ID_LENGTH = 256;

    public final List<String> groupIds;

    /**
     * 组 id -> 持握姿态动画名。只包含**有 pose 的**组，所以通常远少于 groupIds。
     *
     * <p><b>为什么必须同步</b>（"持握动作重进世界失效"的修复）：
     * 客户端每 tick 自己算持握姿态，而它拿不到服务端的解析结果
     * （{@code AnimationGroupRegistry.RESOLVED} 只在服务端填充），
     * 也拿不到 NPC 身上的动画组字符串（CNPC 的 {@code writeSpawnData}
     * 手工挑字段，不含它）。给客户端补上 pose 名它才画得出来。
     */
    public final Map<String, String> poses;

    /** 上面这些带 pose 的组里，哪些是双手武器（客户端判要不要禁副手姿态）。 */
    public final Set<String> twoHanded;

    public AnimGroupListPayload(List<String> groupIds, Map<String, String> poses, Set<String> twoHanded) {
        this.groupIds = groupIds;
        this.poses = poses;
        this.twoHanded = twoHanded;
    }

    public static void encode(AnimGroupListPayload msg, FriendlyByteBuf buf) {
        int count = Math.min(msg.groupIds.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(msg.groupIds.get(i), MAX_ID_LENGTH);
        }

        // pose 表：只写有 pose 的组，每项 = id + pose名 + 是否双手。
        int poseCount = Math.min(msg.poses.size(), MAX_ENTRIES);
        buf.writeVarInt(poseCount);
        int written = 0;
        for (Map.Entry<String, String> entry : msg.poses.entrySet()) {
            if (written++ >= poseCount) {
                break;
            }
            buf.writeUtf(entry.getKey(), MAX_ID_LENGTH);
            buf.writeUtf(entry.getValue(), MAX_ID_LENGTH);
            buf.writeBoolean(msg.twoHanded.contains(entry.getKey()));
        }
    }

    public static AnimGroupListPayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            // 包损坏：返回空而不是抛异常，避免踢掉连接。
            return empty();
        }
        List<String> ids = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUtf(MAX_ID_LENGTH));
        }

        int poseCount = buf.readVarInt();
        if (poseCount < 0 || poseCount > MAX_ENTRIES) {
            // id 列表已经读出来了，pose 表损坏就只丢 pose，别把整包扔掉。
            return new AnimGroupListPayload(ids, new LinkedHashMap<>(), new LinkedHashSet<>());
        }
        Map<String, String> poses = new LinkedHashMap<>();
        Set<String> twoHanded = new LinkedHashSet<>();
        for (int i = 0; i < poseCount; i++) {
            String id = buf.readUtf(MAX_ID_LENGTH);
            String pose = buf.readUtf(MAX_ID_LENGTH);
            boolean isTwoHanded = buf.readBoolean();
            poses.put(id, pose);
            if (isTwoHanded) {
                twoHanded.add(id);
            }
        }
        return new AnimGroupListPayload(ids, poses, twoHanded);
    }

    private static AnimGroupListPayload empty() {
        return new AnimGroupListPayload(new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashSet<>());
    }
}

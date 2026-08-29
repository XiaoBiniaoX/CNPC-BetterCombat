package org.cnpccombat.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

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

    public AnimGroupListPayload(List<String> groupIds) {
        this.groupIds = groupIds;
    }

    public static void encode(AnimGroupListPayload msg, FriendlyByteBuf buf) {
        int count = Math.min(msg.groupIds.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(msg.groupIds.get(i), MAX_ID_LENGTH);
        }
    }

    public static AnimGroupListPayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            // 包损坏：返回空列表而不是抛异常，避免踢掉连接。
            return new AnimGroupListPayload(new ArrayList<>());
        }
        List<String> ids = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUtf(MAX_ID_LENGTH));
        }
        return new AnimGroupListPayload(ids);
    }
}

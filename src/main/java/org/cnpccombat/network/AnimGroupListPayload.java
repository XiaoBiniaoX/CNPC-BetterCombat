package org.cnpccombat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.client.ClientPayloadHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务端 -> 客户端：可用的 BetterCombat 攻击动画组 id 列表 + 持握姿态表。
 *
 * <p>客户端 GUI 需要这份列表来展示可选项（BetterCombat 本体从不同步这些 id），
 * 客户端渲染持握姿态需要 pose 名（它每 tick 自己算，拿不到就画不出来）。
 *
 * <p><b>安全边界：</b>这是纯 S2C 包，客户端只读不写；不存在客户端伪造数据影响服务端的路径。
 * 解码时对条目数和字符串长度都设了上限，防御恶意/损坏的包导致内存爆掉。
 *
 * @param groupIds  全部可选组 id
 * @param poses     组 id -&gt; 持握姿态动画名。只包含<b>有 pose 的</b>组，所以通常远少于 groupIds
 * @param twoHanded 上面这些带 pose 的组里，哪些是双手武器（客户端判要不要禁副手姿态）
 */
public record AnimGroupListPayload(
        List<String> groupIds,
        Map<String, String> poses,
        Set<String> twoHanded
) implements CustomPacketPayload {
    /** 组数量上限。正常整合包大约几十个，1024 已经非常宽松。 */
    private static final int MAX_ENTRIES = 1024;

    /** 单个 id 长度上限，与 DataAIMixin 的写入上限一致。 */
    private static final int MAX_ID_LENGTH = 256;

    public static final Type<AnimGroupListPayload> TYPE = new Type<>(CnpcCombat.id("anim_groups"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnimGroupListPayload> STREAM_CODEC =
            StreamCodec.ofMember(AnimGroupListPayload::write, AnimGroupListPayload::decode);

    private void write(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(this.groupIds.size(), MAX_ENTRIES);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buffer.writeUtf(this.groupIds.get(i), MAX_ID_LENGTH);
        }

        // pose 表：只写有 pose 的组，每项 = id + pose名 + 是否双手。
        int poseCount = Math.min(this.poses.size(), MAX_ENTRIES);
        buffer.writeVarInt(poseCount);
        int written = 0;
        for (Map.Entry<String, String> entry : this.poses.entrySet()) {
            if (written++ >= poseCount) {
                break;
            }
            buffer.writeUtf(entry.getKey(), MAX_ID_LENGTH);
            buffer.writeUtf(entry.getValue(), MAX_ID_LENGTH);
            buffer.writeBoolean(this.twoHanded.contains(entry.getKey()));
        }
    }

    private static AnimGroupListPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            // 包损坏：返回空而不是抛异常，避免踢掉连接。
            return empty();
        }
        List<String> ids = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            ids.add(buffer.readUtf(MAX_ID_LENGTH));
        }

        int poseCount = buffer.readVarInt();
        if (poseCount < 0 || poseCount > MAX_ENTRIES) {
            // id 列表已经读出来了，pose 表损坏就只丢 pose，别把整包扔掉。
            return new AnimGroupListPayload(ids, new LinkedHashMap<>(), new LinkedHashSet<>());
        }
        Map<String, String> poses = new LinkedHashMap<>();
        Set<String> twoHanded = new LinkedHashSet<>();
        for (int i = 0; i < poseCount; i++) {
            String id = buffer.readUtf(MAX_ID_LENGTH);
            String pose = buffer.readUtf(MAX_ID_LENGTH);
            boolean isTwoHanded = buffer.readBoolean();
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

    public static void handle(AnimGroupListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadHandler.handleAnimGroups(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

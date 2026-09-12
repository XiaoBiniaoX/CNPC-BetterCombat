package org.cnpccombat.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.cnpccombat.api.NpcAnimGroupData;
import org.cnpccombat.api.NpcAnimGroupMirror;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把“攻击动画组覆盖”字段挂进 CNPC 的 {@code DataAI}，
 * 借它原生的 save/readToNBT 完成存档与客户端 GUI 往返。
 *
 * <p>{@code save} 与 {@code readToNBT} 都是 CNPC 自有方法名（非 vanilla override），
 * 所以必须 {@code remap = false}，否则运行时注入点找不到。
 * 两者在 1.21.1 里的签名仍是纯 {@code CompoundTag}
 * （{@code DataAI.java:117} / {@code :78}），<b>没有</b>引入 {@code HolderLookup.Provider}。
 *
 * <p><b>NBT 安全：</b>
 * <ul>
 *   <li>key 用纯字母 {@code CnpcCombatAttackAnimGroup}，不含冒号/斜杠/空格，
 *       任何 NBT 实现都能安全序列化。</li>
 *   <li>空值不写 key（而不是写空串），保持 NBT 干净、向后兼容。</li>
 *   <li>读取前先 {@code contains(key, Tag.TAG_STRING)} 做类型校验，
 *       避免旧存档或手改 NBT 里塞了别的类型导致抛异常。</li>
 *   <li>写入长度上限 256，防止被塞超长字符串。</li>
 * </ul>
 */
@Mixin(DataAI.class)
public abstract class DataAIMixin implements NpcAnimGroupData {
    @Unique
    private static final String CNPC$KEY = "CnpcCombatAttackAnimGroup";

    @Unique
    private static final int CNPC$MAX_LENGTH = 256;

    /**
     * CNPC 的 {@code DataAI} 自带的宿主 NPC 引用（用于刷新客户端镜像 + 主动发包）。
     * 声明为 {@code private EntityNPCInterface npc}（{@code DataAI.java:41}），
     * 是 CNPC 自有字段，必须 {@code remap = false}。
     */
    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Unique
    @Nullable
    private String cnpc$attackAnimGroup;

    @Override
    @Nullable
    public String cnpc$getAttackAnimGroup() {
        return this.cnpc$attackAnimGroup;
    }

    @Override
    public void cnpc$setAttackAnimGroup(@Nullable String groupId) {
        if (groupId == null || groupId.isBlank() || groupId.length() > CNPC$MAX_LENGTH) {
            this.cnpc$attackAnimGroup = null;
        } else {
            this.cnpc$attackAnimGroup = groupId;
        }
        this.cnpc$syncMirror();
    }

    /**
     * 把权威值刷进 {@code DataDisplay} 上的镜像，让它随 CNPC 的 spawn 同步到客户端。
     *
     * <p><b>为什么必须这么绕</b>：CNPC 的
     * {@code EntityNPCInterface.writeSpawnData/readSpawnData} 是手工挑字段的，
     * <b>不含 {@code ais}</b> -> DataAI 上的字段永远到不了客户端；
     * 但它们<b>包含</b> {@code display.save/readToNBT}（{@code :1579}/{@code :1649}）。
     * 客户端要自己每 tick 算持握姿态，必须能读到动画组 id
     * —— 见 {@link NpcAnimGroupMirror} 的完整说明。
     *
     * <p>放在 setter 里而不是某个 tick 钩子：所有写入路径
     * （GUI 保存 -> {@code readToNBT}、脚本 API、读档）最终都经过这里，
     * 一处覆盖全部，也不产生每 tick 开销。
     */
    @Unique
    private void cnpc$syncMirror() {
        EntityNPCInterface owner = this.npc;
        if (owner == null || owner.display == null) {
            // DataAI 在 NPC 构造期就创建，display 理论上已就绪；
            // 但读档顺序不保证，拿不到就跳过 —— 下次 setter 调用会补上。
            return;
        }
        if (owner.display instanceof NpcAnimGroupMirror mirror) {
            mirror.cnpc$setAnimGroupMirror(this.cnpc$attackAnimGroup);
        }
    }

    /**
     * 脚本改完动画组后，让 CNPC 把新的 spawn 数据重发给附近客户端。
     *
     * <p><b>这是“脚本改动画组后持握动作仍是旧的”的预防</b>：
     * 刷新镜像只是把值写进服务端的 {@code DataDisplay} 对象，
     * <b>客户端并不会自动知道</b>。已经加载在客户端的那个 NPC 实体
     * 还留着上次同步过来的旧值，所以持握姿态不变 ——
     * 只有重进世界（实体重新 spawn）才会读到新值。
     *
     * <p>用 CNPC 现成的机制而不是自己发包：
     * <pre>
     * EntityNPCInterface.updateClient()      // :626-629
     *     -> Packets.sendNearby(this, new PacketNpcUpdate(getId(), writeSpawnData()));
     * </pre>
     * 而 {@code writeSpawnData()} 里有 {@code display.save(compound)}（{@code :1579}）
     * —— 也就是我们的镜像。
     *
     * <p>★★ <b>直接调 {@code updateClient()}，不要只置 {@code updateClient = true}。</b>
     * 那个标志的消费点被多层条件卡住（{@code EntityNPCInterface.aiStep():558-581}）：
     * <pre>
     * if (!this.level().isClientSide) {
     *     if (!this.isKilled() &amp;&amp; this.tickCount % 20 == 0) {
     *         ...
     *         if (this.updateClient) { this.updateClient(); }
     * </pre>
     * 即<b>每 20 tick 才检查一次</b>，且 {@code isKilled()} 为真时永远不检查。
     * 只置标志的话，脚本改完最多要等 1 秒才生效。
     * {@code updateClient()} 是 public 方法，它自己会把标志复位，不会重复广播。
     *
     * <p><b>只在脚本路径调用</b>，不放进 {@link #cnpc$syncMirror()}：
     * 读档 / GUI 保存也会走 setter，而那两条路 CNPC 本来就会同步客户端
     * （GUI 保存后 {@code SPacketMenuSave:127} 自己置 updateClient；
     * 读档时实体还没进世界，没有客户端可通知）。无条件置起只会产生多余广播。
     */
    @Override
    public void cnpc$pushToClient() {
        EntityNPCInterface owner = this.npc;
        if (owner == null) {
            return;
        }
        // 客户端侧改值不需要广播，也不该广播（客户端数据不可信）。
        if (owner.level() == null || owner.level().isClientSide) {
            return;
        }
        owner.updateClient();
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void cnpc$readAnimGroup(CompoundTag compound, CallbackInfo ci) {
        if (compound != null && compound.contains(CNPC$KEY, Tag.TAG_STRING)) {
            this.cnpc$setAttackAnimGroup(compound.getString(CNPC$KEY));
        } else {
            // 包里没这个 key 说明是“取消设置”或旧数据，必须清掉旧值，
            // 否则 GUI 里取消设置后保存，服务端会保留上一次的值。
            // 走 setter 而不是直接写字段，否则**镜像不会跟着清空**
            // -> 客户端会一直用旧的持握姿态。
            this.cnpc$setAttackAnimGroup(null);
        }
    }

    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpc$saveAnimGroup(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        String group = this.cnpc$attackAnimGroup;
        if (compound != null && group != null && !group.isBlank()) {
            compound.putString(CNPC$KEY, group);
        }
    }
}

package org.cnpccombat.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataAI;
import org.cnpccombat.api.NpcAnimGroupData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把"攻击动画组覆盖"字段挂进 CNPC 的 {@code DataAI}，
 * 借它原生的 save/readToNBT 完成存档与客户端 GUI 往返。
 *
 * <p>{@code save} 与 {@code readToNBT} 都是 CNPC 自有方法名（非 vanilla override），
 * 所以必须 {@code remap = false}，否则运行时（SRG 命名）注入点找不到。
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
            return;
        }
        this.cnpc$attackAnimGroup = groupId;
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void cnpc$readAnimGroup(CompoundTag compound, CallbackInfo ci) {
        if (compound != null && compound.contains(CNPC$KEY, Tag.TAG_STRING)) {
            this.cnpc$setAttackAnimGroup(compound.getString(CNPC$KEY));
        } else {
            // 包里没这个 key 说明是"取消设置"或旧数据，必须清掉旧值，
            // 否则 GUI 里取消设置后保存，服务端会保留上一次的值。
            this.cnpc$attackAnimGroup = null;
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

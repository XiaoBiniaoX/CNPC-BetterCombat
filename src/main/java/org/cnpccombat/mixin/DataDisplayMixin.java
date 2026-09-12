package org.cnpccombat.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataDisplay;
import org.cnpccombat.api.NpcAnimGroupMirror;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把“攻击动画组的客户端镜像”挂进 CNPC 的 {@code DataDisplay}，
 * 借它原生的 save/readToNBT 完成存档与客户端同步。
 *
 * <p><b>为什么要在 DataDisplay 上再存一份</b>（防“持握动作重进世界失效”）：
 * 权威数据在 {@code DataAI}（见 {@link DataAIMixin}），但 CNPC 的
 * {@code EntityNPCInterface.writeSpawnData()} / {@code readSpawnData()}
 * 是<b>手工挑字段</b>的，只同步 Speed/StandingState/Orientation 等 9 项，
 * <b>不调用 {@code ais.save()}</b>（{@code EntityNPCInterface.java:1576-1651}）
 * —— 所以 DataAI 上的任何新字段都到不了客户端。
 *
 * <p>而 {@code writeSpawnData} 里<b>有</b> {@code this.display.save(compound)}
 * （{@code :1579}），{@code readSpawnData} 里<b>有</b>
 * {@code this.display.readToNBT(compound)}（{@code :1649}）。所以这里搭同一条便车。
 *
 * <p><b>只读镜像</b> —— 写入一律走 DataAI（GUI / 脚本），避免两份数据互相打架。
 *
 * <p>{@code save} 与 {@code readToNBT} 都是 CNPC 自有方法名（非 vanilla override），
 * 所以必须 {@code remap = false}。签名在 1.21.1 里仍是纯 {@code CompoundTag}
 * （{@code DataDisplay.java:121} / {@code :148}）。
 *
 * <p>NBT 安全性同 {@link DataAIMixin}：key 用纯字母、空值不写 key、
 * 读取前做类型校验、写入长度设上限。
 */
@Mixin(DataDisplay.class)
public abstract class DataDisplayMixin implements NpcAnimGroupMirror {
    @Unique
    private static final String CNPC$ANIM_GROUP_KEY = "CnpcCombatAnimGroupMirror";

    @Unique
    private static final int CNPC$MAX_LENGTH = 256;

    @Unique
    @Nullable
    private String cnpc$animGroupMirror;

    @Override
    @Nullable
    public String cnpc$getAnimGroupMirror() {
        return this.cnpc$animGroupMirror;
    }

    @Override
    public void cnpc$setAnimGroupMirror(@Nullable String groupId) {
        if (groupId == null || groupId.isBlank() || groupId.length() > CNPC$MAX_LENGTH) {
            this.cnpc$animGroupMirror = null;
            return;
        }
        this.cnpc$animGroupMirror = groupId;
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void cnpc$readAnimGroupMirror(CompoundTag compound, CallbackInfo ci) {
        if (compound != null && compound.contains(CNPC$ANIM_GROUP_KEY, Tag.TAG_STRING)) {
            this.cnpc$setAnimGroupMirror(compound.getString(CNPC$ANIM_GROUP_KEY));
        } else {
            // 包里没这个 key 说明是“取消设置”或旧数据，必须清掉旧值，
            // 否则取消设置后客户端会继续用上一次的持握姿态。
            this.cnpc$animGroupMirror = null;
        }
    }

    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpc$saveAnimGroupMirror(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        String group = this.cnpc$animGroupMirror;
        if (compound != null && group != null && !group.isBlank()) {
            compound.putString(CNPC$ANIM_GROUP_KEY, group);
        }
    }
}

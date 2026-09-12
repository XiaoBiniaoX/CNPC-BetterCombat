package org.cnpccombat.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataDisplay;
import org.cnpccombat.api.NpcAnimGroupMirror;
import org.cnpccombat.api.NpcYsmModelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把"YSM 模型名"挂进 CNPC 的 {@code DataDisplay}，借它原生的 save/readToNBT
 * 完成存档与客户端 GUI 往返。
 *
 * <p>{@code save} 与 {@code readToNBT} 都是 CNPC 自有方法名（非 vanilla override），
 * 所以必须 {@code remap = false} —— 运行时是 SRG 命名，写 official 名会找不到注入点。
 * 这也意味着**不需要**往 build.gradle 的 npcClassMappings 补映射。
 *
 * <p><b>这个 mixin 不引用任何 YSM 类型</b>，所以未装 YSM 时也能安全加载。
 * 它存的只是一个字符串，服务端照常存档/同步，不需要 YSM 在场。
 *
 * <p>NBT 安全性同 DataAIMixin：key 用纯字母、空值不写 key、读取前做类型校验、
 * 写入长度设上限。
 */
@Mixin(DataDisplay.class)
public abstract class DataDisplayYsmMixin implements NpcYsmModelData, NpcAnimGroupMirror {
    @Unique
    private static final String CNPC$KEY = "CnpcCombatYsmModel";

    /**
     * 攻击动画组的**客户端镜像** key。
     *
     * <p><b>为什么要在 DataDisplay 上再存一份</b>（"持握动作重进世界失效"的根因）：
     * 权威数据在 {@code DataAI}（见 {@code DataAIMixin}），但 CNPC 的
     * {@code EntityNPCInterface.writeSpawnData()} / {@code readSpawnData()}
     * 是**手工挑字段**的，只同步 Speed/StandingState/Orientation 等少数几项，
     * <b>不调用 {@code ais.save()}</b> —— 所以 DataAI 上的任何新字段
     * 都到不了客户端。
     *
     * <p>而 {@code writeSpawnData} 里**有** {@code this.display.save(compound)}，
     * {@code readSpawnData} 里**有** {@code this.display.readToNBT(compound)}。
     * 本 mod 的 YSM 模型名一直正常工作，正是因为它存在 DataDisplay 上。
     *
     * <p>所以这里搭同一条便车：服务端把动画组镜像进 DataDisplay，
     * 客户端渲染持握姿态时就能读到。<b>只读镜像</b> ——
     * 写入一律走 DataAI（GUI / 脚本），避免两份数据互相打架。
     */
    @Unique
    private static final String CNPC$ANIM_GROUP_KEY = "CnpcCombatAnimGroupMirror";

    @Unique
    private static final int CNPC$MAX_LENGTH = 256;

    @Unique
    @Nullable
    private String cnpc$ysmModel;

    @Unique
    @Nullable
    private String cnpc$animGroupMirror;

    @Override
    @Nullable
    public String cnpc$getYsmModel() {
        return this.cnpc$ysmModel;
    }

    @Override
    public void cnpc$setYsmModel(@Nullable String modelId) {
        if (modelId == null || modelId.isBlank() || modelId.length() > CNPC$MAX_LENGTH) {
            this.cnpc$ysmModel = null;
            return;
        }
        this.cnpc$ysmModel = modelId;
    }

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
    private void cnpc$readYsmModel(CompoundTag compound, CallbackInfo ci) {
        if (compound != null && compound.contains(CNPC$KEY, Tag.TAG_STRING)) {
            this.cnpc$setYsmModel(compound.getString(CNPC$KEY));
        } else {
            // 包里没这个 key 说明是"取消设置"或旧数据，必须清掉旧值，
            // 否则 GUI 里清除后保存，服务端会保留上一次的值。
            this.cnpc$ysmModel = null;
        }

        if (compound != null && compound.contains(CNPC$ANIM_GROUP_KEY, Tag.TAG_STRING)) {
            this.cnpc$setAnimGroupMirror(compound.getString(CNPC$ANIM_GROUP_KEY));
        } else {
            this.cnpc$animGroupMirror = null;
        }
    }

    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpc$saveYsmModel(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        String model = this.cnpc$ysmModel;
        if (compound != null && model != null && !model.isBlank()) {
            compound.putString(CNPC$KEY, model);
        }

        String group = this.cnpc$animGroupMirror;
        if (compound != null && group != null && !group.isBlank()) {
            compound.putString(CNPC$ANIM_GROUP_KEY, group);
        }
    }
}

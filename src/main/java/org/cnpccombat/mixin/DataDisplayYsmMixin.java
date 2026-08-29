package org.cnpccombat.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataDisplay;
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
public abstract class DataDisplayYsmMixin implements NpcYsmModelData {
    @Unique
    private static final String CNPC$KEY = "CnpcCombatYsmModel";

    @Unique
    private static final int CNPC$MAX_LENGTH = 256;

    @Unique
    @Nullable
    private String cnpc$ysmModel;

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

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void cnpc$readYsmModel(CompoundTag compound, CallbackInfo ci) {
        if (compound != null && compound.contains(CNPC$KEY, Tag.TAG_STRING)) {
            this.cnpc$setYsmModel(compound.getString(CNPC$KEY));
        } else {
            // 包里没这个 key 说明是"取消设置"或旧数据，必须清掉旧值，
            // 否则 GUI 里清除后保存，服务端会保留上一次的值。
            this.cnpc$ysmModel = null;
        }
    }

    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpc$saveYsmModel(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        String model = this.cnpc$ysmModel;
        if (compound != null && model != null && !model.isBlank()) {
            compound.putString(CNPC$KEY, model);
        }
    }
}

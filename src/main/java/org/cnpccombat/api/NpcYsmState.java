package org.cnpccombat.api;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 判断一个 NPC 是否启用了 YSM 模型。
 *
 * <p><b>这个类刻意不引用任何 YSM 类型</b>，所以未装 YSM 时也能安全加载。
 * 它只读 CNPC 的 {@code DataDisplay} 上那个字符串字段
 * （由 {@code DataDisplayYsmMixin} 挂上去），不碰渲染层。
 *
 * <p>用途：那些**通用**的客户端 mixin（比如给 vanilla
 * {@code LivingEntityRenderer} 打的补丁）需要知道"这个 NPC 是不是走 YSM 渲染"，
 * 从而跳过只适用于 CNPC 原生 humanoid 模型的处理。
 * 这些 mixin 未装 YSM 也会加载，所以不能让它们碰 {@code compat.ysm} 包。
 */
public final class NpcYsmState {

    private NpcYsmState() {
    }

    /**
     * 这个实体是否是"已选择 YSM 模型"的 NPC。
     *
     * <p>注意：返回 true 只代表**用户选了模型**，不代表这一帧真的用 YSM 画成了
     * （模型可能还在异步加载）。对调用方来说这个精度足够 —— 选了模型就说明
     * 用户想要 YSM 外观，针对原生 humanoid 模型的调整一律不该再施加。
     */
    public static boolean hasYsmModel(LivingEntity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) {
            return false;
        }
        if (!(npc.display instanceof NpcYsmModelData data)) {
            return false;
        }
        String modelId = data.cnpc$getYsmModel();
        return modelId != null && !modelId.isBlank();
    }
}

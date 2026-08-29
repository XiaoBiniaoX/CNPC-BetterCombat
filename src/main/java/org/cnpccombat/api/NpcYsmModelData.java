package org.cnpccombat.api;

import org.jetbrains.annotations.Nullable;

/**
 * 鸭子接口：给 CNPC 的 {@code DataDisplay} 挂一个"YSM 模型名"字段。
 *
 * <p>存在 DataDisplay（而不是 DataAI）的原因：按钮放在**模型/外观**界面
 * （{@code GuiCreationEntities}），那个界面 save 时提交的是
 * {@code EnumMenuType.DISPLAY}，只有 DataDisplay 的改动会被同步到服务端。
 *
 * <p>借 CNPC 原生的 save/readToNBT 完成存档与同步，不需要自定义网络包，
 * 权限校验也完全由 CNPC 负责。
 */
public interface NpcYsmModelData {
    /** @return 选中的 YSM 模型 id；未设置返回 null */
    @Nullable
    String cnpc$getYsmModel();

    /** 传 null / 空白 / 超长视为"清除设置"。 */
    void cnpc$setYsmModel(@Nullable String modelId);
}

package org.cnpccombat.mixin.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import org.cnpccombat.api.NpcYsmModelData;
import org.cnpccombat.client.gui.GuiStringSelection;
import org.cnpccombat.compat.ysm.YsmFacade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在 NPC 的"模型/外观"界面加一个"YSM 模型"按钮。
 *
 * <h2>位置</h2>
 * 与 CNPC-EF-CE 完全一致：标签在 {@code guiLeft + 130}、按钮在
 * {@code guiLeft + xOffset + 142}、行 y 为 {@code guiTop + 165}，按钮 120x20。
 * 这一行位于 NPC 预览图下方的空白带里，不会压到旋转滑条（{@code guiTop + 210}）
 * 或底部的提示文字。
 *
 * <h2>为什么放在这个界面</h2>
 * 模型名存在 {@code DataDisplay} 上，而这个界面保存时提交的正是
 * {@code EnumMenuType.DISPLAY} —— 只有在这里改，改动才会被 CNPC 原生链路同步到服务端。
 *
 * <h2>为什么必须手工补 refmap</h2>
 * {@code GuiCreationEntities.init} 是 vanilla {@code Screen.init} 的 override，
 * 运行时是 SRG 名 {@code m_7856_}。MixinGradle 的 AP 只自动映射 Minecraft 类，
 * 所以 build.gradle 的 {@code npcClassMappings} 必须有这个条目。
 * 本项目为漏掉这一步崩过一次，见 findings.md。
 *
 * <h2>可选依赖</h2>
 * 按钮<b>只在装了 YSM 时才显示</b>。本类不直接引用 YSM 类型（都走
 * {@link YsmFacade}），未装 YSM 时照常加载、只是不加控件。
 */
@Mixin(value = GuiCreationEntities.class, priority = 1001)
@OnlyIn(Dist.CLIENT)
public abstract class GuiCreationEntitiesYsmMixin extends GuiCreationScreenInterface {

    /** 与 CNPC-EF-CE 的 YSM 行保持一致的纵向位置。 */
    private static final int CNPC$YSM_ROW_Y = 165;

    private static final int CNPC$LABEL_ID = 731;
    private static final int CNPC$BUTTON_ID = 732;

    /**
     * Mixin 要求有可用的构造；这个类永远不会被实例化（它被合并进目标类），
     * 传 null 即可，与 CNPC-EF-CE 的做法一致。
     */
    private GuiCreationEntitiesYsmMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpc$addYsmButton(CallbackInfo ci) {
        if (!YsmFacade.isAvailable()) {
            return;
        }
        if (!(this.npc.display instanceof NpcYsmModelData data)) {
            return;
        }

        List<String> models = YsmFacade.availableModels();
        String current = data.cnpc$getYsmModel();
        String label = current == null || current.isBlank()
                ? "cnpccombat.gui.ysmNone"
                : current;

        int labelX = this.guiLeft + 130;
        int buttonX = this.guiLeft + this.xOffset + 142;

        // 用颜色重载的 GuiLabel（height=0）。tooltip 重载会把 height 设成 10，
        // 在文字左边渲染出一个可聚焦方块，看着像乱码。
        this.addLabel(new GuiLabel(CNPC$LABEL_ID, "cnpccombat.gui.ysmModel",
                labelX, this.guiTop + CNPC$YSM_ROW_Y + 6, 0xFFFFFF));

        // 这个界面的按钮走 lambda 回调（GuiButtonNop + Button.OnPress），
        // 不走 buttonEvent 分派 —— 所以必须用带 OnPress 的构造。
        this.addButton(new GuiButtonNop(this, CNPC$BUTTON_ID, buttonX,
                this.guiTop + CNPC$YSM_ROW_Y, 120, 20, label, b ->
                this.setSubGui(new GuiStringSelection(
                        "cnpccombat.gui.selectYsmModel", models, current, picked -> {
                    data.cnpc$setYsmModel(picked);
                    this.getButton(CNPC$BUTTON_ID).setDisplayText(
                            picked == null || picked.isBlank()
                                    ? "cnpccombat.gui.ysmNone"
                                    : picked);
                }))));
    }
}

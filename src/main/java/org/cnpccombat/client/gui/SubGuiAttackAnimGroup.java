package org.cnpccombat.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.cnpccombat.api.NpcAnimGroupData;
import org.cnpccombat.logic.AnimationGroupRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * "攻击动画组"选择子界面，从 NPC 魔杖 -> AI 面板打开。
 *
 * <p>沿用 CNPC 子界面惯例：{@code extends GuiBasic}、背景 menubg.png、
 * 完成按钮 id 66、直接修改共享的 DataAI 对象。<b>不自己 save()</b> ——
 * 关闭时 {@code GuiWrapper.close()} 会调用父界面 {@code GuiNpcAI.save()}，
 * 由它通过 CNPC 原生的 {@code SPacketMenuSave(EnumMenuType.AI, ...)} 一并提交。
 * 因此没有任何自定义 C2S 包，权限校验完全由 CNPC 负责。
 */
@OnlyIn(Dist.CLIENT)
public class SubGuiAttackAnimGroup extends GuiBasic implements ICustomScrollListener {
    private static final int ID_SCROLL = 0;
    private static final int ID_CLEAR = 60;
    private static final int ID_DONE = 66;

    private final NpcAnimGroupData data;
    private GuiCustomScrollNop scroll;

    public SubGuiAttackAnimGroup(NpcAnimGroupData data) {
        this.data = data;
        this.setBackground("menubg.png");
        this.imageWidth = 256;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        super.init();

        this.addLabel(new GuiLabel(0, "cnpccombat.gui.attackAnimGroup", this.guiLeft + 6, this.guiTop + 6));

        String current = this.data.cnpc$getAttackAnimGroup();
        this.addLabel(new GuiLabel(
                1,
                current == null || current.isBlank() ? "cnpccombat.gui.groupNone" : current,
                this.guiLeft + 6,
                this.guiTop + 20
        ));

        if (this.scroll == null) {
            this.scroll = new GuiCustomScrollNop(this, ID_SCROLL);
            this.scroll.setSize(244, 156);
        }
        this.scroll.guiLeft = this.guiLeft + 6;
        this.scroll.guiTop = this.guiTop + 32;

        List<String> options = new ArrayList<>(AnimationGroupRegistry.available());
        this.scroll.setList(options);
        if (current != null && !current.isBlank()) {
            this.scroll.setSelected(current);
            this.scroll.scrollTo(current);
        }
        this.addScroll(this.scroll);

        this.addButton(new GuiButtonNop(this, ID_CLEAR, this.guiLeft + 6, this.guiTop + 192, 90, 20,
                "cnpccombat.gui.clearGroup"));
        this.addButton(new GuiButtonNop(this, ID_DONE, this.guiLeft + 190, this.guiTop + 192, 60, 20, "gui.done"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ID_CLEAR) {
            this.data.cnpc$setAttackAnimGroup(null);
            if (this.scroll != null) {
                this.scroll.setSelectedIndex(-1);
            }
            this.init();
            return;
        }
        if (button.id == ID_DONE) {
            this.close();
        }
    }

    @Override
    public void scrollClicked(double mouseX, double mouseY, int button, GuiCustomScrollNop scrolled) {
        String selected = scrolled.getSelected();
        if (selected != null && !selected.isBlank()) {
            this.data.cnpc$setAttackAnimGroup(selected);
            this.init();
        }
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scrolled) {
        if (selection != null && !selection.isBlank()) {
            this.data.cnpc$setAttackAnimGroup(selection);
            this.close();
        }
    }
}

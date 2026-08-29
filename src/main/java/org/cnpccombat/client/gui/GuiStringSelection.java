package org.cnpccombat.client.gui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一个通用的"从列表里选一个字符串"子界面，用于 YSM 模型选择。
 *
 * <p>沿用 CNPC 子界面惯例：{@code extends GuiBasic}、背景 menubg.png、完成按钮 id 66。
 * <b>不自己 save()</b> —— 关闭时 {@code GuiWrapper.close()} 会调父界面的 save()，
 * 由 CNPC 原生的菜单保存链路提交，所以不需要自定义网络包，权限校验也归 CNPC。
 *
 * @param title    标题的语言键
 * @param options  可选项
 * @param onPick   选中回调（传 null 表示"清除设置"）
 */
@OnlyIn(Dist.CLIENT)
public class GuiStringSelection extends GuiBasic implements ICustomScrollListener {
    private static final int ID_SCROLL = 0;
    private static final int ID_CLEAR = 60;
    private static final int ID_DONE = 66;

    private final String title;
    private final List<String> options;
    private final Consumer<String> onPick;
    private final String current;

    private GuiCustomScrollNop scroll;

    public GuiStringSelection(String title, List<String> options, String current,
                              Consumer<String> onPick) {
        this.title = title;
        this.options = new ArrayList<>(options);
        this.current = current;
        this.onPick = onPick;
        this.setBackground("menubg.png");
        this.imageWidth = 256;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        super.init();

        // 用 4 参 GuiLabel（颜色跟随 CNPC 主题）。不要用 5 参的 tooltip 重载 ——
        // 那个会把 height 设成 10，于是文字左侧多出一个 10px 的可聚焦方块，看着像乱码。
        this.addLabel(new GuiLabel(0, this.title, this.guiLeft + 6, this.guiTop + 6));

        if (this.scroll == null) {
            this.scroll = new GuiCustomScrollNop(this, ID_SCROLL);
            this.scroll.setSize(244, 156);
        }
        this.scroll.guiLeft = this.guiLeft + 6;
        this.scroll.guiTop = this.guiTop + 26;
        this.scroll.setList(this.options);
        if (this.current != null && !this.current.isBlank()) {
            this.scroll.setSelected(this.current);
            this.scroll.scrollTo(this.current);
        }
        this.addScroll(this.scroll);

        this.addButton(new GuiButtonNop(this, ID_CLEAR, this.guiLeft + 6, this.guiTop + 192,
                90, 20, "cnpccombat.gui.clearYsm"));
        this.addButton(new GuiButtonNop(this, ID_DONE, this.guiLeft + 190, this.guiTop + 192,
                60, 20, "gui.done"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ID_CLEAR) {
            this.onPick.accept(null);
            this.close();
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
            this.onPick.accept(selected);
        }
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scrolled) {
        if (selection != null && !selection.isBlank()) {
            this.onPick.accept(selection);
            this.close();
        }
    }
}

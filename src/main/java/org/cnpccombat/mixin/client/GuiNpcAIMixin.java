package org.cnpccombat.mixin.client;

import noppes.npcs.client.gui.mainmenu.GuiNpcAI;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.api.NpcAnimGroupData;
import org.cnpccombat.client.gui.SubGuiAttackAnimGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 NPC 魔杖 -> AI 面板的"移动设置"按钮下面加一个"攻击动画组"按钮。
 *
 * <p>CNPC 原有布局：标签 {@code ai.movement} 在 (guiLeft+4, guiTop+165)，
 * 按钮 id 2 在 (guiLeft+86, guiTop+160, 60x20)。我们放在它下面一行，
 * 标签 guiTop+185 / 按钮 guiTop+180。背景高 200，按钮底边刚好齐平。
 * CNPC 的 GUI 栈没有任何 scissor 裁剪，所以即使略微出界也只是压在背景外沿，不会被切掉。
 *
 * <p>widget id 选 240/241，远离 CNPC 已占用的按钮 id
 * (0/1/2/5/6/7/9/10/15/16/17/23) 和标签 id (0/1/2/10/11/12/13/14/17/19/21/23/25)，
 * 避免覆盖它们在 GuiWrapper 的 map 里的条目。
 *
 * <p>这里 {@code extends GuiNPCInterface2}（目标类的父类）以便直接使用
 * {@code guiLeft}/{@code guiTop}/{@code addButton}/{@code addLabel}/{@code setSubGui}。
 * 不能用 {@code @Shadow} 拿它们 —— 那些成员声明在祖父类 {@code GuiBasic} 上，
 * {@code @Shadow} 只在目标类自身查找，会得到 "Cannot find target for @Shadow" 并在
 * 运行时抛 InvalidAccessorException。
 *
 * <p><b>remap 规则（本项目踩过的坑）：</b>{@code init} 在 jar 字节码里是 vanilla
 * {@code Screen.init} 的 override（SRG {@code m_7856_}），必须保留默认 remap 并在
 * build.gradle 里手工补 refmap；{@code buttonEvent} 是 CNPC 自有方法名，必须
 * {@code remap = false}。写错会导致注入静默不命中、不报错、诊断 0 条。
 */
@Mixin(GuiNpcAI.class)
public abstract class GuiNpcAIMixin extends GuiNPCInterface2 {
    @Unique
    private static final int CNPC$BUTTON_ID = 240;

    @Unique
    private static final int CNPC$LABEL_ID = 241;

    /**
     * 本行按钮的 y 偏移。CNPC 的"移动设置"按钮在 guiTop+160，
     * 面板各行标准间距是 25px，所以这里用 185 让两行不挤在一起。
     * 背景高 200，按钮底边到 205 —— CNPC 的 GUI 栈没有 scissor 裁剪
     * （已 grep 确认无 enableScissor/m_280588_），只是压在背景外沿一点，不会被切掉。
     * 同目录的 SubGuiNpcMovement 也把完成按钮放在 guiTop+190 而 imageHeight 只有 216，
     * 属于 CNPC 自己的既有惯例。
     */
    @Unique
    private static final int CNPC$ROW_Y = 185;

    @Shadow(remap = false)
    private DataAI ai;

    /**
     * 仅为满足编译期"子类必须调用父类构造"的要求。
     * Mixin 不会把构造器合并进目标类，所以这段代码永远不会真的执行。
     */
    private GuiNpcAIMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpc$addAnimGroupButton(CallbackInfo ci) {
        try {
            // 用 4 参重载 (id, s, x, y)：它内部走
            // CustomNpcResourceListener.getDefaultTextColor()，会跟随 CNPC 主题色。
            // 不能用 5 参的 tooltip 重载 —— 那个重载把 height 设成 10，
            // AbstractWidget 会在文字左侧渲染出一个 10px 可聚焦方块（看起来像乱码块）。
            // 也不要硬编码 0xFFFFFF：CNPC 的面板背景是浅色的，白字看不清。
            this.addLabel(new GuiLabel(
                    CNPC$LABEL_ID,
                    "cnpccombat.gui.attackAnimGroup",
                    this.guiLeft + 4,
                    this.guiTop + CNPC$ROW_Y + 6
            ));
            this.addButton(new GuiButtonNop(
                    (IGuiInterface) this,
                    CNPC$BUTTON_ID,
                    this.guiLeft + 86,
                    this.guiTop + CNPC$ROW_Y,
                    60,
                    20,
                    "selectServer.edit"
            ));
        } catch (Throwable t) {
            CnpcCombat.LOGGER.error("向 AI 面板添加攻击动画组按钮失败", t);
        }
    }

    /**
     * {@code buttonEvent} 里有提前 return，所以只能 HEAD + cancellable。
     * 只拦我们自己的 id，其余原样放行给 CNPC。
     */
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$openAnimGroupGui(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || button.id != CNPC$BUTTON_ID) {
            return;
        }
        ci.cancel();
        if (this.ai instanceof NpcAnimGroupData data) {
            this.setSubGui(new SubGuiAttackAnimGroup(data));
        }
    }
}

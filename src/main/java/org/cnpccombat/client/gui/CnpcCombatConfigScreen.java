package org.cnpccombat.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.cnpccombat.config.CnpcCombatConfig;

/**
 * Mod 列表里的 Config 屏幕。
 *
 * <p>Forge 只有在注册了 {@code ConfigScreenHandler.ConfigScreenFactory} 扩展点时
 * 才会点亮 Mod 列表的 Config 按钮（否则按钮是黑的）。本屏幕直接读写
 * {@link CnpcCombatConfig#COMMON_SPEC}，关闭时写回 toml；运行中的读取都是
 * {@code .get()} 热读，所以改动立即生效、无需重启。
 */
public final class CnpcCombatConfigScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int ROW = 24;

    private final Screen parent;

    public CnpcCombatConfigScreen(Screen parent) {
        super(Component.literal("CNPC BetterCombat 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (this.width - WIDTH) / 2;
        int y = this.height / 2 - 72;

        addRenderableWidget(toggle(CnpcCombatConfig.COMMON.disableFriendlyFire, "关闭 NPC 友军伤害", x, y));
        addRenderableWidget(toggle(CnpcCombatConfig.COMMON.enableSweep, "NPC 横扫", x, y + ROW));
        addRenderableWidget(new IntSlider(x, y + ROW * 2, "横扫额外目标数",
                CnpcCombatConfig.COMMON.sweepExtraTargetCount, 1, 16));
        addRenderableWidget(new DoubleSlider(x, y + ROW * 3, "横扫最大伤害惩罚",
                CnpcCombatConfig.COMMON.sweepMaxDamagePenalty));
        addRenderableWidget(toggle(CnpcCombatConfig.COMMON.enableWeaponTrails, "刀光粒子", x, y + ROW * 4));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, y + ROW * 5 + 8, WIDTH, 20).build());
    }

    private static Button toggle(ForgeConfigSpec.BooleanValue value, String name, int x, int y) {
        return Button.builder(label(name, value.get()), button -> {
            value.set(!value.get());
            button.setMessage(label(name, value.get()));
        }).bounds(x, y, WIDTH, 20).build();
    }

    private static Component label(String name, boolean enabled) {
        return Component.literal(name + ": " + (enabled ? "开" : "关"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        // 编辑期间只改内存值（.get() 立即生效），关闭时统一落盘。
        CnpcCombatConfig.COMMON_SPEC.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final ForgeConfigSpec.IntValue configValue;
        private final String name;
        private final int min;
        private final int max;

        IntSlider(int x, int y, String name, ForgeConfigSpec.IntValue configValue, int min, int max) {
            super(x, y, WIDTH, 20, Component.empty(),
                    (configValue.get() - min) / (double) (max - min));
            this.configValue = configValue;
            this.name = name;
            this.min = min;
            this.max = max;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (this.configValue != null) {
                setMessage(Component.literal(this.name + ": " + this.configValue.get()));
            }
        }

        @Override
        protected void applyValue() {
            this.configValue.set(this.min + (int) Math.round(this.value * (this.max - this.min)));
        }
    }

    private static final class DoubleSlider extends AbstractSliderButton {
        private final ForgeConfigSpec.DoubleValue configValue;

        DoubleSlider(int x, int y, String name, ForgeConfigSpec.DoubleValue configValue) {
            super(x, y, WIDTH, 20, Component.empty(), configValue.get());
            this.configValue = configValue;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (this.configValue != null) {
                setMessage(Component.literal("横扫最大伤害惩罚: " + this.configValue.get()));
            }
        }

        @Override
        protected void applyValue() {
            this.configValue.set(Math.round(this.value * 20.0D) / 20.0D);
        }
    }
}

package org.cnpccombat.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON 类型配置：文件落 {@code config/cnpccombat.toml}（两侧均可读取）。
 * Forge 内建 FileWatcher 监听该文件——游戏运行中直接改 toml 保存即生效，无需重启。
 * 所有使用点均通过 {@code .get()} 热读，无静态缓存。
 */
public final class CnpcCombatConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    private CnpcCombatConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue disableFriendlyFire;
        public final ForgeConfigSpec.BooleanValue enableSweep;
        public final ForgeConfigSpec.IntValue sweepExtraTargetCount;
        public final ForgeConfigSpec.DoubleValue sweepMaxDamagePenalty;
        public final ForgeConfigSpec.BooleanValue enableWeaponTrails;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.comment("热加载：游戏运行中直接修改本文件并保存，即时生效，无需重启。").push("combat");
            disableFriendlyFire = builder
                    .comment("关闭 NPC Better Combat 攻击的友军伤害。")
                    .comment("玩家目标：仅在 NPC 阵营对玩家敌对时才可被伤害。")
                    .comment("NPC 目标：仅在异阵营且攻击方敌对列表包含目标阵营时才可被伤害。")
                    .comment("热加载：修改保存后即时生效。")
                    .define("disableFriendlyFire", true);
            enableSweep = builder
                    .comment("开启 NPC 近战攻击的 Better Combat 横扫范围伤害（sweep AOE）。")
                    .comment("武器攻击范围与攻击角度内的额外目标会受到横扫伤害。")
                    .comment("热加载：修改保存后即时生效。")
                    .define("enableSweep", true);
            sweepExtraTargetCount = builder
                    .comment("达到最大横扫伤害惩罚前可命中的额外目标数量。")
                    .comment("热加载：修改保存后即时生效。")
                    .defineInRange("sweepExtraTargetCount", 4, 1, 16);
            sweepMaxDamagePenalty = builder
                    .comment("命中大量横扫目标时的最大伤害惩罚（0.5 = 伤害降低 50%）。")
                    .comment("热加载：修改保存后即时生效。")
                    .defineInRange("sweepMaxDamagePenalty", 0.5D, 0.0D, 1.0D);
            enableWeaponTrails = builder
                    .comment("玩家与 NPC 近战挥击时显示刀光粒子（移植自 Better Combat 1.21.1）。")
                    .comment("热加载：修改保存后即时生效。")
                    .define("enableWeaponTrails", true);
            builder.pop();
        }
    }
}

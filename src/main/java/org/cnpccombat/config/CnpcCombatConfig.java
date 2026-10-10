package org.cnpccombat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config. File: config/cnpccombat-common.toml.
 *
 * <p>CNPCplus-Fix pattern: COMMON type + getters that read the live
 * ConfigValue every call. The NeoForge Mods -> Config screen edits the
 * in-memory values directly, so changes apply immediately in-game
 * (hot reload, no restart, no world reload).
 */
public final class CnpcCombatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * When true (default):
     * - Player attacking an NPC: only lands if the NPC faction is aggressive to that player.
     * - NPC attacking an NPC: only lands if factions differ and the attacker faction is hostile to the target faction.
     * - NPC attacking a player: only lands if the NPC faction is NOT friendly to that player.
     */
    public static final ModConfigSpec.BooleanValue DISABLE_FRIENDLY_FIRE = BUILDER
            .comment(
                    "关闭涉及 NPC 的 BetterCombat 风格友军伤害（默认开启）",
                    "玩家打 NPC：只有该 NPC 阵营对玩家敌对时才会掉血",
                    "NPC 打 NPC：只有阵营不同且攻击方阵营敌对目标阵营时才会掉血",
                    "NPC 打玩家：只有该 NPC 阵营不友好该玩家时才会掉血",
                    "游戏内 Mods -> 本模组 -> Config 可直接修改，改完即时生效"
            )
            .define("disableFriendlyFire", true);

    public static final ModConfigSpec.BooleanValue NPC_SWEEPING_ENABLED = BUILDER
            .comment(
                    "NPC 触发 BetterCombat 横扫（范围群体伤害）（默认开启）",
                    "关闭后 NPC 攻击只会命中主目标，不再横扫额外敌人",
                    "游戏内 Mods -> 本模组 -> Config 可直接修改，改完即时生效"
            )
            .define("npcSweepingEnabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CnpcCombatConfig() {
    }

    public static boolean disableFriendlyFire() {
        return DISABLE_FRIENDLY_FIRE.get();
    }

    public static boolean npcSweepingEnabled() {
        return NPC_SWEEPING_ENABLED.get();
    }
}

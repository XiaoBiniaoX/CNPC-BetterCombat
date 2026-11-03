package org.cnpccombat.logic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.config.CnpcCombatConfig;

public final class NpcFactionHelper {
    private NpcFactionHelper() {
    }

    /**
     * Config-gated friendly fire filter for NPC attacker.
     * Returns true if the attack is allowed to deal damage.
     *
     * <p>When {@code disableFriendlyFire} is enabled (default):
     * <ul>
     *   <li>Player target: allowed only if NPC faction is aggressive or neutral to that player.</li>
     *   <li>NPC target: allowed only if different faction AND attacker faction is hostile to target faction.</li>
     *   <li>Non-NPC attacker (shouldn't reach here for this path): allowed.</li>
     * </ul>
     */
    public static boolean canDamage(Mob attacker, LivingEntity target) {
        if (!CnpcCombatConfig.COMMON.disableFriendlyFire.get()) {
            return true;
        }
        if (!(attacker instanceof EntityNPCInterface npc)) {
            return true;
        }
        Faction faction = npc.getFaction();
        if (faction == null) {
            return true;
        }

        if (target instanceof EntityNPCInterface targetNpc) {
            Faction targetFaction = targetNpc.getFaction();
            if (targetFaction == null) {
                return true;
            }
            if (faction.id == targetFaction.id) {
                return false;
            }
            return faction.attackFactions.contains(targetFaction.id);
        }

        if (target instanceof Player player) {
            // NPC 已锁定该玩家（激怒反击 / 脚本 setTarget）→ 放行。
            // 中立 NPC 被玩家激怒后 CNPC 靠 setTarget 反击，阵营点数仍是"中立"，
            // 静态 isAggressiveToPlayer 会误判为友伤并吞掉伤害；原版 CNPC 无阵营过滤，
            // AI 选中目标即造成伤害。这里对齐：只要 AI 当前目标就是该玩家即允许。
            if (npc.getTarget() == player) {
                return true;
            }
            // 非当前目标（如横扫扫到的路过玩家）才按静态阵营点数过滤，保留友伤保护。
            return faction.isAggressiveToPlayer(player);
        }

        return true;
    }
}

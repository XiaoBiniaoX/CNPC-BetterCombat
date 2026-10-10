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
            // Friendly or neutral players cannot be damaged by this NPC.
            // CNPC: points >= friendlyPoints => friendly; points >= neutralPoints => neutral.
            // Aggressive means points < neutralPoints (per Faction.isAggressiveToPlayer).
            return faction.isAggressiveToPlayer(player);
        }

        return true;
    }
}

package org.cnpccombat.logic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.config.CnpcCombatConfig;

/**
 * Friendly-fire rules for Better Combat style attacks involving CNPC entities.
 *
 * <p>CNPC natively blocks (EntityNPCInterface.hurt:734-744):
 * same-faction NPC->NPC always, and friendly-faction Player->NPC always.
 * This guard extends that when the config switch is on:
 * - Player -> NPC: only if the NPC faction is aggressive to the player (neutral also protected).
 * - NPC -> NPC: only if factions differ AND the attacker faction is hostile to the target faction.
 * - NPC -> Player: only if the NPC faction is NOT friendly to the player.
 * Everything else (mob vs mob, mob vs player, etc.) passes vanilla behavior.
 */
public final class FriendlyFireGuard {
    private FriendlyFireGuard() {
    }

    /**
     * @return true if the attack from {@code attacker} onto {@code target} is allowed.
     */
    public static boolean canDamage(LivingEntity attacker, LivingEntity target) {
        if (!CnpcCombatConfig.disableFriendlyFire()) {
            return true;
        }
        if (attacker instanceof EntityNPCInterface npcAttacker) {
            if (target instanceof Player player) {
                return !npcAttacker.faction.isFriendlyToPlayer(player);
            }
            if (target instanceof EntityNPCInterface npcTarget) {
                return npcAttacker.faction.id != npcTarget.faction.id
                        && npcAttacker.faction.hostileToFaction(npcTarget.faction.id);
            }
            return true;
        }
        if (attacker instanceof Player player && target instanceof EntityNPCInterface npcTarget) {
            return npcTarget.faction.isAggressiveToPlayer(player);
        }
        return true;
    }
}

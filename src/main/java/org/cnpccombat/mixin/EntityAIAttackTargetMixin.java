package org.cnpccombat.mixin;

import net.minecraft.world.entity.Mob;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataMelee;
import org.cnpccombat.logic.NpcCombatLogic;
import org.cnpccombat.logic.NpcCombatMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AI cooldown = weapon attack duration × (CNPC melee delay / 20).
 */
@Mixin(EntityAIAttackTarget.class)
public abstract class EntityAIAttackTargetMixin {
    @Shadow
    private EntityNPCInterface npc;

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/entity/data/DataMelee;getDelay()I"
            )
    )
    private int cnpc$combinedAttackInterval(DataMelee melee) {
        Mob mob = this.npc;
        if (NpcCombatLogic.isEligible(mob)) {
            return NpcCombatMath.attackIntervalForMob(mob);
        }
        return melee.getDelay();
    }
}

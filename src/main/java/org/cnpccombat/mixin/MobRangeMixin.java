package org.cnpccombat.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.logic.NpcCombatLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobRangeMixin {
    @Inject(method = "isWithinMeleeAttackRange", at = @At("RETURN"), cancellable = true)
    private void cnpc$extendNpcRange(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof EntityNPCInterface)) {
            return;
        }
        Mob mob = (Mob) (Object) this;
        if (NpcCombatLogic.isEligible(mob)) {
            cir.setReturnValue(NpcCombatLogic.isWithinWeaponRange(mob, target));
        }
    }
}

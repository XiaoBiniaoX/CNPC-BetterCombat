package org.cnpccombat.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes LivingEntity.useItemRemaining so the NPC damage path can
 * fast-forward an already-started shield raise (1.21.1 isBlocking needs
 * at least 5 use ticks: getUseDuration - useItemRemaining >= 5).
 */
@Mixin(LivingEntity.class)
public interface LivingUseItemAccessor {
    @Accessor("useItemRemaining")
    void cnpccombat$setUseItemRemaining(int value);
}

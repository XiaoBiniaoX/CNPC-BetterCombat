package org.cnpccombat.api;

import net.bettercombat.api.AttackHand;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface NpcCombatState {
    int cnpc$getComboCount();

    void cnpc$setComboCount(int comboCount);

    int cnpc$getAttackCooldown();

    void cnpc$setAttackCooldown(int ticks);

    int cnpc$getWindupTicks();

    void cnpc$setWindupTicks(int ticks);

    int cnpc$getComboResetTicks();

    void cnpc$setComboResetTicks(int ticks);

    @Nullable
    AttackHand cnpc$getPendingAttack();

    void cnpc$setPendingAttack(@Nullable AttackHand attack);

    int cnpc$getIntendedTargetId();

    void cnpc$setIntendedTargetId(int entityId);

    boolean cnpc$isCallingVanillaAttack();

    void cnpc$setCallingVanillaAttack(boolean calling);

    void cnpc$beginCombatAttack(Entity intendedTarget);

    void cnpc$tickCombatAttack();
}

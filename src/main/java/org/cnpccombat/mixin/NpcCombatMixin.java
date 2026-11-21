package org.cnpccombat.mixin;

import net.bettercombat.api.AttackHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.EventHooks;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.api.NpcCombatState;
import org.cnpccombat.logic.NpcCombatLogic;
import org.cnpccombat.logic.NpcRangedFlow;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityNPCInterface.class)
public abstract class NpcCombatMixin extends LivingEntity implements NpcCombatState {
    @Unique private int cnpc$comboCount;
    @Unique private int cnpc$attackCooldown;
    @Unique private int cnpc$windupTicks;
    @Unique private int cnpc$comboResetTicks;
    @Unique private int cnpc$intendedTargetId = -1;
    @Unique private boolean cnpc$callingVanillaAttack;
    @Unique @Nullable private AttackHand cnpc$pendingAttack;
    @Unique private float cnpc$meleeEventDamage;

    protected NpcCombatMixin(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpc$tickCombat(CallbackInfo ci) {
        if (!this.level().isClientSide) {
            this.cnpc$tickCombatAttack();
            // 非战斗时把弩预先上好弹，避免第一次交战白等一轮上弹。
            NpcRangedFlow.tickKeepLoaded((EntityNPCInterface) (Object) this);
        }
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void cnpc$replaceMelee(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (this.cnpc$callingVanillaAttack) {
            return;
        }
        Mob mob = (Mob) (Object) this;
        if (NpcCombatLogic.isEligible(mob)) {
            NpcCombatLogic.beginAttack(mob, target);
            cir.setReturnValue(false);
            return;
        }
        NpcCombatLogic.playFallbackMeleeAnimation(mob);
    }

    @Redirect(method = "doHurtTarget", at = @At(value = "INVOKE", remap = false,
            target = "Lnoppes/npcs/EventHooks;onNPCAttacksMelee(Lnoppes/npcs/entity/EntityNPCInterface;Lnoppes/npcs/api/event/NpcEvent$MeleeAttackEvent;)Z"))
    private boolean cnpc$scaleMeleeEvent(EntityNPCInterface npc, NpcEvent.MeleeAttackEvent event) {
        if (!this.cnpc$callingVanillaAttack) {
            return EventHooks.onNPCAttacksMelee(npc, event);
        }
        event.damage = NpcCombatLogic.scaleAttackDamage(event.damage, this.cnpc$pendingAttack);
        boolean cancelled = EventHooks.onNPCAttacksMelee(npc, event);
        this.cnpc$meleeEventDamage = event.damage;
        return cancelled;
    }

    @ModifyArg(method = "doHurtTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float cnpc$useMeleeEventDamage(float damage) {
        // BC 只命中 LivingEntity，一定先触发近战事件。CNPC+ 会再次覆盖局部伤害值。
        return this.cnpc$callingVanillaAttack ? this.cnpc$meleeEventDamage : damage;
    }

    @Override
    public int cnpc$getComboCount() {
        return this.cnpc$comboCount;
    }

    @Override
    public void cnpc$setComboCount(int comboCount) {
        this.cnpc$comboCount = Math.max(0, comboCount);
    }

    @Override
    public int cnpc$getAttackCooldown() {
        return this.cnpc$attackCooldown;
    }

    @Override
    public void cnpc$setAttackCooldown(int ticks) {
        this.cnpc$attackCooldown = Math.max(0, ticks);
    }

    @Override
    public int cnpc$getWindupTicks() {
        return this.cnpc$windupTicks;
    }

    @Override
    public void cnpc$setWindupTicks(int ticks) {
        this.cnpc$windupTicks = Math.max(0, ticks);
    }

    @Override
    public int cnpc$getComboResetTicks() {
        return this.cnpc$comboResetTicks;
    }

    @Override
    public void cnpc$setComboResetTicks(int ticks) {
        this.cnpc$comboResetTicks = Math.max(0, ticks);
    }

    @Override
    public @Nullable AttackHand cnpc$getPendingAttack() {
        return this.cnpc$pendingAttack;
    }

    @Override
    public void cnpc$setPendingAttack(@Nullable AttackHand attack) {
        this.cnpc$pendingAttack = attack;
    }

    @Override
    public int cnpc$getIntendedTargetId() {
        return this.cnpc$intendedTargetId;
    }

    @Override
    public void cnpc$setIntendedTargetId(int entityId) {
        this.cnpc$intendedTargetId = entityId;
    }

    @Override
    public boolean cnpc$isCallingVanillaAttack() {
        return this.cnpc$callingVanillaAttack;
    }

    @Override
    public void cnpc$setCallingVanillaAttack(boolean calling) {
        this.cnpc$callingVanillaAttack = calling;
    }

    @Override
    public void cnpc$beginCombatAttack(Entity intendedTarget) {
        NpcCombatLogic.beginAttack((Mob) (Object) this, intendedTarget);
    }

    @Override
    public void cnpc$tickCombatAttack() {
        NpcCombatLogic.tick((Mob) (Object) this);
    }
}

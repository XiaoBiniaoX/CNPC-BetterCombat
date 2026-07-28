package org.cnpccombat.mixin.client;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.animation.AttackAnimationSubStack;
import net.bettercombat.client.animation.CustomAnimationPlayer;
import net.bettercombat.client.animation.PoseSubStack;
import net.bettercombat.client.animation.modifier.TransmissionSpeedModifier;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.api.NpcAnimationAccess;
import org.cnpccombat.logic.NpcAttackSelector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mixin(EntityNPCInterface.class)
public abstract class ClientNpcAnimationMixin extends LivingEntity implements NpcAnimationAccess, IAnimatedPlayer {
    @Unique
    private static final Set<ResourceLocation> CNPC$MISSING = new HashSet<>();

    @Unique
    private final Map<ResourceLocation, IAnimation> cnpc$associated = new HashMap<>();

    @Unique
    private final AnimationStack cnpc$stack = new AnimationStack();

    @Unique
    private final AnimationApplier cnpc$applier = new AnimationApplier(this.cnpc$stack);

    @Unique
    private final PoseSubStack cnpc$mainHandItemPose = new PoseSubStack(null, false, true);

    @Unique
    private final PoseSubStack cnpc$mainHandBodyPose = new PoseSubStack(null, true, true);

    @Unique
    private final PoseSubStack cnpc$offHandItemPose = new PoseSubStack(null, false, true);

    @Unique
    private final PoseSubStack cnpc$offHandBodyPose = new PoseSubStack(null, true, false);

    @Unique
    private final AttackAnimationSubStack cnpc$attackAnimation =
            new AttackAnimationSubStack(new AdjustmentModifier(this::cnpc$attackAdjustment));

    @Unique
    private float cnpc$renderPartialTick;

    @Unique
    private int cnpc$attackVisualTicks;

    @Unique
    private boolean cnpc$twoHandedAttack;

    @Unique
    private boolean cnpc$weaponBodyPoseActive;

    protected ClientNpcAnimationMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cnpc$initStack(EntityType<?> type, Level level, CallbackInfo ci) {
        if (level.isClientSide) {
            this.cnpc$stack.addAnimLayer(1, this.cnpc$offHandItemPose.base);
            this.cnpc$stack.addAnimLayer(2, this.cnpc$offHandBodyPose.base);
            this.cnpc$stack.addAnimLayer(3, this.cnpc$mainHandItemPose.base);
            this.cnpc$stack.addAnimLayer(4, this.cnpc$mainHandBodyPose.base);
            this.cnpc$stack.addAnimLayer(2000, this.cnpc$attackAnimation.base);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpc$tickStack(CallbackInfo ci) {
        if (this.level().isClientSide) {
            this.cnpc$stack.tick();
            if (this.cnpc$attackVisualTicks > 0) {
                this.cnpc$attackVisualTicks--;
                if (this.cnpc$attackVisualTicks == 0) {
                    this.cnpc$twoHandedAttack = false;
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cnpc$refreshPoses(CallbackInfo ci) {
        if (this.level().isClientSide) {
            this.cnpc$updateWeaponPoses();
        }
    }

    @Override
    public AnimationStack cnpc$getAnimationStack() {
        return this.cnpc$stack;
    }

    @Override
    public AnimationStack getAnimationStack() {
        return this.cnpc$stack;
    }

    @Override
    public AnimationApplier playerAnimator_getAnimation() {
        return this.cnpc$applier;
    }

    @Override
    public @Nullable IAnimation playerAnimator_getAnimation(@NotNull ResourceLocation id) {
        return this.cnpc$associated.get(id);
    }

    @Override
    public @Nullable IAnimation playerAnimator_setAnimation(
            @NotNull ResourceLocation id,
            @Nullable IAnimation animation
    ) {
        return animation == null
                ? this.cnpc$associated.remove(id)
                : this.cnpc$associated.put(id, animation);
    }

    @Override
    public float cnpc$getRenderPartialTick() {
        return this.cnpc$renderPartialTick;
    }

    @Override
    public void cnpc$setRenderPartialTick(float partialTick) {
        this.cnpc$renderPartialTick = partialTick;
    }

    @Override
    public boolean cnpc$isAttackAnimationActive() {
        return this.cnpc$attackVisualTicks > 0
                && this.cnpc$attackAnimation.base.getAnimation() != null;
    }

    @Override
    public boolean cnpc$isArmAnimationActive() {
        return this.cnpc$isAttackAnimationActive() || this.cnpc$weaponBodyPoseActive;
    }

    @Override
    public void cnpc$playAttackAnimation(
            String animationId,
            boolean offHand,
            boolean twoHanded,
            float length,
            float animationUpswing,
            float damageUpswing
    ) {
        if (!this.level().isClientSide) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(animationId);
        if (id == null) {
            return;
        }
        KeyframeAnimation animation = this.cnpc$getKeyframe(id);
        if (animation == null) {
            return;
        }

        try {
            this.cnpc$clearWeaponPoses(((Mob) (Object) this).isLeftHanded());
            this.cnpc$twoHandedAttack = twoHanded;

            // Mirror Better Combat player AbstractClientPlayerEntityMixin.playAttackAnimation
            // so the authored damage frame lands on the same tick as server impact.
            KeyframeAnimation.AnimationBuilder copy = animation.mutableCopy();
            copy.torso.fullyEnablePart(true);
            copy.head.pitch.setEnabled(false);

            float safeLength = Math.max(1.0F, length);
            this.cnpc$attackVisualTicks = Math.max(1, Math.round(safeLength));
            // animationUpswing is already hand.upswingRate() = attack.upswing * config.upswing_multiplier
            float upswing = Mth.clamp(animationUpswing, 0.01F, 0.99F);
            float speed = Math.max(0.01F, (float) animation.endTick / safeLength);

            // Recover config multiplier via reflection (avoid compile dep on cloth ConfigData)
            float upswingMultiplier = cnpc$readUpswingMultiplier();
            float trueUpswingRatio = upswing / Math.max(0.2F, upswingMultiplier);
            float upswingSpeed = Math.max(0.01F, speed / Math.max(0.01F, trueUpswingRatio));
            float downwindSpeed = speed * Mth.lerp(
                    (float) (Math.max(upswingMultiplier - 0.5D, 0.0D) / 0.5D),
                    (1.0F - upswing),
                    upswing / Math.max(0.01F, 1.0F - upswing)
            );
            downwindSpeed = Math.max(0.01F, downwindSpeed);

            this.cnpc$attackAnimation.speed.set(
                    upswingSpeed,
                    List.of(
                            new TransmissionSpeedModifier.Gear(safeLength * upswing, downwindSpeed),
                            new TransmissionSpeedModifier.Gear(safeLength, speed)
                    )
            );

            boolean mirror = !twoHanded && (offHand ^ ((Mob) (Object) this).isLeftHanded());
            this.cnpc$attackAnimation.mirror.setEnabled(mirror);

            CustomAnimationPlayer player = new CustomAnimationPlayer(copy.build(), 0);
            player.setFirstPersonMode(FirstPersonMode.NONE);
            int fadeIn = Math.max(0, copy.beginTick);
            this.cnpc$attackAnimation.base.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(fadeIn, Ease.INOUTSINE),
                    player
            );
        } catch (RuntimeException e) {
            CnpcCombat.LOGGER.error("Failed to play NPC attack animation '{}'", id, e);
        }
    }

    @Unique
    private static float cnpc$readUpswingMultiplier() {
        try {
            Object config = Class.forName("net.bettercombat.BetterCombatMod")
                    .getMethod("getConfig")
                    .invoke(null);
            if (config != null) {
                Object value = config.getClass().getMethod("getUpswingMultiplier").invoke(config);
                if (value instanceof Number number) {
                    return Mth.clamp(number.floatValue(), 0.2F, 1.0F);
                }
            }
        } catch (Throwable ignored) {
        }
        return 0.5F;
    }

    @Unique
    private void cnpc$updateWeaponPoses() {
        Mob mob = (Mob) (Object) this;
        boolean leftHanded = mob.isLeftHanded();
        ItemStack mainHand = this.getMainHandItem();
        ItemStack offHand = this.getOffhandItem();

        if (this.cnpc$isAttackAnimationActive()
                || this.swinging
                || this.isSwimming()
                || this.isUsingItem()
                || this.isFallFlying()
                || CrossbowItem.isCharged(mainHand)
                || CrossbowItem.isCharged(offHand)
                || ((mainHand.getItem() instanceof ProjectileWeaponItem
                || offHand.getItem() instanceof ProjectileWeaponItem) && mob.isAggressive())) {
            this.cnpc$clearWeaponPoses(leftHanded);
            return;
        }

        WeaponAttributes mainAttributes = WeaponRegistry.getAttributes(mainHand);
        boolean twoHanded = mainAttributes != null && mainAttributes.isTwoHanded();
        boolean dual = NpcAttackSelector.isDualWielding(this);

        KeyframeAnimation mainPose = this.cnpc$getPose(mainAttributes == null ? null : mainAttributes.pose());
        KeyframeAnimation offPose = null;
        if (!twoHanded && dual) {
            WeaponAttributes offAttributes = WeaponRegistry.getAttributes(offHand);
            offPose = this.cnpc$getPose(offAttributes == null ? null : offAttributes.offHandPose());
        }

        boolean mirrorMain = !twoHanded && leftHanded;
        this.cnpc$mainHandItemPose.setPose(mainPose, mirrorMain);
        this.cnpc$offHandItemPose.setPose(offPose, leftHanded);

        KeyframeAnimation mainBody = mainPose;
        KeyframeAnimation offBody = offPose;
        boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 0.0009D;
        if (!twoHanded && (moving || this.isShiftKeyDown())) {
            mainBody = null;
            offBody = null;
        }
        this.cnpc$mainHandBodyPose.setPose(mainBody, mirrorMain);
        this.cnpc$offHandBodyPose.setPose(offBody, leftHanded);
        this.cnpc$weaponBodyPoseActive = mainBody != null || offBody != null;
    }

    @Unique
    private void cnpc$clearWeaponPoses(boolean leftHanded) {
        this.cnpc$weaponBodyPoseActive = false;
        this.cnpc$mainHandItemPose.setPose(null, leftHanded);
        this.cnpc$mainHandBodyPose.setPose(null, leftHanded);
        this.cnpc$offHandItemPose.setPose(null, leftHanded);
        this.cnpc$offHandBodyPose.setPose(null, leftHanded);
    }

    @Unique
    private KeyframeAnimation cnpc$getPose(String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(animationId);
        return id == null ? null : this.cnpc$getKeyframe(id);
    }

    @Unique
    private KeyframeAnimation cnpc$getKeyframe(ResourceLocation id) {
        var playable = PlayerAnimationRegistry.getAnimation(id);
        if (playable == null) {
            if (CNPC$MISSING.add(id)) {
                CnpcCombat.LOGGER.warn("Missing Better Combat animation '{}'", id);
            }
            return null;
        }
        if (!(playable instanceof KeyframeAnimation animation)) {
            if (CNPC$MISSING.add(id)) {
                CnpcCombat.LOGGER.warn("Unsupported animation type for '{}'", id);
            }
            return null;
        }
        return animation;
    }

    @Unique
    private Optional<AdjustmentModifier.PartModifier> cnpc$attackAdjustment(String partName) {
        float pitch = (float) Math.toRadians(this.getXRot());
        return switch (partName) {
            case "torso" -> Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(-pitch * 0.75F, 0.0F, 0.0F), Vec3f.ZERO));
            case "rightArm", "leftArm" -> Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(pitch * 0.25F, 0.0F, 0.0F), Vec3f.ZERO));
            case "rightLeg", "leftLeg" -> Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(-pitch * 0.75F, 0.0F, 0.0F), Vec3f.ZERO));
            default -> Optional.empty();
        };
    }
}

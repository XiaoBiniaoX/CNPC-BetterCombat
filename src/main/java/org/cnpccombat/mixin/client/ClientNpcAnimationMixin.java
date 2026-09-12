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
import net.bettercombat.BetterCombat;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.animation.AnimationRegistry;
import net.bettercombat.client.animation.AttackAnimationSubStack;
import net.bettercombat.client.animation.CustomAnimationPlayer;
import net.bettercombat.client.animation.PoseSubStack;
import net.bettercombat.client.animation.StateCollectionHelper;
import net.bettercombat.client.animation.modifier.TransmissionSpeedModifier;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
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
    private final AttackAnimationSubStack cnpc$attackAnimation =
            new AttackAnimationSubStack(new AdjustmentModifier(this::cnpc$attackAdjustment));

    /**
     * 渲染侧唯一的取值入口（{@code playerAnimator_getAnimation()}）。
     *
     * <p><b>★ 必须包住整个 {@link #cnpc$stack}，不能只包攻击层</b>
     * —— 这是"持握武器动作完全不生效"的根因（第14轮修）。
     *
     * <p>原先这里写的是 {@code new AnimationApplier(this.cnpc$attackAnimation.base)},
     * 只包了 2000 号那一层（攻击动画）。而持握姿态在 1~4 号层里
     * （{@code offHandItemPose}/{@code offHandBodyPose}/
     * {@code mainHandItemPose}/{@code mainHandBodyPose}）。
     *
     * <p>整条渲染链（{@code HumanoidModelMixin}、{@code PlayerModelMixinNpc}、
     * {@code LivingEntityRendererMixin}）都只经 {@code NpcAnimator.getAnimation()}
     * 读这一个 applier → 那 4 层<b>从来没有被采样过</b>，
     * 于是 {@code cnpc$updateWeaponPoses()} 每 tick 算出来的 pose 全部被丢弃。
     *
     * <p>对照 playerAnimator 的玩家实现（{@code PlayerEntityMixin.java:30-32}）：
     * <pre>
     *   private final AnimationStack   animationStack   = createAnimationStack();
     *   private final AnimationApplier animationApplier = new AnimationApplier(animationStack);
     * </pre>
     * 它包的就是**整个 stack**。BC 也正是靠这点，把 pose 层加进
     * {@code ((IAnimatedPlayer)this).getAnimationStack()} 就能生效。
     *
     * <p>{@code AnimationStack.get3DTransform} 会按优先级从低到高逐层叠加
     * （只取 {@code isActive()} 的层），所以攻击动画（2000）仍然压在
     * 持握姿态（1~4）之上，优先级语义不变。
     */
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
    private float cnpc$renderPartialTick;

    @Unique
    private int cnpc$attackVisualTicks;

    @Unique
    private boolean cnpc$twoHandedAttack;

    @Unique
    private boolean cnpc$weaponBodyPoseActive;

    /**
     * 攻击动画俯仰补偿的输入上限（度）。
     *
     * <p>与 {@code NpcCombatLogic} 里 {@code setLookAt(target, 30.0F, 30.0F)}
     * 的俯仰限幅取同一个值 —— 那是 NPC 正常追击时 xRot 的实际范围。
     * 超出这个范围（被其它 mod 改朝向、骑乘、脚本强制设 pitch）时夹住，
     * 避免异常输入被 0.75 的系数放大成夸张动作。
     */
    @Unique
    private static final float CNPC$MAX_PITCH_DEGREES = 30.0F;

    protected ClientNpcAnimationMixin(EntityType<? extends net.minecraft.world.entity.PathfinderMob> type, Level level) {
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

            // 持握姿态的 body 通道要按当前姿态裁剪（游泳/骑乘时腿不该被 pose 摆）。
            // BC 玩家侧也是只给这两个 body 通道挂 configure，
            // 见 AbstractClientPlayerEntityMixin:64-65。configure 是 public 字段。
            this.cnpc$mainHandBodyPose.configure = this::cnpc$configurePoseByActivity;
            this.cnpc$offHandBodyPose.configure = this::cnpc$configurePoseByActivity;
        }
    }

    /**
     * 按 NPC 当前姿态裁剪持握姿态动画的通道。
     *
     * <p>复刻 BC 玩家侧的 {@code updateAnimationByCurrentActivity}
     * （{@code AbstractClientPlayerEntityMixin:249-275}）：
     * 游泳或骑乘时把**腿部通道关掉**，否则持握姿态会去摆腿 ——
     * 骑马的 NPC 两条腿会脱离马鞍、游泳时腿会僵直。
     *
     * <p>只处理这两种姿态，与 BC 一致（它的 switch 其余分支都是空的）。
     */
    @Unique
    private void cnpc$configurePoseByActivity(KeyframeAnimation.AnimationBuilder animation) {
        if (this.isPassenger() || this.getPose() == net.minecraft.world.entity.Pose.SWIMMING) {
            StateCollectionHelper.configure(animation.rightLeg, false, false);
            StateCollectionHelper.configure(animation.leftLeg, false, false);
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

    /**
     * 当前 BC 攻击动画名，供 YSM 的 {@code ctrl.bcombat_attack_animation} 使用。
     *
     * <p>取法<b>完全照抄</b> YSM 的 {@code BetterCombatBinding.getAttackAnimationName}：
     * 从动画数据的 {@code extraData["name"]} 取，并且要求动画处于 active 状态。
     * 这样 NPC 返回的名字格式与玩家侧一致，模型作者的脚本无需区分。
     */
    @Override
    @Nullable
    public String cnpc$getCurrentAttackAnimation() {
        IAnimation current = this.cnpc$attackAnimation.base.getAnimation();
        if (!(current instanceof dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer player)) {
            return null;
        }
        if (!player.isActive()) {
            return null;
        }
        Object name = player.getData().extraData.getOrDefault("name", "");
        return name instanceof String s && !s.isEmpty() ? s : null;
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
            this.cnpc$clearWeaponPoses(this.cnpc$isLeftHanded());
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

            // Recover config multiplier (BetterCombat.config is public static in 1.9.0)
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

            boolean mirror = !twoHanded && (offHand ^ this.cnpc$isLeftHanded());
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
            return Mth.clamp(BetterCombat.config.getUpswingMultiplier(), 0.2F, 1.0F);
        } catch (Throwable ignored) {
        }
        return 0.5F;
    }

    @Unique
    private void cnpc$updateWeaponPoses() {
        Mob mob = (Mob) (Object) this;
        boolean leftHanded = this.cnpc$isLeftHanded();
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

        // ★★ 这里**不能**用 NpcAttackSelector.attributesFor()。
        //
        // 本方法跑在**客户端**，而 attributesFor 内部要查
        // AnimationGroupRegistry.RESOLVED —— 那张表**只在服务端填充**
        // （acceptFromServer 从不填它）。于是设了动画组的 NPC 在客户端
        // 恒取不到属性，持握姿态画不出来。
        //
        // 这正是"持握动作不连续、重进世界就失效"的根因之一：
        // 刚用 GUI 保存过时客户端 DataAI 里有残留值、看着是好的；
        // 重进世界实体重新 spawn，残留消失 -> 姿态再也不出现。
        //
        // poseFor / isTwoHandedPose 走的是随 S2C 同步的 POSES 表
        // + DataDisplay 上的动画组镜像，**双端都有数据**。
        boolean twoHanded = NpcAttackSelector.isTwoHandedPose(this, mainHand);
        boolean dual = NpcAttackSelector.isDualWielding(this);

        KeyframeAnimation mainPose = this.cnpc$getPose(NpcAttackSelector.poseFor(this, mainHand));
        KeyframeAnimation offPose = null;
        if (!twoHanded && dual) {
            // 副手仍按手中物品取：动画组覆盖是"整个实体一套动作"，
            // 覆盖生效时 isDualWielding 已经返回 false（见 NpcAttackSelector:69），
            // 所以走到这里必然没有覆盖，用 WeaponRegistry 是对的。
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
    private boolean cnpc$isLeftHanded() {
        return this.getMainArm() == HumanoidArm.LEFT;
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
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(id);
        if (animation == null) {
            // BetterCombat 1.9.0: 动画存在自己的 AnimationRegistry（String key），不注册到 PlayerAnimationRegistry
            animation = AnimationRegistry.animations.get(id.toString());
        }
        if (animation == null) {
            // ERROR 级：动画找不到意味着该武器的攻击动画完全播不出来（功能损坏）。
            // 已用 CNPC$MISSING 去重，每个 id 只报一次，不会刷屏。
            if (CNPC$MISSING.add(id)) {
                CnpcCombat.LOGGER.error("Missing Better Combat animation '{}'", id);
            }
            return null;
        }
        return animation;
    }

    /**
     * 攻击动画的俯仰补偿。<b>部位名必须与 BetterCombat 玩家侧逐字一致</b>。
     *
     * <p><b>★ 这里曾经是 "用力过猛 / NPC 头扎进地里" 的根因</b>，
     * 修复过程记在 findings.md「第14轮·二·勘误」，要点：
     *
     * <p>playerAnimator 把躯干分成**两个不同的部位**：
     * <ul>
     *   <li>{@code "body"} —— 整体位移/旋转，由 <b>PoseStack</b> 施加；
     *       实测 BC 的 30 个攻击动画<b>没有一个</b>驱动这个通道
     *       → 对攻击动画来说这一支是<b>空操作</b>；</li>
     *   <li>{@code "torso"} —— vanilla 的 {@code body} <b>ModelPart</b>，
     *       由 {@code updatePart("torso", model.body)} 施加；
     *       22 个攻击动画都在驱动它。</li>
     * </ul>
     *
     * <p>BC 玩家侧补的是 {@code "body"}（因此实际不生效，torso 只有动画本身的值）。
     * 我们最初凭"语义相同"把它写成了 {@code "torso"} —— 于是补偿真的生效，
     * 叠加到动画自带的前倾上：
     * <pre>
     *   two_handed_slam_heavy 动画自带 torso.pitch = -1.73 rad (-99deg)
     * + NPC 俯视目标时 xRot=30deg 的补偿      = -0.39 rad
     * = -2.12 rad (-121deg)  -> 躯干前倾超过 90 度，头随躯干转进地面
     * </pre>
     * 玩家不会这样：他那份补偿落在空操作的 {@code body} 上。
     *
     * <p>所以这里<b>改回 {@code "body"}</b>，与 BC 对齐；torso 只由动画驱动。
     */
    @Unique
    private Optional<AdjustmentModifier.PartModifier> cnpc$attackAdjustment(String partName) {
        // NPC 的俯仰由 CNPC 的 LookControl 驱动（NpcCombatLogic 用 setLookAt(target,30,30)），
        // 量级与玩家鼠标操作不同，先夹到 +-30 度再用，避免异常俯仰放大成夸张动作。
        float degrees = Mth.clamp(this.getXRot(), -CNPC$MAX_PITCH_DEGREES, CNPC$MAX_PITCH_DEGREES);
        float pitch = (float) Math.toRadians(degrees);
        return switch (partName) {
            // ★ 是 "body" 不是 "torso" —— 与 BetterCombat 玩家侧逐字一致。
            case "body" -> Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(-pitch * 0.75F, 0.0F, 0.0F), Vec3f.ZERO));
            case "rightArm", "leftArm" -> Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(pitch * 0.25F, 0.0F, 0.0F), Vec3f.ZERO));
            case "rightLeg", "leftLeg" -> Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(-pitch * 0.75F, 0.0F, 0.0F), Vec3f.ZERO));
            default -> Optional.empty();
        };
    }
}

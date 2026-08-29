package org.cnpccombat.compat.ysm;

import com.elfmcys.yesstevemodel.OO00O0o0OooOOOo00OO00o00;
import com.elfmcys.yesstevemodel.O00OoOOOOoOOOO0OOOoO0ooO;
import com.elfmcys.yesstevemodel.OOOO0O0O000O000000oOOO0o;
import com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo;
import com.elfmcys.yesstevemodel.Oo0o00oOOo0OO000000O0oO0;
import com.elfmcys.yesstevemodel.o000OO00oooOo00000ooo0Oo;
import com.elfmcys.yesstevemodel.ooO00Oo0oooOoO0OOOOoo0Oo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 用 YSM 模型渲染 CNPC 的 NPC。
 *
 * <p>直接继承 YSM 的 {@code GeoReplacedEntityRenderer}（混淆名
 * {@link OOoo0o0oO000ooO0Oo00OoOo}），泛型实参填 {@code LivingEntity} 与我们自己的
 * animatable。YSM 的车万女仆兼容用的就是同一个基类，所以这是官方支持的用法。
 *
 * <h2>为什么这样就不会 ClassCastException</h2>
 * 基类的泛型上界是 {@code LivingEntity}，本身零 Player 依赖。之前几轮之所以反复崩，
 * 是因为复用了 YSM 的<b>玩家</b>渲染器实例 —— 那个类把泛型实参绑成 {@code Player}，
 * 编译器为它生成的一批桥接方法（{@code m_7523_}/{@code m_6512_}/{@code m_7392_}…）
 * 第一条指令就是 {@code checkcast Player}。我们自己继承、泛型实参填 LivingEntity，
 * 编译器生成的桥接方法 checkcast 的就是 LivingEntity，从根上避开了这个问题。
 *
 * <h2>渲染层</h2>
 * 不挂 YSM 的披风/鹦鹉/鞘翅/装备层 —— 那 4 个层的泛型都绑玩家 animatable，
 * 挂上去会 ClassCastException（而且用户明确说不要盔甲覆盖）。
 * 持物层是唯一零 Player 依赖的，但它的泛型同样绑玩家 animatable，
 * 所以这里<b>自己实现</b>持物渲染（逻辑照搬 YSM 的持物层，全部只用 LivingEntity API）。
 *
 * <p><b>客户端专用</b>，引用 YSM 类型，只能在 {@link YsmCompat#isLoaded()} 为真时触碰。
 */
@OnlyIn(Dist.CLIENT)
public class YsmNpcRenderer
        extends OOoo0o0oO000ooO0Oo00OoOo<LivingEntity, YsmNpcAnimatable> {

    /** vanilla 的手持物渲染器，由渲染上下文提供。 */
    private final ItemInHandRenderer itemInHandRenderer;

    /** 当前帧要用的贴图。renderEntityWithTexture 会通过 getTextureLocation 回读。 */
    private ResourceLocation currentTexture;

    /** 当前帧的死亡染色强度（0~1），由 renderNpc 写入、getRenderColor 回读。 */
    private float dyingProgress;

    /** 上一帧是否处于死亡状态，用于只在状态**变化**时打日志。 */
    private boolean wasDyingLastFrame;

    public YsmNpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemInHandRenderer = context.getItemInHandRenderer();
    }

    /**
     * 渲染一个 NPC。
     *
     * <p><b>yaw 传 0</b>：NPC 的朝向已经由 CNPC 自己的渲染器施加到 PoseStack 上了，
     * 再传一次实体 yaw 会导致二次旋转（表现为模型左右反了）。这是前几轮踩过的坑。
     *
     * <p><b>不再触碰 YSM 的 first-person 全局标记</b>（前两轮都在这上面栽了）：
     * 那个标记由 YSM 在 {@code renderLevel} 的两个注入点控制，
     * 实测 vanilla 字节码里 {@code FogRenderer.setupColor}(置 true) 在偏移 315、
     * {@code RenderType.entitySolid}(置 false) 在偏移 514，
     * 而**实体渲染发生在 514 之后** → 正常渲染实体时它本来就是 false。
     * 玩家死亡打断 renderLevel 时它才会残留成 true。
     *
     * <p>与其去猜/改那个全局值，不如让我们自己的 animatable **不依赖它** ——
     * {@link YsmNpcAnimatable} 重写了 {@code shouldSkipAnimation()} 固定返回 true，
     * 使动画推进条件恒定成立。详见那个方法的注释。
     */
    public void renderNpc(YsmNpcAnimatable animatable, float partialTick,
                          PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        // 修掉"渲染中断/低帧率"造成的动画时钟落后。
        // 必须在 processAnimation 之前调，详见该方法注释。
        animatable.setPartialTick(partialTick);
        animatable.syncAnimationClock();

        LivingEntity entity = animatable.OO00OOOOo0Ooo0oo0o0Oo0OO();   // getEntity()

        // 记录"这一帧要不要画成死亡红"，供 getRenderColor 回读。
        this.dyingProgress = entity == null ? 0.0F : deathTint(entity);

        // ★ 只在**进入死亡状态的那一刻**记录一条（取代旧的每帧 [death] 日志）。
        // 死亡相关的三个量语义各不相同且都有坑，
        // 详见 task_plan_ysm.md 第 5 节；这里留一条快照便于回溯。
        if (YsmDebug.enabled() && entity != null) {
            boolean dying = entity.deathTime > 0;
            if (dying != this.wasDyingLastFrame) {
                this.wasDyingLastFrame = dying;
                YsmDebug.once("[death] npc={} dying={} deathTime={} isKilled={} "
                                + "isAlive={} health={}",
                        entity.getId(), dying, entity.deathTime,
                        entity instanceof EntityNPCInterface npc ? npc.isKilled() : "n/a",
                        entity.isAlive(), entity.getHealth());
            }
        }

        poseStack.pushPose();
        // 死亡倒地：我们取消了 vanilla 的整段渲染，
        // 所以 LivingEntityRenderer.setupRotations 里那段 deathTime 翻转不会执行，
        // 必须自己补，否则 NPC 死了还站着（用户第六轮反馈）。
        applyDeathRotation(entity, poseStack, partialTick);

        // ★★★★★ 玩家死亡后 NPC 动画永久损坏的根因（第二十轮，日志+字节码双证）
        //
        // 用户症状：**玩家自己死亡后** NPC 动画变慢→变成无动画（保持待机、
        // 追击时直接平移）、此时杀死 NPC 也不倒地，而且**不可逆，只能大退游戏**。
        //
        // 机制：`processAnimation(partialTick)` 是 public final，内部这样调：
        //     processAnimation(partialTick, ModelPreviewRenderer.isFirstPersonOnRenderThread())
        // 而 `isFirstPersonOnRenderThread()` 读的是**静态全局**字段
        // `isFirstPersonMode`（混淆 OoO00Oo00Ooo0OoOoo00o000.O00OOOooOoooOoo0o0o0oO0O）。
        //
        // YSM 的 WorldRendererMixin 在 renderLevel 里成对设置它：
        //     FogRenderer.setupColor  (vanilla offset 315) -> setFirstPersonMode(true)
        //     RenderType.entitySolid  (vanilla offset 514) -> setFirstPersonMode(false)
        // **玩家死亡时死亡界面打断了 renderLevel**，后一个注入点不执行
        // → 标记永久残留 true → 之后每帧 event.isFirstPerson 都是 true
        // → 影响 setCustomAnimations 里的 z2/z3 → 动画不再逐帧求值
        // → 骨骼停在 bind pose（日志：[bones] 死后全部 0.314 完全相同）。
        // 这完美解释「不可逆、只能大退」：静态标记再也没人复位。
        //
        // 修法：在我们自己的渲染窗口内**显式置 false**（渲染实体时的正确值），
        // 出去时恢复原值 —— 既不依赖外部是否被打断，也不干扰 YSM 自己的流程。
        // 注意日志证据同时排除了另外两个怀疑方向：
        //   · NPC 从未停止 tick（[clock] 死后仍 +20、skipped=0）
        //   · seekTime 速率死后仍是 1:1（ratio≈1.0），所以不是时钟问题
        boolean prevFirstPerson =
                com.elfmcys.yesstevemodel.OoO00Oo00Ooo0OoOoo00o000.O00OOOooOoooOoo0o0o0oO0O();
        com.elfmcys.yesstevemodel.OoO00Oo00Ooo0OoOoo00o000.O00OOOooOoooOoo0o0o0oO0O(false);
        try {
            // 每帧驱动动画时钟。少了这一步动画会停在第一帧。
            animatable.oOo0o0000OOOO0OooooO00oo();   // tickModel()
            this.currentTexture = animatable.b_();   // getTextureLocation()
            // renderEntityWithTexture(animatable, texture, yaw, partialTick, pose, buffer, light)
            this.Oo0Oo0o00O00Oo0OOoOOoooo(
                    animatable, this.currentTexture, 0.0F, partialTick,
                    poseStack, bufferSource, light);
        } finally {
            com.elfmcys.yesstevemodel.OoO00Oo00Ooo0OoOoo00o000
                    .O00OOOooOoooOoo0o0o0oO0O(prevFirstPerson);
            poseStack.popPose();
        }
    }

    /**
     * 复刻 vanilla {@code LivingEntityRenderer.setupRotations} 里的死亡倒地旋转。
     *
     * <p>vanilla 的算法：{@code deathTime > 0} 时绕 Z 轴旋转
     * {@code min(sqrt((deathTime + partialTick - 1) / 20 * 1.6), 1) * 90°}。
     * 我们只需要这一段 —— 朝向/身体旋转已经由 CNPC 的渲染器施加到 PoseStack 上了。
     */
    private static void applyDeathRotation(LivingEntity entity, PoseStack poseStack,
                                           float partialTick) {
        if (entity == null) {
            return;
        }
        // ★★★★★ 三个候选判据，前两个都试过并失败，现在用第三个：
        //
        //  1. `deathTime > 0`     —— 失败：**CNPC 会主动把它清零**。
        //     反编译 EntityNPCInterface（offset 76-138）：
        //         boolean flag = (wasKilled != isKilled()) && wasKilled;
        //         flag |= (deathTime > 0 && !isDeadOrDying());   // ★
        //         if (flag) { deathTime = 0; refreshDimensions(); }
        //     第二项意味着只要 deathTime>0 而 isDeadOrDying() 为假，就被抹成 0。
        //     日志实测死后全程 `deathTime=0` → 倒地旋转一次都不施加
        //     → 用户看到「死亡后是待机动画站在地上」。
        //
        //  2. `!isAlive()`        —— 失败：CNPC 的 isAlive() 不可靠，
        //     活着的 NPC 也可能返回 false（第十二轮：isAlive=false 但 health=19），
        //     结果「面朝地躺在地上还追人」。
        //
        //  3. `isKilled()`        —— 采用。它读的是 CNPC 自己的
        //     **同步数据** `IsDead`（SynchedEntityData），
        //     是服务端显式设置、客户端可靠可见的死亡标记。
        //     反编译确认：isKilled() = isRemoved() || entityData.get(IsDead)。
        //
        // 渐变仍用 deathTime（它在死亡那一刻确实会短暂增长），
        // 归 0 后直接用最终姿态，保证整个死亡期都躺着。
        // ★★★★★ 判据只能用 deathTime。三个候选里另外两个都已被实测否掉：
        //
        //   · `!isAlive()`   → 「面朝地躺着追人」（第十六轮）
        //   · `isKilled()`   → 同样「躺着追人」（第二十轮）
        //
        // 原因是同一个，日志在玩家死亡前后对比得非常清楚：
        //     玩家死前: isKilled=false isAlive=true  health=18   ← 正常
        //     玩家死后: isKilled=true  isAlive=false health=18   ← NPC 还在追人
        // **玩家死亡会让客户端上 NPC 的这两个值一起翻转**（NPC 本身没死），
        // 所以它们都不能用来判断"这个 NPC 死了"。
        if (entity.deathTime <= 0) {
            return;
        }
        float progress = ((float) entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
        progress = Math.min(Mth.sqrt(progress), 1.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(progress * 90.0F));
    }

    /**
     * 死亡红染色强度（0 = 不染色，1 = 全红）。
     *
     * <p>CNPC 自己的红色是 {@code renderColor()} 设的顶点色，只作用于它的
     * humanoid 渲染路径；YSM 有独立的顶点色管线，不会读它，
     * 所以 NPC 死后不会变红（用户第六轮反馈）。这里通过
     * {@link #getRenderColor} 把它补回来。
     *
     * <p>CNPC 的判定是 {@code isKilled()}；用 {@code deathTime} 做渐变，
     * 表现上更接近"逐渐变红倒下"。
     */
    private static float deathTint(LivingEntity entity) {
        // ★ 只看 deathTime，**绝不能用 CNPC 的 isKilled()**（第七轮日志铁证）：
        //   [death] isKilled=true deathTime=0 isAlive=false health=19.0
        // CNPC 的 NPC 死后不会被移除、而是等待复活，这期间 isKilled() 一直为 true，
        // 即使 health 已恢复、deathTime 已归 0 也不变 → 拿它染色会导致 NPC **永久深红**。
        //
        // deathTime 是 vanilla 的死亡动画计时（每 tick +1，死亡瞬间才 >0），
        // 语义上才是"正在播死亡动画"，与倒地旋转用的是同一个量，两者天然同步。
        return entity.deathTime > 0 ? 1.0F : 0.0F;
    }

    /**
     * getRenderColor(animatable, partialTick, poseStack, bufferSource, buffer, packedLight)
     *
     * <p>死亡时染红，其余情况用白色（不改变贴图本色）。
     */
    @Override
    public ooO00Oo0oooOoO0OOOOoo0Oo Oo0Oo0o00O00Oo0OOoOOoooo(
            YsmNpcAnimatable animatable, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int light) {
        if (this.dyingProgress <= 0.0F) {
            // Color.WHITE
            return ooO00Oo0oooOoO0OOOOoo0Oo.Oo0Oo0o00O00Oo0OOoOOoooo;
        }
        // Color.ofRGB(r, g, b) —— 红色保留，绿蓝压暗，与 CNPC 的死亡染色观感一致。
        float dim = 1.0F - 0.7F * this.dyingProgress;
        return ooO00Oo0oooOoO0OOOOoo0Oo.Oo0Oo0o00O00Oo0OOoOOoooo(1.0F, dim, dim);
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(@NotNull LivingEntity entity) {
        return this.currentTexture == null
                ? MissingTextureAtlasSprite.getLocation()
                : this.currentTexture;
    }

    /**
     * 名牌交给 CNPC 渲染 —— 它有自己的名字/标题/血条一套显示逻辑。
     * 这里返回 false，避免两套名牌重叠。
     */
    @Override
    public boolean shouldShowName(@NotNull LivingEntity entity) {
        return false;
    }

    /**
     * 基类会在模型渲染完后回调这里渲染"附加层"。
     * 我们用它渲染主副手物品 —— 这是本方法唯一的用途。
     *
     * <p>签名对应 YSM 的
     * {@code render(T animatable, float partialTick, PoseStack, MultiBufferSource,
     * int packedLight, AnimationEvent, EntityModelData)}。
     */
    @Override
    public void Oo0Oo0o00O00Oo0OOoOOoooo(
            YsmNpcAnimatable animatable, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int light,
            OO00O0o0OooOOOo00OO00o00<?> event, O00OoOOOOoOOOO0OOOoO0ooO modelData) {
        this.renderHeldItems(animatable, poseStack, bufferSource, light);
    }

    /**
     * renderEarly —— 在骨骼矩阵计算之前的钩子。
     *
     * <p>此时 YSM 自己的动画已经算完并写进了骨骼，但还没生成渲染矩阵。
     *
     * <h2>★★★★ 这里曾经是"B 方案"的注入点，现已废弃</h2>
     * 曾在这里把 BetterCombat 的骨骼旋转写进 YSM 骨骼，
     * 结果 NPC 攻击姿态严重诡异（手臂平举到背后）。
     *
     * <p>用户的关键反馈点破了方向错误：「我要求向玩家的看齐，玩家那种才正常」。
     * 玩家身上**根本没有**"把 BC 骨骼搬进 YSM 骨骼"这回事：
     * <ul>
     *   <li>YSM 全 jar 只有 3 个类引用 playerAnimator，<b>全部只读字符串</b>
     *       （BC 绑定只取动画名；ISS 兼容取 {@code extraData["name"]} 后拼前缀
     *       再 {@code setAnimation}；{@code PlayerAnimatorCompat} 只判断
     *       对方是否在播动画）</li>
     *   <li>YSM 渲染玩家用<b>自己的 geo 骨骼</b>（{@code IBone}），
     *       而 BC/playerAnimator 驱动的是 <b>vanilla humanoid 的 ModelPart</b></li>
     *   <li>两套骨骼完全独立 → <b>BC 的骨骼动画对 YSM 模型本来就无效</b></li>
     * </ul>
     * 所以玩家攻击时看到的动作，其实是 YSM 模型自带的 {@code swing_hand}
     * （配合 BC 改过的攻速与音效，观感上像是 BC 的动作）。
     *
     * <p>跨骨架搬运骨骼数据是玩家身上不存在的行为，且注定失败 ——
     * 两套骨架的 pivot 布局、层级、比例都不同。
     * 现在 NPC 走与玩家<b>完全相同</b>的路径：只播模型自带的动画。
     *
     * <h2>★ override 已整个删除（第三十二轮）</h2>
     * 它最后只剩一个 {@code dumpBones} 诊断日志，而那个日志**误导了我好几轮**：
     * 它采样 RightArm/RightLeg/UpBody，但待机动画主要动**尾巴**，
     * 于是手臂常驻 {@code 0.314} 被我读成"动画没在播"，
     * 进而把「不切换」误判成「不推进」，往错方向查了很久。
     *
     * <p><b>教训</b>：骨骼采样类日志必须覆盖该动画真正会动的骨骼，
     * 否则宁可不打。用户那句"尾巴还会推一推"才是准确的观测。
     */

    /**
     * 渲染主副手物品。
     *
     * <p>逻辑照搬 YSM 自己的持物层（它是 5 个渲染层里唯一不含 {@code checkcast Player}
     * 的那个），但只用 {@code LivingEntity} 的 API：
     * <ol>
     *   <li>只有模型定义了对应手部骨骼时才渲染该手（{@code rightHandBones} /
     *       {@code leftHandBones} 非空）—— 模型作者没做手部挂点时不硬塞；</li>
     *   <li>用 {@code prepMatrixForLocator} 把矩阵对齐到手部骨骼；对不齐时用
     *       vanilla 那套兜底变换。</li>
     * </ol>
     */
    private void renderHeldItems(YsmNpcAnimatable animatable, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int light) {
        // getCurrentModel()
        OOOO0O0O000O000000oOOO0o model = animatable.OOOoOO000000o0o0oOooo0o0();
        if (model == null) {
            return;
        }
        // getEntity()。泛型实参是 LivingEntity，所以这里直接就是 LivingEntity。
        LivingEntity entity = animatable.OO00OOOOo0Ooo0oo0o0Oo0OO();
        if (entity == null) {
            return;
        }
        ItemStack mainHand = entity.getMainHandItem();
        ItemStack offHand = entity.getOffhandItem();
        if (mainHand.isEmpty() && offHand.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        // rightHandBones() 非空 -> 渲染主手
        if (!model.oo0OoO00oOoo000O0000o0oo().isEmpty()) {
            this.renderItem(model, entity, mainHand,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT,
                    poseStack, bufferSource, light);
        }
        // leftHandBones() 非空 -> 渲染副手
        if (!model.OOOOo0O0oO0OOo0O0O0Oo0O0().isEmpty()) {
            this.renderItem(model, entity, offHand,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT,
                    poseStack, bufferSource, light);
        }
        poseStack.popPose();
    }

    private void renderItem(OOOO0O0O000O000000oOOO0o model, LivingEntity entity,
                            ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                            PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        if (stack.isEmpty()) {
            return;
        }
        boolean isLeftHand = arm == HumanoidArm.LEFT;

        poseStack.pushPose();
        List<Oo0o00oOOo0OO000000O0oO0> bones = isLeftHand
                ? model.OOOOo0O0oO0OOo0O0O0Oo0O0()   // leftHandBones
                : model.oo0OoO00oOoo000O0000o0oo();  // rightHandBones
        // prepMatrixForLocator 返回 true 表示"骨骼被隐藏，不该渲染这只手的物品"
        // （YSM 原逻辑：返回 true 就跳过渲染）。
        if (!o000OO00oooOo00000ooo0Oo.Oo0Oo0o00O00Oo0OOoOOoooo(poseStack, bones)) {
            // 兜底变换，与 vanilla / YSM 一致
            poseStack.translate(0.0D, -0.0625D, -0.1D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            this.itemInHandRenderer.renderItem(
                    entity, stack, context, isLeftHand, poseStack, bufferSource, light);
        }
        poseStack.popPose();

        // 模型可以定义额外的手部挂点链（一把武器渲染多份，例如背上的备用武器）。
        // 注意 YSM 原实现这里左右是**反的**（左手取 rightHandChain），照抄以保持一致。
        List<List<Oo0o00oOOo0OO000000O0oO0>> chains = isLeftHand
                ? model.Ooooo0oooO0oooOOOoO0000O()
                : model.oooooooOOoOOoO00OooOo00O();
        for (List<Oo0o00oOOo0OO000000O0oO0> chain : chains) {
            poseStack.pushPose();
            if (!o000OO00oooOo00000ooo0Oo.Oo0Oo0o00O00Oo0OOoOOoooo(poseStack, chain)) {
                poseStack.translate(0.0D, -0.0625D, -0.1D);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                this.itemInHandRenderer.renderItem(
                        entity, stack, context, isLeftHand, poseStack, bufferSource, light);
            }
            poseStack.popPose();
        }
    }
}

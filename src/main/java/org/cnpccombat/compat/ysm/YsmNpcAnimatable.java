package org.cnpccombat.compat.ysm;

import com.elfmcys.yesstevemodel.OOoOoooOOooO0o0000o0O0o0;
import com.elfmcys.yesstevemodel.o0O0oOooOo0OoOo0oOo00O00;
import com.elfmcys.yesstevemodel.oOo00OoOO00O000ooO0Oo00o;
import com.elfmcys.yesstevemodel.oOo0oO0OOo0ooOoo0oOo0oOo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 一个 NPC 的 YSM 模型状态载体（YSM 术语里叫 "animatable"）。
 *
 * <p>这是整个方案的关键：直接继承 YSM 的 {@code LivingAnimatable<T extends LivingEntity>}
 * （混淆名 {@link o0O0oOooOo0OoOo0oOo00O00}），泛型实参填 CNPC 的实体类型。
 * YSM 自己的车万女仆兼容就是这么做的（{@code MaidCapability extends
 * LivingAnimatable<EntityMaid>}），所以这条路是 YSM 官方支持的用法，不是 hack。
 *
 * <h2>为什么不复用玩家那套</h2>
 * YSM 的玩家 animatable / 玩家渲染器把泛型实参绑成了 {@code Player}，
 * 编译器为它们生成的桥接方法第一条指令就是 {@code checkcast Player}，
 * 喂非玩家实体必然 {@code ClassCastException}。
 * 而基类本身的泛型上界只是 {@code LivingEntity}，零 Player 依赖
 * （已用字节码扫描逐类确认）。我们泛型实参填 NPC 类型，
 * 编译器生成的桥接方法 checkcast 的就是 NPC 类，天然安全。
 *
 * <h2>抽象方法</h2>
 * 父类已经把 8 个抽象方法里的大部分实现了，只剩 3 个需要我们提供，
 * 而且都能直接委托给父类已有的设施。
 *
 * <p><b>客户端专用</b>：模型渲染纯客户端行为。这个类会引用 YSM 类型，
 * 只能在 {@link YsmCompat#isLoaded()} 为真时被触碰。
 */
@OnlyIn(Dist.CLIENT)
public class YsmNpcAnimatable extends o0O0oOooOo0OoOo0oOo00O00<LivingEntity> {

    /**
     * 贴图分辨率上限，与 YSM 自己给玩家/女仆用的值一致（600）。
     * 传小了会导致高分辨率模型贴图被裁切。
     */
    private static final int TEXTURE_RESOLUTION = 600;

    /**
     * 单帧最多补多少 tick 的动画时间。
     *
     * <p>NPC 长时间不被渲染（切界面、离开视野）后重新出现时，
     * 若把欠下的全部时间一次性补上，动画会瞬间快进一大段。
     * 封顶到 2 tick，表现为"从当前姿态平滑继续"，而不是补播几百 tick。
     */
    private static final int MAX_CATCHUP_TICKS = 2;

    /** 当前已绑定的模型 id，用于避免每帧重复绑定（重复绑定会重置动画状态）。 */
    private String boundModelId;

    /**
     * 上一帧渲染时看到的时钟值（客户端全局 tick），用于检测"渲染中断"。
     * -1 表示还没渲染过。
     */
    private int lastRenderedTick = -1;

    /**
     * 上一帧看到的 {@code manager.limbSwing}。
     *
     * <p><b>不再用它判断「YSM 有没有推进」</b>——本方法末尾我自己也写这个字段，
     * 用它做判据等于自欺（第十六轮的半速 bug）。现在只是留着做诊断对照。
     */
    private float lastLimbSwing = -1.0F;

    /**
     * 上一帧看到的 {@code entity.tickCount}，用于判断实体是否还在 tick。
     *
     * <p>这是判断「要不要接管动画时钟」的**唯一可靠判据**：
     * 它完全由 vanilla 维护，我们从不写它。
     * CNPC 的 NPC 只在"死亡待复活"态停止 tick。
     */
    private int lastEntityTick = -1;

    /**
     * 上次观察到 {@code entity.tickCount} 发生变化时的全局时间（tick，含 partialTick）。
     *
     * <p>用于区分「实体真的停止 tick」与「同一个 tick 内被渲染了多帧」。
     * -1 表示还没观察到过。
     */
    private float lastEntityTickChangeAt = -1.0F;

    /** 是否已把动画时钟的基准从「实体时间轴」切到「全局 tick」（只做一次）。 */
    private boolean globalClockRebased;

    /**
     * 全局 tick 与实体时间轴的偏移量。
     *
     * <p>{@code currentTick = globalTick - offset}，用于让改写后的值
     * 与原来的实体时间轴**连续**。必须如此：
     * {@code ControllerRegistry.process} 里有
     * {@code if (currentTick - lastResetTick >= 1200) reset();}，
     * 时间轴一次跳几千 tick 会导致动画状态被反复重置。
     */
    private float globalClockOffset;

    /**
     * 是否启用「反射补运动状态」的兜底路径。
     *
     * <p><b>默认关闭</b>，见 setCustomAnimations 里的说明：
     * 真根因是 animatable 抓着旧实体，已在 {@code YsmBridge} 修好；
     * 继续开着会用估算值覆盖真实值，反而让模型状态机判断出错。
     */
    private static final boolean REVIVE_FALLBACK_ENABLED = false;

    /** 上一次健康检查看到的 seekTime（-1 = 还没看过）。 */
    private float lastSeenSeek = -1.0F;

    /** 上一次健康检查看到的 entity.tickCount（-1 = 还没看过）。 */
    private int lastSeenEntityTick = -1;

    /** {@code AnimationEvent.limbSwing}（private final，需反射写）。 */
    private static java.lang.reflect.Field limbSwingField;

    /** {@code AnimationEvent.limbSwingAmount}（private final，需反射写）。 */
    private static java.lang.reflect.Field limbSwingAmountField;

    /** 反射是否已初始化（失败也只尝试一次，避免每帧抛异常）。 */
    private static boolean limbFieldsResolved;

    /** 累积的行走相位，用于在 walkAnimation 冻结时自己推进步伐。 */
    private float syntheticLimbSwing;

    /**
     * 把 {@code limbSwing} / {@code limbSwingAmount} 补回真实值。
     *
     * <p>字段对位（由构造器参数顺序确定，已核对字节码）：
     * <ul>
     *   <li>{@code oOOOo0OOO0ooooo0O00OO0o0} = limbSwing（构造器第 2 个参数）</li>
     *   <li>{@code OOOOo0O0oO0OOo0O0O0Oo0O0} = limbSwingAmount（第 3 个参数）</li>
     * </ul>
     *
     * <p>速度来源不能用 {@code walkAnimation}（它在 {@code aiStep()} 里更新，
     * 实体停 tick 就冻结），改用 {@code getDeltaMovement()} 的水平分量 ——
     * 它由移动同步包更新，与实体是否 tick 无关。
     */
    private void reviveLimbSwing(
            com.elfmcys.yesstevemodel.OO00O0o0OooOOOo00OO00o00<?> event) {
        LivingEntity e = this.OO00OOOOo0Ooo0oo0o0Oo0OO();
        if (e == null) {
            return;
        }
        // 只在 YSM 已经把值清零时才介入（活着的实体交给 YSM 自己算）。
        if (e.isAlive()) {
            return;
        }
        if (!limbFieldsResolved) {
            limbFieldsResolved = true;
            try {
                Class<?> c = com.elfmcys.yesstevemodel.OO00O0o0OooOOOo00OO00o00.class;
                limbSwingField = c.getDeclaredField("oOOOo0OOO0ooooo0O00OO0o0");
                limbSwingAmountField = c.getDeclaredField("OOOOo0O0oO0OOo0O0O0Oo0O0");
                limbSwingField.setAccessible(true);
                limbSwingAmountField.setAccessible(true);
                YsmDebug.log("limb", "limb fields resolved");
            } catch (Throwable t) {
                limbSwingField = null;
                limbSwingAmountField = null;
                YsmDebug.log("limb", "could not resolve limb fields: {}", t);
            }
        }
        if (limbSwingField == null || limbSwingAmountField == null) {
            return;
        }

        var m = e.getDeltaMovement();
        float speed = (float) Math.sqrt(m.x * m.x + m.z * m.z);
        // vanilla 的 limbSwingAmount 量纲：行走约 0.6~1.0，而每 tick 位移约 0.1，
        // 所以放大约 6 倍再夹到 [0,1]，与模型脚本的预期范围一致。
        float amount = Math.min(speed * 6.0F, 1.0F);
        if (amount < 0.02F) {
            amount = 0.0F;
        }
        // 相位自己累积（walkAnimation.position 已冻结）。
        this.syntheticLimbSwing += amount * 0.6F;

        try {
            limbSwingField.setFloat(event, this.syntheticLimbSwing);
            limbSwingAmountField.setFloat(event, amount);
            YsmDebug.log("limb", "revived limbSwing={} amount={} (speed={})",
                    this.syntheticLimbSwing, amount, speed);
        } catch (Throwable ignored) {
            // 写不进去就算了，不影响渲染
        }
    }

    /** {@code MovementState.velocity}（private Vec3，需反射写）。 */
    private static java.lang.reflect.Field velocityField;

    /** velocity 反射是否已初始化。 */
    private static boolean velocityFieldResolved;

    /**
     * {@code ground_speed2} 的放大系数。
     *
     * <p>模型阈值：{@code > 0} 进 walk_slow、{@code >= 3} 进 walk、{@code >= 5} 进 run。
     * 而 {@code ground_speed2 = 20 * |velocity|}，我们填的是"每渲染帧位移"。
     * 行走约 0.1/tick，60fps 下约 0.033/帧 → 20*0.033 ≈ 0.67，够不上 walk。
     * 乘 6 后约 4，落在 walk 区间；跑动时更高，能进 run。
     */
    private static final double SPEED2_GAIN = 6.0D;

    /** 上一渲染帧的位置（用于自测速度，与实体 tick 无关）。 */
    private double lastRenderX = Double.MAX_VALUE;

    /** 上一渲染帧的位置 Z。 */
    private double lastRenderZ = Double.MAX_VALUE;

    /** 平滑后的每帧位移，避免位置只在同步包到达时跳变导致状态机抖动。 */
    private double smoothedStep;

    /**
     * 把 {@code MovementState.velocity} 补成真实速度。
     *
     * <h2>★★★★★ 这才是「玩家死后只播待机、不切走路」的真正根因（第三十轮）</h2>
     *
     * 解包模型文件后才看清：{@code wine_fox/16_tactics} 的
     * {@code player.post_main} 状态机**只看一个变量**
     * （controller/wine_fox.animation_controllers.json）：
     * <pre>
     * "transitions": [ {"walk_slow": "ysm.ground_speed2 > 0"} ]
     *                 {"walk":       "ysm.ground_speed2 >= 3"}
     *                 {"run":        "ysm.ground_speed2 >= 5"}
     * </pre>
     * 所以我上一轮补 {@code limbSwingAmount} 补错了字段 —— 模型根本不读它。
     *
     * <p>{@code ysm.ground_speed2} 的实现（字节码已解全）：
     * <pre>
     * var st = context.getAnimatable().getMovementState();
     * Vec3 v = st.getVelocity();
     * return 20.0f * sqrt(v.x*v.x + v.z*v.z) / st.getScale();
     * </pre>
     * 而 {@code MovementState.updateVelocity} 用的是**插值后的渲染位置差**：
     * <pre>
     * Vec3 cur = new Vec3(lerp(pt, xOld, getX()), lerp(pt, yOld, getY()),
     *                     lerp(pt, zOld, getZ()));
     * if (lastPos != null) velocity = cur.subtract(lastPos);
     * lastPos = cur;
     * </pre>
     * 玩家死亡后 Orphie 的 {@code xOld}/{@code getX()} 全部冻结
     * （日志 [speed] byPos=0.0 就是这个），差值恒为零向量
     * → {@code ground_speed2 == 0} → 状态机永远停在 default（待机），
     * **盖住我们在 player.main 上播的 walk**（所以 playing=true 但看不到）。
     *
     * <p>修法：用 {@code getDeltaMovement()}（由移动同步包更新，不依赖实体 tick）
     * 直接覆盖 velocity。注意量纲：ground_speed2 会乘 20 再除以 scale，
     * 而 velocity 语义是"每帧位移"，所以直接把每 tick 位移除以 20 填进去，
     * 让 {@code 20 * |v|} 还原成"每 tick 位移"的数值（行走约 0.1 → speed2≈2，
     * 正好落在模型的 walk_slow/walk 阈值区间）。
     */
    private void reviveGroundSpeed() {
        LivingEntity e = this.OO00OOOOo0Ooo0oo0o0Oo0OO();
        if (e == null || e.isAlive()) {
            return;   // 活着时交给 YSM 自己算
        }
        if (!velocityFieldResolved) {
            velocityFieldResolved = true;
            try {
                Class<?> c = com.elfmcys.yesstevemodel.oOooOOo00oOO0oO0O00O0O0o.class;
                // oo0OoO00oOoo000O0000o0oo = velocity（getVelocity() 返回它）
                velocityField = c.getDeclaredField("oo0OoO00oOoo000O0000o0oo");
                velocityField.setAccessible(true);
                YsmDebug.log("gspeed", "velocity field resolved");
            } catch (Throwable t) {
                velocityField = null;
                YsmDebug.log("gspeed", "could not resolve velocity field: {}", t);
            }
        }
        if (velocityField == null) {
            return;
        }
        try {
            var st = this.oo0OoO00oOoo000O0000o0oo();   // getMovementState()
            if (st == null) {
                return;
            }

            // ★★★★★ 速度必须**自己按渲染帧测**，不能用 getDeltaMovement()。
            //
            // 第三十轮实测：deltaMovement 在玩家死亡后同样是**冻结的快照**
            //（日志 velocity=(-0.00258,-0.00053) 每帧完全相同，
            // 而 NPC 实际在追人、速度约 0.1~0.2）。
            // 所有 per-tick 量都不可信，唯一可靠的是"渲染位置每帧的真实变化" ——
            // 渲染每帧都在跑，与实体 tick 无关。
            double x = e.getX();
            double z = e.getZ();
            if (this.lastRenderX == Double.MAX_VALUE) {
                this.lastRenderX = x;
                this.lastRenderZ = z;
                return;   // 首帧没有参照，下一帧起才有速度
            }
            double dx = x - this.lastRenderX;
            double dz = z - this.lastRenderZ;
            this.lastRenderX = x;
            this.lastRenderZ = z;

            // 位置只在收到同步包时跳变，中间帧为 0；用衰减保持让数值平滑，
            // 否则模型状态机会在 walk/idle 之间抖动。
            double stepSq = dx * dx + dz * dz;
            if (stepSq > 1.0E-12D) {
                this.smoothedStep = Math.sqrt(stepSq);
            } else {
                this.smoothedStep *= 0.90D;   // 没有新位移时缓慢衰减
                if (this.smoothedStep < 1.0E-4D) {
                    this.smoothedStep = 0.0D;
                }
            }

            // ground_speed2 = 20 * |velocity| / scale，而 velocity 语义是"每帧位移"。
            // 我们测到的 smoothedStep 就是每帧位移，直接填进去即可
            //（行走时约 0.1/tick ÷ 3 帧 ≈ 0.03/帧 → speed2 ≈ 0.7；
            //  乘一个系数抬到模型的 walk_slow/walk 阈值区间）。
            double perFrame = this.smoothedStep * SPEED2_GAIN;
            var v = new net.minecraft.world.phys.Vec3(perFrame, 0.0D, 0.0D);
            velocityField.set(st, v);
            YsmDebug.log("gspeed",
                    "step={} smoothed={} -> ground_speed2~{}",
                    Math.sqrt(stepSq), this.smoothedStep, 20.0D * perFrame);
        } catch (Throwable ignored) {
            // 写不进去就算了
        }
    }

    /** 安全取 {@code entity.tickCount}（实体为空时给 0）。 */
    private int entityOrZero() {
        LivingEntity e = this.OO00OOOOo0Ooo0oo0o0Oo0OO();   // getEntity()
        return e == null ? 0 : e.tickCount;
    }

    /**
     * 是否需要在下一次 {@code setAnimation} 前**强制清空**控制器状态。
     *
     * <h2>★★★★★ 这是「玩家死后走路/攻击动画失效」的最后一环</h2>
     * {@code PredicateBasedController.setAnimation(name, loop)} 开头有短路
     * （runtime.txt offset 9-44）：
     * <pre>
     * if (currentPair != null
     *     &amp;&amp; currentPair.getSecond().equals(name)     // 动画名相同
     *     &amp;&amp; currentPair.getFirst() == loopType) {    // 循环类型相同
     *     return;                                     // ★ 什么都不做
     * }
     * </pre>
     * 玩家死亡后控制器的 {@code currentPair} 卡在 {@code (LOOP, "walk")}，
     * 而我们每帧都请求 walk/LOOP → **每次都命中短路** → 动画实例永不重建。
     * 但那个实例已随旧的实体对象/animatable 失效，于是骨骼停在 bind pose。
     *
     * <p>日志证据（同一份日志的前后对比，非常干净）：
     * <pre>
     * 死前: animBefore=Coded              ← 每帧都重新触发，骨骼在动
     * 死后: animBefore=Coded -&gt; walk 恒定  ← 卡在 walk，setAnimation 变空操作
     * </pre>
     * 且死后 {@code [main]}/{@code [swing]} 日志完全消失
     * —— 因为控制器从未离开 walk 状态。
     */
    private volatile boolean controllersNeedReset;

    /**
     * 渲染入口检测到实体对象被替换时调用，要求下一帧重建动画状态。
     *
     * <p>★★★★★ 同时**必须重算时基偏移**。第二十六轮日志证明了这一点：
     * swap 之后 seekTime 从 1558 一次跳到 2193（+635），而
     * {@code hasUpd=true} 从此恒定 —— 因为 YSM 复位 hasUpdatedThisTick
     * 的唯一入口是 {@code if (currentTick > lastTick)}（offset 20-41），
     * 一次大跳变把 lastTick 推到很高，之后 currentTick 再也追不上
     * → 复位永不发生 → {@code z2 = isTickTriggered && !hasUpdatedThisTick}
     * 恒假 → <b>setupAnim 从不执行</b> → 骨骼冻在 bind pose。
     *
     * <p>所以换对象时要把偏移量按**新的** eTick 重新对齐，
     * 让 currentTick 保持连续、不产生跳变。
     */
    void requestControllerReset() {
        this.controllersNeedReset = true;
        this.globalClockRebased = false;   // 下一帧按新 eTick 重算偏移
    }

    /** 供 {@code play()} 消费：返回并清除"需要重置"标志。 */
    boolean consumeControllerResetFlag() {
        if (this.controllersNeedReset) {
            this.controllersNeedReset = false;
            return true;
        }
        return false;
    }

    /**
     * 本帧的 partialTick，由渲染器每帧写入。
     *
     * <p>用于让动画时钟在**帧间**也平滑推进：只用整数 tick 会让动画
     * 以 20Hz 的台阶跳动，高帧率下能明显看出顿挫。
     */
    private float lastPartialTick;

    /** 上一帧的 partialTick，用于算出「两帧之间真实流逝了多少 tick」。 */
    private float prevPartialTick;

    /** 渲染器每帧调用，把 partialTick 交给时钟补偿逻辑。 */
    void setPartialTick(float partialTick) {
        this.lastPartialTick = partialTick;
    }

    /** 把 partialTick 夹到 [0,1]，防止异常值把时钟推歪。 */
    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /**
     * 客户端全局 tick，只要游戏在跑就持续递增。
     *
     * <p>用 YSM 自己的 {@code ClientTickEvent.getTickCount()} 而不是
     * {@code entity.tickCount}：后者在 CNPC 的 NPC 死亡待复活期间会**停止增长**，
     * 导致动画时钟冻结（第七轮日志实测 `tick=1008` 恒定不变）。
     *
     * <p>这也是 YSM 自己给模型预览用的时基
     * （{@code AnimatableEntity.processAnimationImpl} 里对
     * {@code IPreviewAnimatable} 就走这个分支），所以是它内部认可的用法。
     */
    private static int clientTick() {
        return com.elfmcys.yesstevemodel.o0O0000ooOOO0O0oo0OOOoOo.Oo0Oo0o00O00Oo0OOoOOoooo();
    }

    /**
     * @param npc 要渲染的 NPC
     *
     * <p><b>务必传 {@code false} 给父类的 registerWithCache 参数</b>，
     * 即不注册进 YSM 的 {@code EntityRenderCache}。原因：
     *
     * <p>{@code EntityRenderCache.tick(partialTick)} 由 YSM 每帧调用，会对所有
     * 注册进来的**非玩家非投掷物**实体（正好是我们的 NPC）执行
     * {@code submitAsyncUpdate(partialTick)} —— 用**它自己的 partialTick**
     * 把动画丢给线程池预算，结果存进 {@code modelFuture}。
     * 而我们渲染时调的 {@code processAnimation(自己的 partialTick)} 会发现
     * future 已存在，于是**直接取用异步结果而不按我们的 partialTick 重算**。
     *
     * <p>两个 partialTick 不一致 → 每帧姿态在两个值之间摆动 → NPC 反复横跳／抽搐
     * （玩家死亡界面下渲染与 tick 节奏错开，会把这个不一致放大到肉眼可见）。
     *
     * <p>YSM 自己的玩家/女仆 animatable 传 true 是合理的，因为它们的渲染器
     * <b>不自己调 processAnimation</b>，完全依赖缓存里算好的结果。
     * 我们是自己驱动渲染的，两套驱动会打架。
     *
     * <p>代价：失去 YSM 的异步动画优化（NPC 很多时略慢），换来姿态稳定。
     */
    public YsmNpcAnimatable(LivingEntity npc) {
        super(npc, false);
    }

    /**
     * 修补"渲染中断"造成的动画时钟跳变，必须在每次渲染前调用。
     *
     * <h2>要解决的问题</h2>
     * YSM 的动画时钟是这样推进的（{@code AnimatableEntity.setCustomAnimations}）：
     * <pre>
     * float f2 = currentTick - manager.startTick;   // currentTick 来自 entity.tickCount
     * float f3 = f2 - manager.limbSwing;
     * if (f3 &gt; 0) { manager.limbSwing = f2; seekTime += f3; }
     * ...
     * isTickTriggered |= rateLimiter.request(seekTime / 20.0f);
     * </pre>
     * 也就是说 {@code seekTime} 的增量由「实体 tick 数」与「上次渲染时记录的
     * limbSwing」之差决定 —— 它<b>假设每个 tick 都会被渲染到</b>。
     *
     * <p>玩家死亡时死亡界面接管画面，<b>NPC 继续 tick 但不再被渲染</b>。
     * 复活后第一次渲染，{@code f3} 会一次性跳到几百（死亡停留的 tick 数），
     * {@code seekTime} 随之暴增。而 {@code RateLimiter.request} 里：
     * <pre>
     * aggregate += time - lastRequestTime;   // time 暴增 -&gt; aggregate 暴增
     * if (aggregate &lt; interval) return false;
     * aggregate %= interval;                 // 取模把巨大的余额抹平
     * </pre>
     * 取模之后 {@code aggregate} 归零，而 {@code lastRequestTime} 已经是那个巨大值，
     * 之后每帧的正常增量（约 0.0025）远小于 {@code interval}（1/60≈0.0167），
     * 于是 {@code isTickTriggered} 长期为 false → <b>动画冻结</b>。
     * 这就是"玩家只要死一次，NPC 动画就被冻结"的根因。
     *
     * <p><b>修法</b>：检测到 tick 跳变（说明中间有帧没渲染）时，
     * 把 {@code manager.limbSwing} 直接推到"当前应有的值"，
     * 这样下一帧算出的 {@code f3} 就是正常的一帧增量，不会暴增。
     * 相当于告诉动画系统"那段没渲染的时间不算"，
     * 动画从当前姿态平滑继续，而不是试图补播几百 tick。
     */
    public void syncAnimationClock() {
        LivingEntity entity = this.OO00OOOOo0Ooo0oo0o0Oo0OO();   // getEntity()
        if (entity == null) {
            return;
        }
        // ★ 用**客户端全局 tick** 而不是 entity.tickCount（第七轮日志铁证）：
        //   [clock] tick=1008 prevTick=1008 skipped=0 seekTime=983.98  ← 恒定不变
        // CNPC 的 NPC 死亡后进入"等待复活"状态就**不再 tick**，
        // entity.tickCount 会永久停住。而 YSM 的动画时钟是
        // `f2 = currentTick - startTick` 驱动的，currentTick 停 → seekTime 冻结
        // → 动画完全不推进（表现为"站在原地一动不动"）。
        //
        // 客户端全局 tick 只要游戏在跑就一直递增，与实体是否 tick 无关，
        // 所以动画能继续推进。副作用是暂停菜单里动画也会走 —— 可以接受
        // （YSM 自己给模型预览用的就是这个时基，见 AnimatableEntity 里
        //  `this instanceof IPreviewAnimatable ? ClientTickEvent.getTickCount() : entity.tickCount`）。
        int tick = clientTick();
        int previous = this.lastRenderedTick;
        this.lastRenderedTick = tick;

        com.elfmcys.yesstevemodel.Oo0OO00OooO0o0OOOo0oO0Oo manager = null;
        try {
            manager = this.ooOoooooO0OoOOOO0oO0OooO();   // getAnimationData()
        } catch (Throwable ignored) {
            // 拿不到就走下面的 null 分支
        }

        // ★ 健康检查日志（取代旧的 [clock] 排查日志）。
        //
        // 旧日志堆了十几个内部字段，是用来定位"动画冻结"的，那个问题已经解决。
        // 现在只保留**能判断兼容是否健康**的三个量，而且只在异常时打：
        //   · seekTime 是否在推进（动画时钟活着吗）
        //   · entity.tickCount 是否在增长（实体活着吗 —— 陈旧对象的特征）
        //   · 模型是否就绪
        // 正常情况完全不输出，避免噪音掩盖真正的问题。
        if (YsmDebug.enabled() && manager != null) {
            float seek = this.OOoo0OOooooOoo0OoO0oo00o();          // getSeekTime()
            boolean seekStalled = this.lastSeenSeek >= 0.0F
                    && seek <= this.lastSeenSeek + 1.0E-4F;
            boolean entityStalled = this.lastSeenEntityTick >= 0
                    && entity.tickCount == this.lastSeenEntityTick;
            this.lastSeenSeek = seek;
            this.lastSeenEntityTick = entity.tickCount;

            if (seekStalled || entityStalled || !this.oOOo0Ooo0oOoo0O0OOOOo0oo()) {
                YsmDebug.log("health",
                        "ABNORMAL npc={} seekTime={} seekStalled={} eTick={} entityStalled={} "
                                + "modelReady={} (entityStalled=stale-object symptom, "
                                + "see task_plan_ysm.md section 0)",
                        entity.getId(), seek, seekStalled,
                        entity.tickCount, entityStalled,
                        this.oOOo0Ooo0oOoo0O0OOOOo0oo());
            }
        }

        if (manager == null) {
            return;
        }
        // 首次渲染，或时钟回退（换世界/实体重建）→ 让 YSM 自己初始化。
        if (previous < 0 || tick < previous) {
            return;
        }

        // ★★★★★ 这里曾经是 `if (tick - previous <= 0) return;`，
        // **它让 rebase 几乎永远到不了**（第二十三轮日志：rebase 只触发 1 次）。
        //
        // 原因：`tick` 是客户端全局 tick（整数，20/秒），
        // 而本方法**每帧**被调用（60/秒）。所以有 2/3 的帧 tick 与上一帧相同，
        // `advanced == 0` → 直接 return → 后面的补偿逻辑根本执行不到。
        //
        // 时间推进必须用**带 partialTick 的浮点时间轴**来判断，
        // 否则在"每帧调用、按 tick 计数"的场合必然漏掉大部分帧。

        try {
            // ★★★★ 第十一轮的正解：让 seekTime 对齐「实体的真实时间轴」，
            // 而不是去猜"该补多少"。
            //
            // 前三轮的错误与日志证据：
            //   · 第八轮：把 limbSwing 推到未来 → f3 恒负 → 动画全死
            //     （日志 seekTime=0.0 恒定、limbSwing 一路涨）
            //   · 第九轮：无条件 seekTime += delta → 与 YSM 叠加 → 2 倍速
            //     （日志 seek-limb == limb，即收了两份增量）
            //   · 第十轮：补了 seekTime 但漏了 hasUpdatedThisTick → 画面仍不动
            //     （日志 seekTime 1817→1837 一直涨，但 limbSwing 卡死）
            //   · 第十一轮（本次）：修好上面之后又变成"动画变慢"
            //     （日志 tick +20 / seekTime +20 / limbSwing 只 +10）
            //
            // 「变慢」的根因（这次才看清）：
            //   limbSwing 只在 renderNpc 被调用时由 YSM 更新，
            //   所以 **limbSwing 的增速 = 实际渲染帧率**，不是实体 tick 速率。
            //   玩家死亡界面下 NPC 的渲染频率骤降（实测约 10 次/秒），
            //   YSM 每帧只推一点点，累计追不上实体 tick → 动画慢放。
            //   我按"每帧最多补 2 tick"去补，既补不满又和 YSM 抢，
            //   结果 seekTime 跑得比 limbSwing 快，两者永久失同步。
            //
            // 正解：seekTime 应该等于「实体活了多久」。
            // 直接算目标值再对齐，不做增量猜测 —— 这样无论渲染帧率多低、
            // 实体是否停 tick，动画速度都与游戏时间一致。
            //
            // YSM 的推进逻辑（AnimatableEntity.setCustomAnimations，逐行）：
            //   float currentTick = event.currentTick;      // = entity.tickCount + partialTick
            //   if (currentTick > lastTick) {
            //       hasUpdatedThisTick = false;             // ← 只有这里会重置！
            //       isTickTriggered    = false;
            //       lastTick           = currentTick;
            //   }
            //   f2 = currentTick - manager.startTick;
            //   f3 = f2 - manager.limbSwing;
            //   if (f3 > 0) { manager.limbSwing = f2; seekTime += f3; }
            //   isTickTriggered |= rateLimiter.request(seekTime / 20f);
            //   z3 = (!z || ...) && isTickTriggered && !hasUpdatedThisTick;
            //   if (z2) { if (z3) hasUpdatedThisTick = true; setupAnim(seekTime, z3); ... }
            //
            // CNPC 的 NPC 死亡后**停止 tick** → currentTick 停住 → lastTick 不更新
            // → `hasUpdatedThisTick` 永远保持 true（第一帧置的）
            // → z3 恒为 false → **动画不推进**。
            //
            // 这是我第三次改这里。前两次都只盯着 seekTime：
            //   · 第八轮把 limbSwing 推到未来 → f3 恒负 → 动画全死
            //   · 第九轮无条件 seekTime += delta → 与 YSM 叠加 → 2 倍速
            //   · 第十轮补了 seekTime（日志确认它在涨），但画面仍不动 ——
            //     因为**真正的门槛是 hasUpdatedThisTick，不是 seekTime**。
            //
            // 正确做法：模拟 YSM 自己"进入新 tick"时做的事 ——
            // 把 lastTick 往前推、并复位两个标志位，让 z3 有机会为真。
            // ★★ 不能拿 manager.startTick 当基准来算目标值（第十二轮的 2 倍速 bug）：
            // startTick 是 YSM 在**实体时间轴**上记的起点，而我这里用的是
            // **全局客户端 tick**，两个时间轴的原点不同 → 差值恒为正
            // → 每帧都补满 MAX_CATCHUP_TICKS → 动画 2 倍速
            //（日志铁证：tick 每条 +20，seekTime 却 +40）。
            //
            // 正解：自己维护一个基准，只按「全局 tick 的真实增量」推进。
            // 这样每帧补的量就是真实流逝的时间，与帧率无关。
            //
            // ★★★★★ 判据改成「实体的 tickCount 有没有真的停住」。
            //
            // 之前用 limbSwing 做判据是错的：那个字段**我自己也在写**
            //（本方法末尾），所以它"在涨"根本不能说明 YSM 在推进。
            // 第十六轮日志里 seekTime 与 limbSwing 始终完全相等，
            // 就是我自己写回造成的假象。
            //
            // 正确判据只能用**我完全不碰的量**：entity.tickCount。
            // YSM 的推进完全由它驱动（f2 = currentTick - startTick），
            // 而 CNPC 的 NPC 只在"死亡待复活"时才停止 tick。
            int entityTick = entity.tickCount;
            // ★ 不能用 `entityTick != lastEntityTick` 判断"实体在 tick"：
            // 本方法**每帧**调用（60/秒），而实体 tick 只有 20/秒，
            // 所以同一个 tick 内的 2/3 帧都会被误判成"冻结"。
            // 改为记录「上次看到 eTick 变化时的全局时间」，
            // 超过 3 tick（150ms）没变化才认定真的冻结。
            float nowF = tick + clamp01(this.lastPartialTick);
            if (entityTick != this.lastEntityTick) {
                this.lastEntityTick = entityTick;
                this.lastEntityTickChangeAt = nowF;
            }
            boolean entityTicking = this.lastEntityTickChangeAt < 0.0F
                    || (nowF - this.lastEntityTickChangeAt) < 3.0F;

            // ★★★★★ 第二十三轮：日志终于给出了确定答案。
            //
            // [clock] 在玩家死亡（客户端换掉 NPC 实体对象）前后：
            //     L1539  eTick=2628  f2=2609.94  limb=2609.78    ← 正常，每条 +20
            //     L1544  objId 1565669157 -> 1453138968
            //     L1545  eTick=2649  f2=2631.04  limb=2631.36
            //     L1550  eTick=2649  ← 冻结
            //     L1570  eTick=2649  ← 永久冻结在 2649
            //
            // **结论：新实体对象的 entity.tickCount 永久冻结（2649 不再增长）。**
            // 而 YSM 的推进公式是
            //     f2 = (entity.tickCount + partialTick) - startTick;
            //     f3 = f2 - limbSwing;
            //     if (f3 > 0) { limbSwing = f2; seekTime += f3; }
            // eTick 冻结 → f2 卡在 2631.3，而 limb=2631.36 → f3 ≈ 0（甚至略负）
            // → **YSM 永不推进** → 骨骼停在 bind pose。
            //
            // 这解释了全部症状：动画停止、走路变纯平移（位置由服务端同步照常更新）、
            // 以及"不可逆"（tickCount 再也不会恢复增长）。
            // 上一轮的 rebase 没触发，是因为 f2 只比 limbSwing 小 0.02，
            // 远达不到我设的 1.0 阈值 —— 判据选错了，不是机制没找到。
            //
            // 正解：**让 startTick 跟着全局 tick 往回退**，使 f2 持续增长。
            // f2 = (eTick + pt) - startTick，eTick 冻结时，只要每帧把 startTick
            // 减去"这一帧真实流逝的时间"，f2 就能继续涨 → f3 恒正 → YSM 自己推进。
            // 这样 limbSwing / seekTime / hasUpdatedThisTick 全部交回 YSM 维护，
            // 我们只动一个"起点"，不再和它的状态机抢任何东西。
            // 先算增量，再更新 prevPartialTick（顺序不能颠倒，否则差值恒为 0）。
            float advance = (tick + clamp01(this.lastPartialTick))
                    - (previous + clamp01(this.prevPartialTick));
            this.prevPartialTick = this.lastPartialTick;
            this.lastLimbSwing = manager.o0OOooo0o0OO00OoOOOo0o0O;   // 只读，做诊断对照

            if (entityTicking) {
                // 实体正常 tick → YSM 自己那套完全可用，**绝对不要插手**。
                return;
            }
            if (advance <= 0.0F || manager.O00OOOooOoooOoo0o0o0oO0O < 0.0F) {
                return;
            }

            // ★★★★★ 第二十四轮：整段补偿已**停用**。
            //
            // 现在 currentTick 由 setCustomAnimations 的 override 换成全局 tick，
            // f2 恒定增长，YSM 自己就能推进 —— 不再需要任何补偿。
            // 保留这段代码只是为了 [clock] 诊断日志；一旦介入就会重演
            // 前面十几轮的"抢字段"问题（2 倍速 / 半速 / 卡顿 / 完全静止）。
            if (true) {
                return;
            }

            // ↓↓↓ 以下为历史实现，已不执行 ↓↓↓
            // ★ eTick 冻结：把 startTick 往回退，让 f2 继续增长。
            //
            // 这是**唯一**的写入点，而且只写 startTick 这一个"起点"字段。
            // 前几轮我分别写过 seekTime / limbSwing / lastTick /
            // hasUpdatedThisTick / isTickTriggered，每一个都和 YSM 的状态机打架：
            //   · 写 limbSwing  → 破坏不变量 limbSwing <= f2 → f3 恒负 → 全死
            //   · 写 seekTime   → 与 YSM 叠加 → 2 倍速；YSM 停推时又变"极慢地推一推"
            //   · 写 isTickTriggered=false → 关掉 z2/z3 唯一通路 → 卡顿
            //   · 写 lastTick=-1 → 每帧走 if 分支，currentTick 永不被拉回
            //
            // 只退 startTick 的好处：f2 变大 → f3 > 0 → YSM **自己**去做
            // `limbSwing = f2; seekTime += f3;`，所有联动逻辑（rateLimiter、
            // hasUpdatedThisTick、wasActive）都按它原本的设计走，我们不介入。
            float back = Math.min(advance, MAX_CATCHUP_TICKS);
            manager.O00OOOooOoooOoo0o0o0oO0O -= back;   // startTick -= 本帧流逝的时间
            YsmDebug.log("rebase",
                    "eTick frozen at {} -> startTick={} (-{}) f2={} limb={}",
                    entityTick, manager.O00OOOooOoooOoo0o0o0oO0O, back,
                    (entityTick + clamp01(this.lastPartialTick))
                            - manager.O00OOOooOoooOoo0o0o0oO0O,
                    manager.o0OOooo0o0OO00OoOOOo0o0O);
        } catch (Throwable ignored) {
            // 不该因为时钟补偿失败而影响渲染。
        }
    }



    /**
     * 把指定模型绑定到这个 animatable 上。
     *
     * <p><b>只在模型 id 真的变化时才绑定</b>。YSM 的 initModelWithTexture 会重置
     * 动画控制器状态，每帧都调会导致动画永远停在第一帧、动作播不完。
     * 这是前几轮踩过的坑。
     *
     * @param modelId YSM 模型 id
     * @return 绑定后模型是否就绪
     */
    public boolean bindModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        if (!modelId.equals(this.boundModelId)) {
            this.boundModelId = modelId;
            // initModelWithTexture(modelId, textureName)
            // 贴图名传空串：YSM 在找不到指定贴图时会自动回退到模型的第一张贴图，
            // 正好是我们想要的默认外观（用户要求不做高级设置）。
            this.Oo0Oo0o00O00Oo0OOoOOoooo(modelId, "");
        }
        // isModelReady()
        boolean ready = this.oOOo0Ooo0oOoo0O0OOOOo0oo();

        // ★★★★★ 模型就绪后补装动画控制器（第二十八轮的核心修复）。
        //
        // a_() 在构造期被调用，那时 bundle/resources 还是 null，
        // install() 直接 return → **一个控制器都没注册**。
        // 后果：所有 predicate 永不运行，只能播模型自带的 post_main（待机），
        // 无法切换到走路/攻击/死亡 —— 正是用户描述的
        // 「待机动画正常推进，但不会更改为其他动画」。
        //
        // 玩家死亡时客户端换掉 NPC 实体对象 → 我们为新对象建新 animatable
        // → 新 animatable 就落进这个坑（开局那个是因为先 bind 后用才没事）。
        // 日志铁证：整场只有一次 `controllers installed (27)`，死亡后没有第二次。
        if (ready && !this.controllersInstalled) {
            this.controllersInstalled = YsmNpcAnimationControllers.install(this);
            YsmDebug.log("install",
                    "late install after model ready -> {}", this.controllersInstalled);
        }
        return ready;
    }

    /**
     * buildRenderShape(ModelAssembly, boolean) —— 构造模型外壳。
     *
     * <p>直接用父类提供的 {@code TexturedModelWrapper}，参数与 YSM 给玩家和女仆用的
     * 完全一致：收集全部贴图、立即注册、分辨率 600。
     */
    @Override
    protected oOo00OoOO00O000ooO0Oo00o.Oo0Oo0o00O00Oo0OOoOOoooo o0OOooo0o0OO00OoOOOo0o0O(
            oOo0oO0OOo0ooOoo0oOo0oOo assembly, boolean isDefault) {
        return new Oo0Oo0o00O00Oo0OOoOOoooo(
                assembly, isDefault, true, true, TEXTURE_RESOLUTION);
    }

    /**
     * getAnimationProcessor() —— 提供骨骼模型。
     *
     * <p>取模型包里的主模型（{@code getAnimationBundle().getMainModel()}），
     * 与 YSM 的 LivingAnimatable 对玩家/女仆的做法一致。
     */
    @Override
    public OOoOoooOOooO0o0000o0O0o0 o0o0Oooo00OoOoOOOooo0000() {
        // getModelAssembly().getAnimationBundle().getMainModel()
        return this.O0ooooOO0oOo000O0Oo00OOO().Oo0Oo0o00O00Oo0OOoOOoooo().Oo0Oo0o00O00Oo0OOoOOoooo();
    }

    /**
     * registerAnimationControllers() —— 装配动画控制器。
     *
     * <p>这里是"NPC 能不能正常播待机/走路/战斗动画"的关键。
     * 交给 {@link YsmNpcAnimationControllers} 处理，原因见那个类的注释：
     * YSM 自带的玩家控制器组内部把实体当 Player 用，不能直接套。
     */
    @Override
    protected void a_() {
        // ★ 这里发生在 animatable **构造期**，模型往往还没 bind，
        // 此时 install() 会因为 bundle/resources 为 null 而什么都不装。
        // 记录结果，等模型就绪后由 bindModel() 补装（见那里的注释）。
        this.controllersInstalled = YsmNpcAnimationControllers.install(this);
    }

    /** 动画控制器是否已经成功注册过（构造期模型未就绪时会失败）。 */
    private boolean controllersInstalled;

    /**
     * shouldSkipAnimation(AnimationEvent) —— 固定返回 {@code false}。
     *
     * <h2>★★★ 这是"动画卡顿/骨骼冻结/走路不播/BC 姿势残留"的总根因</h2>
     * 我一度把它写死成 {@code true}，理由是"z3 需要 !z"。**那个推理只看了 z3，
     * 漏了真正的总开关 z2**：
     * <pre>
     * boolean z  = !shouldSkipAnimation(event);
     * boolean z2 = (isTickTriggered &amp;&amp; !hasUpdatedThisTick)
     *              || wasAnimationActiveLastTick || z;      // ← 总开关
     * boolean z3 = (!z || ...) &amp;&amp; isTickTriggered &amp;&amp; !hasUpdatedThisTick;
     * if (z2) {                                            // ← setupAnim 在 z2 里！
     *     if (z3) { hasUpdatedThisTick = true; ... }
     *     setupAnim(seekTime, z3);                          // 真正求值动画
     *     getEvaluationContext().tickAnimation(event, ctx, z3, ...);
     *     wasAnimationActiveLastTick = z;
     * }
     * </pre>
     * {@code z3} 只是传给 {@code setupAnim} 的参数（"是否算作新 tick"），
     * 而 <b>{@code z2} 才决定这一帧要不要求值动画</b>。
     *
     * <p>返回 true 的后果（日志实测）：
     * <ul>
     *   <li>{@code z = false} → {@code wasAnimationActiveLastTick} 每帧被赋成 false</li>
     *   <li>{@code z2} 只剩 {@code isTickTriggered && !hasUpdatedThisTick}</li>
     *   <li>而 {@code isTickTriggered} 由 rateLimiter 限流（refreshRate 约 10~60），
     *       所以**只有少数帧会求值动画**，其余帧骨骼保持上一次的值不变</li>
     * </ul>
     * 这一条解释了之前所有症状：
     * 动画像 15fps、走路动画设置成功却不播（{@code [play:walk] animAfter=walk}
     * 但 {@code [bones]} 每帧完全相同）、BC 攻击姿势在攻击结束后残留不清、
     * 玩家死亡后"诡异的水平平举"（就是残留的 BC 姿势）。
     *
     * <p>父类 {@code GeoEntity} 的原实现是
     * {@code event.isFirstPerson() || OculusCompat.isPBRActive()} ——
     * 渲染实体时 {@code isFirstPerson} 为 false，所以**原本就返回 false**，
     * {@code z = true} → {@code z2} 恒真 → **每帧求值** → 流畅。
     *
     * <p>我们返回 false 即恢复这个语义。之所以不直接继承父类实现，
     * 是为了不依赖 {@code ModelPreviewRenderer.isFirstPersonMode} 那个
     * **静态全局标记**（玩家死亡会打断 renderLevel 使其残留成 true，
     * 导致动画时好时坏）。固定 false 让行为完全确定。
     *
     * <p><b>教训</b>：一个布尔量参与多个条件时，必须找出**哪个条件控制目标行为**。
     * 我盯着 z3 推了半天，而 setupAnim 其实在 z2 里。
     */
    /**
     * setCustomAnimations(runtime, event) —— 在交给父类之前，把
     * {@code event.currentTick} 换成**客户端全局 tick**。
     *
     * <h2>★★★★★ 这才是「玩家一死，NPC 动画就死」的正解（第二十四轮）</h2>
     *
     * 用户的问题问到了本质：「为什么玩家只要一死亡，这个问题就一定能稳定复现」。
     * 答案是一条**确定性因果链**，每一步都必然发生：
     * <ol>
     *   <li>玩家死亡 → 客户端重建 NPC 实体对象
     *       （[ident] 日志：objId 1565669157 → 1453138968）</li>
     *   <li>新对象的 {@code entity.tickCount} 冻结
     *       （[clock] 日志：eTick 停在 207 / 2649 再也不动）</li>
     *   <li>YSM 的推进公式：
     *       {@code f2 = eTick + partialTick - startTick; f3 = f2 - limbSwing;}
     *       只有 {@code f3 > 0} 才推进 → f2 卡住 → <b>永不推进</b></li>
     * </ol>
     * 所以它不是偶发 bug，而是必然结果；也因此**不可逆**
     * （tickCount 不会自己恢复），只能大退游戏。
     *
     * <p><b>前几轮全错在方向上</b>：我一直试图修补 f2 的输入 ——
     * 退 startTick、写 limbSwing、推 seekTime、复位 hasUpdatedThisTick ——
     * 全都是在跟一个**已经死掉的时基**抢，注定失败，而且每次都因为
     * 破坏 YSM 的内部不变量而引入新症状（2 倍速、半速、卡顿、完全静止）。
     *
     * <p><b>正解是彻底不依赖 {@code entity.tickCount}。</b>
     * 而 YSM 自己就留了这条路 —— 它给模型预览用的就是全局 tick：
     * <pre>
     * currentTick = (this instanceof IPreviewAnimatable)
     *     ? ClientTickEvent.getTickCount()   // 全局 tick，游戏在跑就递增
     *     : entity.tickCount;                // 我们原本走这条，会死
     * </pre>
     * 我们不实现 {@code IPreviewAnimatable}（那个接口被 29 个类引用，
     * 会连带触发预览专用逻辑），而是在这里直接改写 {@code event.currentTick}
     * —— 它是 {@code AnimationEvent} 上**唯一的 public 非 final 字段**
     * （其余全是 private final），而 {@code setCustomAnimations} 的第一行
     * 正是 {@code fload_3 = event.currentTick}（字节码 offset 0-4 已核对）。
     *
     * <p>这样 {@code f2} 恒定增长，YSM 自己完成
     * {@code limbSwing = f2; seekTime += f3;} 以及全部联动
     * （rateLimiter / hasUpdatedThisTick / wasActive），我们一个内部字段都不用碰。
     */
    @Override
    protected void Oo0Oo0o00O00Oo0OOoOOoooo(
            com.elfmcys.yesstevemodel.O0O00Oo0o0oO0oo0oO00OOoO<?> runtime,
            com.elfmcys.yesstevemodel.OO00O0o0OooOOOo00OO00o00<
                    com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo<LivingEntity>> event) {
        try {
            float global = clientTick() + clamp01(this.lastPartialTick);

            // ★★★★★ 必须保持时间轴**连续**，不能直接把 currentTick 从
            // 实体时间轴跳到全局 tick。第二十四轮就是这么做的，结果动画仍然冻结，
            // 日志里 f2=-3654（两个时间轴差 3655 tick）暴露了问题。
            //
            // 根因在 ControllerRegistry.process（reg.txt offset 30-46）：
            //     float t = event.currentTick;
            //     if (t - this.lastResetTick >= 1200.0f) {   // ★
            //         this.state.reset();                    // offset 53
            //         this.lastResetTick = t;
            //     }
            // 时间轴一次跳 3655 tick > 1200 → **每帧都触发 reset**
            // → 动画状态被反复清空 → 骨骼永远停在 bind pose。
            // 这解释了「seekTime 正常增长但骨骼完全不动」这个矛盾现象。
            //
            // 正解：记下首帧的偏移量，之后一直减掉它。
            // 这样 currentTick 的**数值范围与原来一致**（不会触发 1200 的 reset），
            // 但它的**增长来源**变成了全局 tick（不会因 entity.tickCount 冻结而停）。
            if (!this.globalClockRebased) {
                this.globalClockRebased = true;
                // ★ 偏移必须让 currentTick **紧接着 lastTick 继续走**，
                // 而不是对齐到 eTick。理由（第二十六轮日志铁证）：
                //   YSM 复位 hasUpdatedThisTick 的唯一入口是
                //   `if (currentTick > lastTick)`（offset 20-41）。
                //   如果 currentTick 出现向下跳变（<= lastTick），复位永不发生
                //   → z2 = isTickTriggered && !hasUpdatedThisTick 恒假
                //   → setupAnim 从不执行 → 骨骼冻死。
                //   日志：swap 后 seek 从 1558 跳到 2193，hasUpd 从此恒 true。
                //
                // 取 lastTick + 一小步作为新的起点，保证严格递增且无跳变。
                // oooooooOOoOOoO00OooOo00O = lastTick（protected float，我们继承了）
                float lastTick = this.oooooooOOoOOoO00OooOo00O;
                float resumeAt = Math.max(lastTick, 0.0F) + 0.05F;
                this.globalClockOffset = global - resumeAt;
                YsmDebug.log("gclock",
                        "clock base set: offset={} (global={} resumeAt={} lastTick={})",
                        this.globalClockOffset, global, resumeAt, lastTick);
            }

            // event.currentTick = 全局 tick - 偏移 → 与实体时间轴同量级且单调递增
            event.Oo0Oo0o00O00Oo0OOoOOoooo = global - this.globalClockOffset;

            // ★★★★★ 补回被 isAlive() 门清零的 limbSwing / limbSwingAmount。
            //
            // 这是「玩家死后 NPC 只播待机、不切走路/攻击/死亡」的**最后一环**。
            //
            // YSM 的 processAnimationImpl（字节码 offset 105-160）：
            //     if (!shouldSit && entity.isAlive() && livingEntity != null) {
            //         limbSwingAmount = walkAnimation.speed(partialTick);
            //         limbSwing       = walkAnimation.position(partialTick);
            //     }
            //     // 否则两者保持 0
            //
            // 玩家死亡后 Orphie 自己的 isAlive() 变成 false（血量还有 13、仍在追人），
            // 于是 YSM 喂给模型的 limbSwingAmount 恒为 0。
            // 而该模型声明的是 player.post_main —— 它在 player.main **之后**执行，
            // 其 molang 脚本靠 limbSwingAmount 判断"在不在走"，读到 0 就一直播待机，
            // **盖住了我们在 player.main 上播的 walk**。
            //
            // 日志正好对上：[play:walk] playing=true（walk 真的在播），
            // 但玩家看到的是待机；[speed] 里 walkAnim 恒为 3.8e-6。
            //
            // 我们自己的 predicate 早就绕开了这道门（改用 getDeltaMovement），
            // 但**模型作者的脚本绕不开** —— 它只能读 YSM 喂进去的值。
            // 所以在这里把真实值补回去，让模型脚本恢复正常判断。
            // ★★★★★ 第三十二轮：这两个反射补丁**已彻底停用**。
            //
            // 它们是"治标"时期的产物（试图给抓着旧实体的 animatable 打补丁）。
            // 第三十一轮找到真根因后 —— 实体对象被换掉时丢弃旧 animatable、
            // 用新实体重建（见 YsmBridge.render 的 swapped 分支）——
            // getX()/deltaMovement/walkAnimation 全都是活的，不需要补。
            //
            // **而且继续开着是有害的**：它们会用估算值覆盖真实的
            // MovementState.velocity / limbSwing，反而让模型状态机判断出错。
            //
            // 代码保留仅作为历史记录与知识存档（详见各方法的注释，
            // 里面记着 ysm.ground_speed2 的完整实现链，做 TACZ 等兼容时有用）。
            if (REVIVE_FALLBACK_ENABLED) {
                reviveLimbSwing(event);
                reviveGroundSpeed();
            }
        } catch (Throwable ignored) {
            // 改写失败就退回原值，不影响渲染
        }
        super.Oo0Oo0o00O00Oo0OOoOOoooo(runtime, event);
    }

    @Override
    protected boolean Oo0Oo0o00O00Oo0OOoOOoooo(
            com.elfmcys.yesstevemodel.OO00O0o0OooOOOo00OO00o00<?> event) {
        // ★★★★★ 这个布尔我猜错过三次。第十七轮把整段 setCustomAnimations
        // 反汇编逐条算完，才知道**两个取值都不能固定**：
        //
        //   z  = !shouldSkipAnimation(event)
        //   z2 = (isTickTriggered && !hasUpdatedThisTick)      // offset 192-203
        //        || wasAnimationActiveLastTick                 // offset 206-210
        //        || z;                                         // offset 213-215
        //   z3 = z ? (seekTime == 0 && !hasUpdatedThisTick)    // offset 225-243
        //          : (isTickTriggered && !hasUpdatedThisTick); // offset 246-257
        //   if (z2) { if (z3) hasUpdatedThisTick = true;
        //             setupAnim(seekTime, z3); ... }            // offset 275/320
        //   wasAnimationActiveLastTick = z;                    // offset 355-358
        //
        // 固定 true  → z=false → wasActive 被每帧清成 false
        //              → z2 只剩限流项 → 多数帧不求值 → 卡顿（第十五轮症状）
        // 固定 false → z=true  → z3 要求 **seekTime == 0**，而 seekTime 一直在涨
        //              → z3 恒 false → 动画完全不推进（第十七轮症状）
        //
        // YSM 的原意：z=true 只适用于**第一帧**（seekTime 恰好为 0），
        // 之后靠 wasActive 维持 z2、靠限流项驱动 z3。
        //
        // ★★★★★ 第十八轮修正：上面那段对 z3 的读法**是错的**，
        // 我把 offset 246 当成了 else 分支，其实它是**共同的必经路径**。
        // 逐条追跳转（225 ifeq 246 / 236 ifne 264 / 243 ifne 264）：
        //
        //   z3 = (z ? (seekTime == 0 && !hasUpdatedThisTick) : true)
        //        && isTickTriggered && !hasUpdatedThisTick;
        //
        // 也就是说 `isTickTriggered && !hasUpdated` 是**两条路都要过**的门，
        // 而 z=true 只是额外再加一个 `seekTime == 0` 的约束。
        //
        // 用户实测把这件事钉死了（「死前不推进、死后卡顿推进」）：
        //   · 死前 clockTakeover=false → z=true  → z3 要 seekTime==0 → 恒假 → 不推进
        //   · 死后 clockTakeover=true  → z=false → z3 只需限流项 → 能推进
        // 正好是我这个字段的两个分支，说明取值完全反了。
        //
        // 所以应当**固定返回 true**（z=false）：
        //   · z3 只需 `isTickTriggered && !hasUpdated`，去掉 seekTime==0 的死约束
        //   · z2 = (isTickTriggered && !hasUpdated) || wasActive || z
        //     首项仍然成立，所以 z2 照样为真 —— 这是固定 true 安全的关键
        //   · RateLimiter：interval = 1/refreshRate ≈ 0.0167，
        //     而 request(seekTime/20f) 每 tick 输入 +0.05 > interval
        //     → isTickTriggered **每 tick 为真** → 不是"限流导致卡顿"
        //
        // 第十五轮以为"固定 true 会卡顿"，真凶其实是 syncAnimationClock 里
        // 把 isTickTriggered 强制置 false（已删除）。
        //
        // ★★★ 保持 true，**不要再试 false**（第十七/十八轮各实测过一次）：
        //   z3 = (z ? (seekTime == 0 && !hasUpd) : true)
        //        && isTickTriggered && !hasUpd;
        // 返回 false → z=true → z3 要求 `seekTime == 0`，而 seekTime 一直在涨
        // → z3 恒假 → setupAnim 永不推进（用户实测「死之前动画不推进」）。
        // 返回 true  → z=false → z3 只需限流项，而 rateLimiter 每 tick 为真
        // （interval≈0.0167，输入每 tick +0.05）→ 正常推进。
        // z2 由首项 `isTickTriggered && !hasUpd` 保住，不受 z=false 影响。
        return true;
    }

    /**
     * getRefreshRate() —— 固定返回一个高刷新率。
     *
     * <h2>★★★ 这是"动画卡顿/待机不推进"的最后一块拼图</h2>
     * 父类 {@code AnimatableEntity.getRefreshRate()} 对**非玩家实体**有降频逻辑：
     * <pre>
     * if (localPlayer != null &amp;&amp; localPlayer != this.entity) {
     *     if (!this.isFirstFrameAfterReset) {
     *         return 10;                    // ← 我们的 NPC 恒走这条
     *     }
     *     if (distance &gt; 64) return 30;
     *     if (distance &gt; 40) return 60;
     * }
     * return ClientTickEvent.getRefreshRate();
     * </pre>
     * 这个值喂给 {@code rateLimiter.setRefreshRate()}，而
     * {@code isTickTriggered |= rateLimiter.request(seekTime / 20f)} 决定
     * 动画这一帧能不能求值。返回 10 意味着**每秒只有约 10 帧会更新动画**
     * —— 这正是用户反复反馈的"像 15fps"、"动画不推进"。
     *
     * <p>YSM 这么设计是为了省性能（远处的其他玩家不需要高频动画），
     * 但我们的 NPC 是玩法核心、且数量有限，值得给足帧率。
     *
     * <p>取显示器刷新率（与 YSM 给本地玩家用的一致），
     * 保证 {@code isTickTriggered} 每帧为真。
     */
    @Override
    public int oOo0oOOOO0ooOOO0oOoOO0oo() {
        int rate = com.elfmcys.yesstevemodel.o0O0000ooOOO0O0oo0OOOoOo.o0OOooo0o0OO00OoOOOo0o0O();
        // 兜底：拿不到刷新率时给 60，别退回 10。
        return rate > 0 ? rate : 60;
    }
}

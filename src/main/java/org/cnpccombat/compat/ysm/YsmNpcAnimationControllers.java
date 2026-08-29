package org.cnpccombat.compat.ysm;

import com.elfmcys.yesstevemodel.O0Oooo00oOo00O00OoOOOooO;
import com.elfmcys.yesstevemodel.O0oOo0OoO0O0o0000o0O00o0;
import com.elfmcys.yesstevemodel.O0ooO0o0O0O0ooooOO0o00OO;
import com.elfmcys.yesstevemodel.OO00O0o0OooOOOo00OO00o00;
import com.elfmcys.yesstevemodel.Oo0OOo00Oo00oO00Ooo0O00O;
import com.elfmcys.yesstevemodel.Oo0o0o0o0OOo0000ooo00o00;
import com.elfmcys.yesstevemodel.OoO00O0oOo0000oo0OOO0oO0;
import com.elfmcys.yesstevemodel.OoO0o0ooOO0ooOo0oO0oOOoo;
import com.elfmcys.yesstevemodel.OoOoOO0O00oOoO0o0ooOO0oO;
import com.elfmcys.yesstevemodel.OooOO0OOOoO0oOO0OOOO0Ooo;
import com.elfmcys.yesstevemodel.oO000ooO00OO000oOOoo0OoO;
import com.elfmcys.yesstevemodel.oO00o00OoO0OO0O0oooooO0o;
import com.elfmcys.yesstevemodel.oO00oooOoOo0O0oOo000o0Oo;
import com.elfmcys.yesstevemodel.oOOO0Oo0O00oo0O0oO0OooO0;
import com.elfmcys.yesstevemodel.oOooOOOOo0O0O0o00ooo000o;
import com.elfmcys.yesstevemodel.ooOo0OoOoo0ooOOoOO0o000O;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 给 NPC 装配 YSM 动画控制器。
 *
 * <h2>为什么不能直接用 YSM 自带的玩家控制器组</h2>
 * YSM 的玩家控制器组里，{@code main} 槽位用的是 {@code AnimationManager}，
 * 它内部把实体直接当 {@code Player} 用（取 {@code getAbilities().flying} 等），
 * 喂 NPC 会 {@code ClassCastException}。女仆那套则依赖 TLM 的类，
 * 而且未装 TLM 时装配器是 null。所以只能自己装配。
 *
 * <h2>但槽位体系必须照抄 YSM（第二轮测试的教训）</h2>
 * 第一版只注册了 4 个槽位（main / hold_mainhand / hold_offhand / swing），
 * 导致三个 bug（实测雪狐模型文件得出）：
 * <ul>
 *   <li><b>没有待机动画</b>：伸懒腰动画定义在 {@code player.post_main} 控制器里，
 *       靠 {@code v.next_idle} 变量在 5 个状态间跳转，而 {@code v.next_idle} 由
 *       {@code idle} 动画的时间线事件赋值。少了 post_main 槽位就永远播不到。</li>
 *   <li><b>多形态模型全部显示</b>：模型用 {@code "scale": 0} 隐藏不该出现的形态，
 *       例如雪狐的狐狸形态根骨骼 {@code FOX} 是
 *       {@code "parallel1": {"bones":{"FOX":{"scale":"v.roaming.a"}}}}。
 *       少了 parallel 槽位，没人驱动这个 scale，形态就全露出来。</li>
 *   <li><b>攻击时整个上半身摆动</b>：模型靠 pre_/post_ 叠加槽位做局部覆盖，
 *       只有一个 main 槽位时整条动画（含 UpBody）被直接应用。</li>
 * </ul>
 *
 * <p>所以这一版<b>直接复用 YSM 自己的槽位处理器</b>
 * （{@code ControllerSlotBinder} / {@code ParallelProcessor}），
 * 由它们按 YSM 的命名规则去模型里扫 {@code player.<slot>} /
 * {@code player_ctrl_<slot>_*} 条目，行为与 YSM 给玩家的完全一致 ——
 * 这才是「能正确读 YSM 模型文件」。
 *
 * <p><b>客户端专用</b>，引用 YSM 类型，只能在 {@link YsmCompat#isLoaded()} 为真时触碰。
 */
@OnlyIn(Dist.CLIENT)
final class YsmNpcAnimationControllers {

    /** YSM 约定的控制器名前缀。模型里的条目形如 "player.main"、"player.post_main"。 */
    private static final String PREFIX = "player";

    /**
     * 走路判定阈值，单位是**每 tick 的水平位移（方块）**。
     *
     * <p><b>不能沿用 YSM/vanilla 的 0.05</b>：那个值是给
     * {@code limbSwingAmount} 用的，量纲完全不同（行走时约 0.6~1.0）。
     * 我们改用真实位移后（见 {@link #walkSpeed}），行走实测只有 0.02 左右
     * （NPC 受 AI 限速，比玩家慢），沿用 0.05 会把行走误判成静止 ——
     * 这正是第十三轮"只有待机动画"的最后一环。
     *
     * <p>取 0.003：静止时的浮点噪声在 1e-4 量级，行走 0.02~0.1，
     * 这个阈值能可靠区分两者。
     */
    private static final float MIN_SPEED = 0.003f;

    // 两个 enum 用 valueOf 取常量：enum 的真名保留在 class 文件里，
    // 比写混淆字段名更抗版本变化，也更可读。
    private static final O0oOo0OoO0O0o0000o0O00o0 CONTINUE =
            O0oOo0OoO0O0o0000o0O00o0.valueOf("CONTINUE");
    private static final O0oOo0OoO0O0o0000o0O00o0 STOP =
            O0oOo0OoO0O0o0000o0O00o0.valueOf("STOP");
    private static final O0oOo0OoO0O0o0000o0O00o0 PAUSE =
            O0oOo0OoO0O0o0000o0O00o0.valueOf("PAUSE");
    private static final OooOO0OOOoO0oOO0OOOO0Ooo LOOP =
            OooOO0OOOoO0oOO0OOOO0Ooo.valueOf("LOOP");
    private static final OooOO0OOOoO0oOO0OOOO0Ooo PLAY_ONCE =
            OooOO0OOOoO0oOO0OOOO0Ooo.valueOf("PLAY_ONCE");

    private YsmNpcAnimationControllers() {
    }

    /**
     * 装配控制器。
     *
     * <p>槽位顺序照抄 YSM 的 {@code PlayerAnimationController}（顺序即叠加优先级），
     * 只去掉两类：
     * <ul>
     *   <li>{@code gui_hover} / {@code gui_focus} —— 仅用于模型预览界面，NPC 用不到；</li>
     *   <li>{@code fire} / {@code carry_on} / {@code parcool} —— 依赖第三方 mod 的兼容槽位。</li>
     * </ul>
     * {@code armor} 也不装：用户明确要求不做盔甲覆盖。
     */
    static boolean install(YsmNpcAnimatable animatable) {
        Oo0o0o0o0OOo0000ooo00o00 bundle = bundleOf(animatable);
        OoO00O0oOo0000oo0OOO0oO0 resources = resourcesOf(animatable);
        if (bundle == null || resources == null) {
            // ★★★★★ 这个 early-return 是「玩家死亡后动画不切换」的根因（第二十八轮）。
            //
            // install() 由 a_()（registerAnimationControllers）调用，而那发生在
            // animatable **构造期** —— 此时模型还没 bind，bundle/resources 都是 null
            // → 一个控制器都不注册 → 所有 predicate 永不运行
            // → 只能播模型自带的 post_main（待机），**无法切换到走路/攻击/死亡**。
            //
            // 玩家死亡时客户端换掉 NPC 实体对象，我们会为新对象建**新的**
            // animatable，于是新对象就落进这个坑；而日志里那些 [play:walk]
            // 来自**旧** animatable（仍被 YSM 内部引用），所以看起来"predicate 在跑"。
            //
            // 日志铁证：整场只有**一次** `controllers installed (27)`，
            // 出现在开局；玩家死亡后再也没有第二次 —— 新 animatable 是空的。
            YsmDebug.log("install",
                    "SKIP: model not ready (bundle={} resources={}) -> will retry after bind",
                    bundle != null, resources != null);
            return false;
        }

        List<oO00o00OoO0OO0O0oooooO0o<YsmNpcAnimatable>> installers = new ArrayList<>();
        DataProvider provider = new DataProvider();

        // pre_parallel / parallel：驱动 v.roaming.* 之类的持久变量，
        // 多形态模型靠它把不用的形态 scale 到 0。**必须有，否则形态全显示。**
        parallel(installers, "pre_parallel", provider, bundle, resources, false);

        // vehicle：坐骑/载具姿态。predicate 只用 LivingEntity 的 getVehicle。
        slot(installers, "vehicle", provider, bundle, resources,
                0.1f, YsmNpcAnimationControllers::vehicleState);

        // pre_main / main / post_main：基础运动状态机。
        // post_main 是待机动画（伸懒腰）所在的槽位，**必须有**。
        //
        // ★ main 用 slot()（直接 new Composite）而不是 binder()：
        // Composite 在 init 时会查模型有没有同名的**控制器条目**，
        // 有就用模型的状态机、我们的 predicate 根本不会被调用。
        // 实测 16_tactics 声明了 player.post_main / parallel_5 / parallel_6，
        // 没有 player.main，所以 main 槽位会走我们的 predicate。
        binder(installers, "pre_main", provider, bundle, resources, 0.0f, (e, ev) -> STOP);
        slot(installers, "main", provider, bundle, resources,
                0.1f, YsmNpcAnimationControllers::mainState);
        binder(installers, "post_main", provider, bundle, resources, 0.0f, (e, ev) -> STOP);
        YsmDebug.once("main slot: model declares player.main = {}",
                declaresBareSlot("main", provider, bundle, resources));

        // 持物姿态
        binder(installers, "pre_hold", provider, bundle, resources, 0.0f, (e, ev) -> STOP);
        slot(installers, "hold_offhand", provider, bundle, resources, 0.1f,
                (e, ev) -> holdState(e, ev, InteractionHand.OFF_HAND));
        slot(installers, "hold_mainhand", provider, bundle, resources, 0.1f,
                (e, ev) -> holdState(e, ev, InteractionHand.MAIN_HAND));
        binder(installers, "post_hold", provider, bundle, resources, 0.0f, (e, ev) -> STOP);

        // 挥击/攻击。过渡 0 —— 攻击动画要立刻起效。
        //
        // ★ swing 必须用 binder（而不是 slot）：模型作者的 BetterCombat 控制器
        // 是靠**文件名** `xxx@player_ctrl_swing.molang` 挂上来的，
        // 这类脚本只存在于 ModelResourceBundle.getEvents() 里，
        // 只有 ControllerSlotBinder 会去扫 `^player_ctrl_swing(_.+)?$`。
        // 用 slot() 只会注册我们自己的 predicate，模型的 BC 脚本被无声忽略
        // → NPC 只能播通用 swing_hand，播不了 BC 动画（第十二轮修的 bug）。
        binder(installers, "pre_swing", provider, bundle, resources, 0.0f, (e, ev) -> STOP);
        binder(installers, "swing", provider, bundle, resources,
                0.0f, YsmNpcAnimationControllers::swingState);
        binder(installers, "post_swing", provider, bundle, resources, 0.0f, (e, ev) -> STOP);

        // 使用物品（吃/喝/举盾/拉弓）
        binder(installers, "pre_use", provider, bundle, resources, 0.0f, (e, ev) -> STOP);
        slot(installers, "use", provider, bundle, resources,
                0.1f, YsmNpcAnimationControllers::useState);
        binder(installers, "post_use", provider, bundle, resources, 0.0f, (e, ev) -> STOP);

        // passenger：被别的实体骑乘时的姿态
        slot(installers, "passenger", provider, bundle, resources, 0.1f, (e, ev) -> STOP);

        // cap：模型作者自定义的兜底控制器
        slot(installers, "cap", provider, bundle, resources, 0.0f, (e, ev) -> STOP);

        // parallel：与 pre_parallel 同理，放在最后叠加
        parallel(installers, "parallel", provider, bundle, resources, true);

        // 全部装上去。顺便统计实际注册了哪些控制器 —— 这是排查
        // "模型的 BC 脚本有没有被挂上"的关键观测点。
        java.util.List<String> registered =
                YsmDebug.enabled() ? new ArrayList<>() : null;
        for (oO00o00OoO0OO0O0oooooO0o<YsmNpcAnimatable> installer : installers) {
            if (installer == null) {
                continue;
            }
            installer.create(animatable, controller -> {
                if (registered != null) {
                    try {
                        // IAnimationController.getName()
                        registered.add(controller.Oo0Oo0o00O00Oo0OOoOOoooo());
                    } catch (Throwable ignored) {
                        registered.add("<name?>");
                    }
                }
                animatable.Oo0Oo0o00O00Oo0OOoOOoooo(controller);
            });
        }
        if (registered != null) {
            YsmDebug.once("controllers installed ({}): {}", registered.size(), registered);
            // 同时把模型声明了哪些条目打出来，用于对照
            // （BC 脚本挂在 events 里，形如 player_ctrl_swing）。
            try {
                YsmDebug.once("model animationEntries: {}",
                        provider.O00OOOooOoooOoo0o0o0oO0O(bundle, resources).keySet());
                YsmDebug.once("model molang events: {}",
                        resources.O00OOOooOoooOoo0o0o0oO0O().keySet());
            } catch (Throwable t) {
                YsmDebug.once("could not dump model entries: {}", t.toString());
            }
        }
        return true;
    }

    /**
     * 注册一个"单控制器"槽位（对应 YSM 的 {@code registerController}）。
     *
     * <p>用 {@code CompositeAnimationController}（混淆名 {@link oO00oooOoOo0O0oOo000o0Oo}）：
     * 它在 init 时会先查模型里有没有同名的<b>动画控制器条目</b>，
     * 有就用模型作者定义的状态机，没有才退回我们的 predicate。
     * 这是「能读 YSM 模型文件里的控制器配置」的关键。
     *
     * <p>注意别和 {@code PredicateBasedController}（{@code oo000oooo0OOoo00O0o0OOOO}）
     * 搞混 —— 两者构造签名一模一样，只能靠字段布局区分。
     */
    private static void slot(List<oO00o00OoO0OO0O0oooooO0o<YsmNpcAnimatable>> out,
                             String slotName, DataProvider provider,
                             Oo0o0o0o0OOo0000ooo00o00 bundle, OoO00O0oOo0000oo0OOO0oO0 resources,
                             float transition, NpcPredicate predicate) {
        String key = PREFIX + "." + slotName;
        out.add((animatable, consumer) -> consumer.accept(
                new oO00oooOoOo0O0oOo000o0Oo<>(animatable, key, transition,
                        new Adapter(predicate))));
    }

    /**
     * 注册一个"可有多个同名槽"的槽位（对应 YSM 的 {@code registerSlotController}）。
     *
     * <p>用 YSM 的 {@code ControllerSlotBinder}（{@link O0ooO0o0O0O0ooooOO0o00OO}）：
     * 它按 {@code ^player\.<slot>(_.+)?$} 去模型的控制器条目里扫，
     * 并且把 molang 事件 {@code player_ctrl_<slot>_*} 也算进来。
     * 也就是说 <b>模型作者定义了几个就注册几个</b>，我们不需要知道名字。
     * 这正是 post_main 这类槽位能生效的原因。
     */
    private static void binder(List<oO00o00OoO0OO0O0oooooO0o<YsmNpcAnimatable>> out,
                               String slotName, DataProvider provider,
                               Oo0o0o0o0OOo0000ooo00o00 bundle,
                               OoO00O0oOo0000oo0OOO0oO0 resources,
                               float transition, NpcPredicate predicate) {
        O0ooO0o0O0O0ooooOO0o00OO<YsmNpcAnimatable, Oo0o0o0o0OOo0000ooo00o00> binder =
                new O0ooO0o0O0O0ooooOO0o00OO<>(PREFIX, slotName, provider,
                        (name, animatable) -> new oO00oooOoOo0O0oOo000o0Oo<>(
                                animatable, name, transition, new Adapter(predicate)));

        // Binder 只注册"模型里确实声明了的"槽位（扫 animationEntries 的
        // `player.<slot>[_后缀]` 和 molang events 的 `player_ctrl_<slot>[_后缀]`）。
        // 如果模型一个都没声明，它返回的 installer 不会建任何控制器，
        // 我们自己的兜底 predicate（例如 swing 的 swing_hand）就永远不跑。
        //
        // 所以在模型**没有声明裸槽位** `player.<slot>` 时补一个，跑我们的 predicate。
        // 判断条件与 binder 内部一致，避免和它注册出来的重名、导致同名控制器注册两次。
        out.add(binder.process(bundle, resources));
        if (!declaresBareSlot(slotName, provider, bundle, resources)) {
            out.add(bare(slotName, transition, predicate));
        }
    }

    /**
     * 模型是否已经声明了裸槽位 {@code player.<slot>}
     * （动画控制器条目或 {@code player_ctrl_<slot>} molang 事件）。
     *
     * <p>只看**不带后缀**的那个名字：带后缀的（如 {@code player.swing_2}）
     * 与我们的兜底控制器不同名，可以共存。
     */
    private static boolean declaresBareSlot(String slotName, DataProvider provider,
                                            Oo0o0o0o0OOo0000ooo00o00 bundle,
                                            OoO00O0oOo0000oo0OOO0oO0 resources) {
        String controllerKey = PREFIX + "." + slotName;
        String eventKey = PREFIX + "_ctrl_" + slotName;
        try {
            if (provider.O00OOOooOoooOoo0o0o0oO0O(bundle, resources).containsKey(controllerKey)) {
                return true;
            }
            return resources.O00OOOooOoooOoo0o0o0oO0O().containsKey(eventKey);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 建一个"裸槽位"控制器（名字 {@code player.<slot>}）作为兜底。
     *
     * <p>与 {@link #slot} 的区别只是语义：这个是配合 {@link #binder} 用的补充，
     * 保证即使模型没声明任何同名槽位，我们自己的 predicate 仍然有机会跑。
     */
    private static oO00o00OoO0OO0O0oooooO0o<YsmNpcAnimatable> bare(
            String slotName, float transition, NpcPredicate predicate) {
        String key = PREFIX + "." + slotName;
        return (animatable, consumer) -> consumer.accept(
                new oO00oooOoOo0O0oOo000o0Oo<>(animatable, key, transition,
                        new Adapter(predicate)));
    }

    /**
     * 注册 parallel 系槽位（对应 YSM 的 {@code registerParallelController}）。
     *
     * <p>用 YSM 的 {@code ParallelProcessor}（{@link OoO0o0ooOO0ooOo0oO0oOOoo}）：
     * 它扫 {@code player.<slot>_0..7} 的控制器条目、{@code player_ctrl_<slot>_*}
     * 的 molang 事件，以及**同名动画**（如 {@code parallel1}），
     * 并把动画名一起绑给控制器。
     *
     * <p>第三个参数（allowExtraSlots）决定匹配 {@code _[0-7]} 还是 {@code _.+}，
     * 与 YSM 对 pre_parallel/parallel 的用法保持一致（两者都传 true）。
     *
     * <p><b>多形态模型的隐藏就靠这个槽位</b>：模型把形态根骨骼的 scale
     * 绑到 molang 变量上，由 parallel 动画驱动。
     */
    private static void parallel(List<oO00o00OoO0OO0O0oooooO0o<YsmNpcAnimatable>> out,
                                 String slotName, DataProvider provider,
                                 Oo0o0o0o0OOo0000ooo00o00 bundle,
                                 OoO00O0oOo0000oo0OOO0oO0 resources,
                                 boolean deprecatedMode) {
        OoO0o0ooOO0ooOo0oO0oOOoo<YsmNpcAnimatable, Oo0o0o0o0OOo0000ooo00o00> processor =
                new OoO0o0ooOO0ooOo0oO0oOOoo<>(PREFIX, slotName, true, provider,
                        (name, animatable, animationName) -> new oO00oooOoOo0O0oOo000o0Oo<>(
                                animatable, name, 0.0f,
                                animationName != null
                                        ? new NamedAnimation(animationName)
                                        : new Adapter((e, ev) -> STOP),
                                deprecatedMode));
        out.add(processor.process(bundle, resources));
    }

    /**
     * 基础运动状态。判定顺序即优先级（高优先级在前），复刻 YSM 的
     * {@code AnimationRegister}，但只用 {@code LivingEntity} 的状态。
     *
     * <p>YSM 那套里唯一的 Player 专属项是 {@code fly}（{@code getAbilities().flying}），
     * NPC 没有 abilities，直接省掉 —— 其余项语义完全一致。
     *
     * <p><b>注意</b>：模型若定义了 {@code player.main} 控制器条目，这个 predicate
     * 根本不会被调用（Composite 会优先用模型的状态机）。它只是兜底。
     */
    private static O0oOo0OoO0O0o0000o0O00o0 mainState(
            LivingEntity e, OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event) {
        // ★ 不能用 event 里的 limbSwingAmount（第十二轮日志铁证）。
        //
        // YSM 的 processAnimationImpl 里：
        //   if (!shouldSit && entity.isAlive() && livingEntity != null) {
        //       limbSwingAmount = livingEntity.walkAnimation.speed(partialTick);
        //   }
        // 也就是说 **isAlive() 为 false 时 limbSwingAmount 恒为 0**。
        //
        // 而 CNPC 的 NPC 死亡待复活期间 isAlive() 返回 false
        // （日志实测：isAlive=false 但 health=19.0，NPC 其实活着并在移动），
        // 于是 YSM 给出的幅度永远是 0 → 走路判定失败 → 只有待机动画。
        //
        // ★★ 也不能用 walkAnimation.speed()（第十三轮日志铁证）：
        //   walkSpeed=5.45e-8  walkPos=67.786(卡死)  deltaXZ=0.0197(在移动)
        // `walkAnimation.update()` 是在 `LivingEntity.aiStep()` 里调的，
        // 而 CNPC 死亡待复活的 NPC **不再走 aiStep** → walkAnimation 永久冻结。
        //
        // 所以改用**实际水平位移**判断是否在移动。这是最可靠的信号：
        // 只要实体位置在变，它就一定非零，与 isAlive/aiStep 都无关。
        //
        // 阈值换算：vanilla 的 limbSwingAmount 与「每 tick 位移」不同量纲，
        // 但我们只用它做"是否在走"的布尔判断，所以取一个经验阈值即可。
        // 行走速度约 0.1 blocks/tick，潜行约 0.03，静止时的浮点噪声 < 0.003。
        float limbSwing = walkSpeed(e);

        if (e.isDeadOrDying()) {
            return play(event, "death", PLAY_ONCE);
        }
        if (e.isAutoSpinAttack()) {
            return play(event, "riptide", LOOP);
        }
        if (e.getPose() == Pose.SLEEPING) {
            return play(event, "sleep", LOOP);
        }
        if (e.isSwimming()) {
            return play(event, "swim", LOOP);
        }
        if (e.getPose() == Pose.SWIMMING) {
            return play(event, limbSwing > MIN_SPEED ? "climb" : "climbing", LOOP);
        }
        if (e.onClimbable()) {
            // 用竖直位移判断上/下爬，与 YSM 的 getVerticalSpeed 同义
            double vertical = e.position().y - e.yo;
            if (vertical > 0.0d) {
                return play(event, "ladder_up", LOOP);
            }
            if (vertical < 0.0d) {
                return play(event, "ladder_down", LOOP);
            }
            return play(event, "ladder_stillness", LOOP);
        }
        if (e.getPose() == Pose.FALL_FLYING && e.isFallFlying()) {
            return play(event, "elytra_fly", LOOP);
        }
        if (e.isInWater() && !e.onGround()) {
            return play(event, "swim_stand", LOOP);
        }
        if (e.hurtTime > 0) {
            return play(event, "attacked", PLAY_ONCE);
        }
        if (!e.onGround() && !e.isInWater()) {
            return play(event, "jump", LOOP);
        }
        if (e.onGround() && e.getPose() == Pose.CROUCHING) {
            return play(event, limbSwing > MIN_SPEED ? "sneak" : "sneaking", LOOP);
        }
        if (e.onGround() && e.isSprinting()) {
            return play(event, "run", LOOP);
        }
        if (e.onGround() && limbSwing > MIN_SPEED) {
            return play(event, "walk", LOOP);
        }
        // ★ 旧的 [main] 日志已删除：它**只在 idle 分支**打印，
        // 导致"日志里没有 [main]"被我误读成"predicate 没被调用"
        // （实际是选了别的分支）—— 第二十六轮因此白查了一轮。
        // 教训：**只在部分分支打印的日志会误导，要么全打要么不打。**
        // 现在动画选择由 [anim] 的切换记录覆盖，这里不再需要日志。
        return play(event, "idle", LOOP);
    }

    /**
     * "移动速度"，用 {@code getDeltaMovement()} 的水平分量。
     *
     * <h2>为什么前两个方案都不行（每次都有日志铁证）</h2>
     * <ul>
     *   <li>{@code event.limbSwingAmount}：YSM 在
     *       {@code isAlive()} 为 false 时**直接置 0**，而 CNPC 死亡待复活的
     *       NPC 正是这种状态（日志 {@code alive=false} 但 {@code health=19}）。</li>
     *   <li>{@code walkAnimation.speed()}：由 {@code LivingEntity.aiStep()} 驱动，
     *       死亡 NPC 不走 aiStep → 永久冻结
     *       （日志 {@code walkSpeed=5.45e-8}、{@code walkPos=67.786} 卡死）。</li>
     *   <li>{@code getX() - xo}：{@code xo/zo} 也是在实体 tick 里更新的，
     *       同样冻结 → 差值恒为 0（日志 {@code limbSwing=0.0} 而
     *       {@code deltaXZ=0.246}，直接证伪）。</li>
     * </ul>
     *
     * <p><b>共同根因</b>：实体不 tick 时，**所有 per-tick 累积的状态都会冻结**。
     * 只有服务端同步过来的字段仍然有效。
     *
     * <p>{@code getDeltaMovement()} 正是这样的字段 —— 它由移动同步包更新，
     * 不依赖客户端 tick。日志实测 {@code deltaXZ=0.246}，准确反映了移动。
     *
     * <p>（我上一轮特意避开它，理由是"AI 寻路转向时某些帧会是 0"。
     * 那个顾虑对**活着**的实体成立，但对死亡待复活的 NPC 来说，
     * 它是唯一还在更新的信号 —— 选错了。）
     */
    private static float walkSpeed(LivingEntity e) {
        // ★★★★★ 第三十二轮：判据回归「实际位移」，并去掉 deltaMovement。
        //
        // 历史教训（每条都有日志支撑）：
        //   · 早期用 event.limbSwingAmount → isAlive()==false 时被 YSM 置 0
        //   · 改用 walkAnimation.speed()   → 实体停 tick 时冻结
        //   · 改用 max(deltaMovement, 位置差) → **站立不动也判成走路**
        //     （deltaMovement 对静止实体仍有重力/碰撞回弹的残留分量，
        //       恒大于 0.003 阈值 → walk 动画永不退出）
        //
        // 第三十一轮修好「animatable 抓着旧实体」之后，实体恢复正常 tick，
        // 所以**位置差**又变成可靠信号了，而且它语义最直接：
        // 位置没变就是没动，不受 deltaMovement 残留值干扰。
        //
        // 用 xo/zo（上一 tick 的位置）而不是 xOld（渲染插值用的量），
        // 因为前者严格对应"上一个 tick 的实际位置"。
        double dx = e.getX() - e.xo;
        double dz = e.getZ() - e.zo;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** 坐骑/载具姿态。只用 LivingEntity 的 getVehicle。 */
    private static O0oOo0OoO0O0o0000o0O00o0 vehicleState(
            LivingEntity e, OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event) {
        var vehicle = e.getVehicle();
        if (vehicle == null || !vehicle.isAlive()) {
            return STOP;
        }
        if (vehicle instanceof net.minecraft.world.entity.animal.Pig
                && has(event, "ride_pig")) {
            return play(event, "ride_pig", LOOP);
        }
        if (vehicle instanceof net.minecraft.world.entity.vehicle.Boat
                && has(event, "boat")) {
            return play(event, "boat", LOOP);
        }
        if (vehicle instanceof net.minecraft.world.entity.Saddleable && has(event, "ride")) {
            return play(event, "ride", LOOP);
        }
        return has(event, "sit") ? play(event, "sit", LOOP) : STOP;
    }

    /**
     * 持物姿态。只在该手确实拿着东西、且没在挥击/使用时生效。
     *
     * <p>动画名沿用 YSM 约定：先试 {@code hold_mainhand:<物品注册名>}，
     * 没有再退到不带后缀的通用条目。
     */
    private static O0oOo0OoO0O0o0000o0O00o0 holdState(
            LivingEntity e, OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event,
            InteractionHand hand) {
        if (e.getItemInHand(hand).isEmpty()) {
            return STOP;
        }
        // 该手正在挥击或使用物品时，让 swing / use 控制器接管。
        // 返回 PAUSE 而不是 STOP —— 与 YSM 的 MainHandHoldPredicate 一致，
        // PAUSE 会冻结当前姿态而不是让它淡出，避免手臂突然弹回。
        if (e.swinging && e.swingingArm == hand) {
            return PAUSE;
        }
        if (e.isUsingItem() && e.getUsedItemHand() == hand) {
            return PAUSE;
        }
        String slot = hand == InteractionHand.MAIN_HAND ? "hold_mainhand" : "hold_offhand";
        String itemKey = slot + ":"
                + BuiltInRegistries.ITEM.getKey(e.getItemInHand(hand).getItem()).getPath();
        if (has(event, itemKey)) {
            return play(event, itemKey, LOOP);
        }
        return has(event, slot) ? play(event, slot, LOOP) : STOP;
    }

    /**
     * 挥击/攻击动画。
     *
     * <p>这是"NPC 攻击时有动画"的关键。本 mod 的战斗逻辑会设置
     * {@code swinging}/{@code swingingArm}，YSM 侧据此播 swing 动画。
     */
    private static O0oOo0OoO0O0o0000o0O00o0 swingState(
            LivingEntity e, OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event) {
        if (!e.swinging || e.isSleeping()) {
            return STOP;
        }

        // ===== A 线：优先用 BetterCombat 的动画 =====
        // 取本 mod 记录的当前 BC 攻击动画名（格式与 YSM 的
        // ctrl.bcombat_attack_animation 一致：**带双引号**，
        // 因为 playerAnimator 的 AnimationJson 用 JsonElement.toString() 存 name）。
        String bcAnim = e instanceof org.cnpccombat.api.NpcAnimationAccess access
                ? access.cnpc$getCurrentAttackAnimation() : null;

        if (bcAnim != null && !bcAnim.isEmpty()) {
            // 模型作者按 YSM 约定命名时，动画键**含引号**（如
            // ["one_handed_slash_horizontal_right"]）；但也可能有人写不带引号。
            // 两种都试，命中即用 —— 这就是 A 线。
            String unquoted = stripQuotes(bcAnim);
            if (has(event, bcAnim)) {
                YsmDebug.log("swing", "A-line: BC anim {} (quoted) -> play", bcAnim);
                return play(event, bcAnim, PLAY_ONCE);
            }
            if (!unquoted.equals(bcAnim) && has(event, unquoted)) {
                YsmDebug.log("swing", "A-line: BC anim {} (unquoted) -> play", unquoted);
                return play(event, unquoted, PLAY_ONCE);
            }
            // A 线未命中：模型里没有这个 BC 动画 -> 落到 B 线
            YsmDebug.log("swing",
                    "A-line MISS: model has no BC anim {} -> fallback to B-line", bcAnim);
        }

        // ===== B 线：播模型自带的通用挥击动画（与玩家完全一致）=====
        //
        // ★ 这里**必须播 swing_hand**，不能返回 STOP 去等"BC 骨骼写入"。
        // 早先的 B 线是把 BC 的骨骼旋转搬进 YSM 骨骼，结果姿态严重诡异
        // （手臂平举到背后）。原因是玩家身上根本没有这种行为：
        // YSM 只读 BC 的动画**名字**，从不复用它的骨骼数据
        // （详见 YsmNpcRenderer.renderEarly 的注释与 task_plan_ysm.md 第 0 条）。
        // 玩家攻击时看到的动作，就是模型自带的 swing_hand 配合 BC 的攻速/音效。
        //
        // 所以"向玩家看齐"= 让 NPC 也播 swing_hand。
        String generic = e.swingingArm == InteractionHand.OFF_HAND
                ? "swing_offhand" : "swing_hand";
        if (has(event, generic)) {
            YsmDebug.log("swing", "generic swing {} (bcAnim={} swingTime={})",
                    generic, bcAnim, e.swingTime);
            return play(event, generic, PLAY_ONCE);
        }
        YsmDebug.log("swing", "no swing animation at all (bcAnim={})", bcAnim);
        return STOP;
    }

    /**
     * 去掉字符串首尾的双引号。
     *
     * <p>BC 动画名经 playerAnimator 的 {@code AnimationJson} 解析后**带引号**
     * （它用 {@code JsonElement.toString()} 存 name），YSM 的模型作者也按带引号
     * 命名动画（见 18_wedding 的注释「动画名要加双引号 是个小bug」）。
     * 但不能假定所有模型都这样，所以两种形式都要试。
     */
    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 使用物品（吃/喝/举盾/拉弓）。 */
    private static O0oOo0OoO0O0o0000o0O00o0 useState(
            LivingEntity e, OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event) {
        if (!e.isUsingItem() || e.isSleeping()) {
            return STOP;
        }
        String name = e.getUsedItemHand() == InteractionHand.OFF_HAND
                ? "use_offhand" : "use_mainhand";
        return has(event, name) ? play(event, name, LOOP) : STOP;
    }

    /** 模型里是否存在这个动画条目。避免让 YSM 去找不存在的动画。 */
    private static boolean has(
            OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event, String name) {
        // event.getAnimatable().getAnimation(name)
        YsmNpcAnimatable animatable = event.o0OOooo0o0OO00OoOOOo0o0O();
        if (animatable == null) {
            return false;
        }
        Oo0OOo00Oo00oO00Ooo0O00O anim = animatable.OOOOo0O0oO0OOo0O0O0Oo0O0(name);
        return anim != null;
    }

    /**
     * 设置动画并返回 CONTINUE。等价于 YSM 的 {@code playAnimationWithLoop}。
     *
     * <p>{@code event.getController()} 返回的是内层的 {@code PredicateBasedController}
     * （Composite 在调 predicate 前把内层设进 event），所以 setAnimation 设给它。
     */
    private static O0oOo0OoO0O0o0000o0O00o0 play(
            OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event, String name,
            OooOO0OOOoO0oOO0OOOO0Ooo loop) {
        // event.getController() —— 内层的 PredicateBasedController
        var controller = event.o0OOO0o0o0OOo000oO00o00O();

        // ★ 记录 setAnimation **前后**控制器的实际状态。
        // 上一轮日志只证明了"我们请求了、动画存在"，但没看到请求是否被接受 ——
        // 结果 walk 每帧都在请求、动画也存在，画面却不动，卡在这个盲区。
        String before = null;
        String currentAnim = null;
        if (YsmDebug.enabled()) {
            try {
                // getCurrentAnimation() / getName()
                before = controller.o0OOooo0o0OO00OoOOOo0o0O();
                currentAnim = controller.Oo0Oo0o00O00Oo0OOoOOoooo();
            } catch (Throwable ignored) {
                // 只是诊断，取不到就算了
            }
        }

        // ★★★★★ 实体对象被换过时，必须先**彻底重置控制器**，
        // 否则 setAnimation 会因为"动画名与循环类型都没变"而短路
        //（runtime.txt offset 9-44），动画实例永不重建 → 骨骼冻在 bind pose。
        //
        // 日志证据（玩家死亡前后对比）：
        //   死前: animBefore=Coded              ← 每帧重新触发，骨骼在动
        //   死后: animBefore=Coded -> walk 恒定  ← 卡住，setAnimation 成了空操作
        YsmNpcAnimatable self = event.o0OOooo0o0OO00OoOOOo0o0O();
        if (self != null && self.consumeControllerResetFlag()) {
            try {
                // PredicateBasedController.reset() —— 清 currentPair + 动画缓存
                controller.oOOOo0OOO0ooooo0O00OO0o0();
                YsmDebug.log("ctrlreset",
                        "controller reset before setAnimation({}) after entity swap", name);
            } catch (Throwable t) {
                YsmDebug.log("ctrlreset", "reset failed: {}", t);
            }
        }

        // event.getController().setAnimation(name, loopType)
        controller.Oo0Oo0o00O00Oo0OOoOOoooo(name, loop);

        // ★★★★★ 第二十八轮诊断：确认 setAnimation 之后**动画实例是否真的建立**。
        //
        // 到这一步为止，日志已经证明以下环节全部正常（玩家死亡后）：
        //   · seekTime 正常增长（待机动画能播 → 时钟没问题）
        //   · walkSpeed 判据正确（chosen=0.2046 > threshold，请求的就是 walk）
        //   · isTickTriggered=true、setAnimation 被调用、controller 已 reset
        // 但动画**不切换**。所以问题在"请求被接受之后、动画实例建立"这一环。
        //
        // getCurrentAnimation() 返回的是控制器**声称**的名字（来自 currentPair），
        // 它在短路时也会是新名字，所以不能证明实例真的换了。
        // 真正能证伪的是 getAnimationInstance()：拿不到实例 = 动画没建立。
        // ★ 只在**动画真正切换**时打一条（取代旧的每帧 [play:*] 刷屏日志）。
        //
        // 旧日志每帧一条、按动画名分组限流，噪音极大，而且它想验证的
        // "setAnimation 是否被接受"已经不是问题了。
        // 现在关心的是「状态机有没有在该切的时候切」，所以只记录**变化**：
        // 一次正常的战斗过程应该能看到 idle -> walk -> swing_hand -> idle 这样的序列。
        // 如果长时间只有一条、或反复在两个动画间抖动，那就是判据出问题了。
        if (YsmDebug.enabled()) {
            String after = null;
            try {
                after = controller.o0OOooo0o0OO00OoOOOo0o0O();
            } catch (Throwable ignored) {
                // 取不到就算了
            }
            if (before == null ? after != null : !before.equals(after)) {
                YsmDebug.once("[anim] {} : {} -> {}", currentAnim, before, after);
            }
        }
        return CONTINUE;
    }

    /** getModelAssembly().getAnimationBundle()，失败返回 null。 */
    private static Oo0o0o0o0OOo0000ooo00o00 bundleOf(YsmNpcAnimatable animatable) {
        var assembly = animatable.O0ooooOO0oOo000O0Oo00OOO();
        return assembly == null ? null : assembly.Oo0Oo0o00O00Oo0OOoOOoooo();
    }

    /** getModelAssembly().getExpressionCache()，失败返回 null。 */
    private static OoO00O0oOo0000oo0OOO0oO0 resourcesOf(YsmNpcAnimatable animatable) {
        var assembly = animatable.O0ooooOO0oOo000O0Oo00OOO();
        return assembly == null ? null : assembly.O00OOOooOoooOoo0o0o0oO0O();
    }

    /** 我们自己的 predicate 形状：只暴露 LivingEntity，杜绝误用 Player。 */
    private interface NpcPredicate {
        O0oOo0OoO0O0o0000o0O00o0 test(
                LivingEntity entity, OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event);
    }

    /**
     * 适配到 YSM 的 {@code IAnimationPredicate} 接口。
     *
     * <p>实体可能为 null（模型预览等场景），此时返回 STOP 而不是抛异常。
     */
    private record Adapter(NpcPredicate delegate)
            implements oOOO0Oo0O00oo0O0oO0OooO0<YsmNpcAnimatable> {
        @Override
        public O0oOo0OoO0O0o0000o0O00o0 Oo0Oo0o00O00Oo0OOoOOoooo(
                OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event,
                O0Oooo00oOo00O00OoOOOooO<?> evaluator) {
            YsmNpcAnimatable animatable = event.o0OOooo0o0OO00OoOOOo0o0O();
            if (animatable == null) {
                return STOP;
            }
            LivingEntity entity = animatable.OO00OOOOo0Ooo0oo0o0Oo0OO();
            if (entity == null) {
                return STOP;
            }
            return this.delegate.test(entity, event);
        }
    }

    /**
     * 固定播放某个具名动画的 predicate，用于 parallel 槽位。
     *
     * <p>parallel 动画（如 {@code parallel1}）是"常驻叠加"的：模型把骨骼的
     * scale/position 绑到 molang 变量上，靠这条动画持续求值。
     * 所以这里无条件 CONTINUE。
     */
    private record NamedAnimation(String animationName)
            implements oOOO0Oo0O00oo0O0oO0OooO0<YsmNpcAnimatable> {
        @Override
        public O0oOo0OoO0O0o0000o0O00o0 Oo0Oo0o00O00Oo0OOoOOoooo(
                OO00O0o0OooOOOo00OO00o00<YsmNpcAnimatable> event,
                O0Oooo00oOo00O00OoOOOooO<?> evaluator) {
            event.o0OOO0o0o0OOo000oO00o00O().Oo0Oo0o00O00Oo0OOoOOoooo(this.animationName, LOOP);
            return CONTINUE;
        }
    }

    /**
     * 喂给 YSM 槽位处理器的数据源，取自模型包。
     *
     * <p>三个方法分别是 getAnimationEntries / getAnimations / getConditionArmor，
     * 与 YSM 自己的 {@code PlayerAnimationDataProvider} 实现完全一致。
     */
    private static final class DataProvider
            implements oOooOOOOo0O0O0o00ooo000o<Oo0o0o0o0OOo0000ooo00o00> {
        /** getAnimationEntries(bundle, resources) —— 模型定义的动画控制器条目。 */
        @Override
        public Object2ReferenceMap<String, ooOo0OoOoo0ooOOoOO0o000O> O00OOOooOoooOoo0o0o0oO0O(
                Oo0o0o0o0OOo0000ooo00o00 bundle, OoO00O0oOo0000oo0OOO0oO0 resources) {
            return bundle.oo0OoO00oOoo000O0000o0oo();
        }

        /** getAnimations(bundle, resources) —— 主模型的动画表。 */
        @Override
        public Object2ReferenceMap<String, Oo0OOo00Oo00oO00Ooo0O00O> o0OOooo0o0OO00OoOOOo0o0O(
                Oo0o0o0o0OOo0000ooo00o00 bundle, OoO00O0oOo0000oo0OOO0oO0 resources) {
            return bundle.O00OOOooOoooOoo0o0o0oO0O();
        }

        /** getConditionArmor(bundle, resources)。 */
        @Override
        public oO000ooO00OO000oOOoo0OoO Oo0Oo0o00O00Oo0OOoOOoooo(
                Oo0o0o0o0OOo0000ooo00o00 bundle, OoO00O0oOo0000oo0OOO0oO0 resources) {
            return bundle.Ooooo0oooO0oooOOOoO0000O().Oo0Oo0o00O00Oo0OOoOOoooo();
        }
    }
}

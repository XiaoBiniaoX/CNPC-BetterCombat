package org.cnpccombat.compat.ysm;

import com.elfmcys.yesstevemodel.OoOOOOOo0oo0000oooOOOoOO;
import com.elfmcys.yesstevemodel.oOO0OOooo00oo0oOOOOoO00O;
import com.elfmcys.yesstevemodel.oo0oOO0000o0Ooooo0OoOo0O;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.cnpccombat.CnpcCombat;
import org.cnpccombat.api.NpcAnimationAccess;

/**
 * 把 YSM 的 {@code ctrl.bcombat_attack_animation} molang 变量扩展到 NPC。
 *
 * <h2>为什么需要这个</h2>
 * YSM 原生支持 BetterCombat，但那个支持是<b>模型侧</b>实现的：模型作者写一个
 * {@code xxx@player_ctrl_swing.molang} 脚本，里面调
 * {@code ctrl.set_animation(ctrl.bcombat_attack_animation)}，
 * 也就是"BC 在播什么，模型就播同名动画"。
 *
 * <p>问题在于 YSM 那个变量是用 {@code clientPlayerEntityVar} 注册的，
 * 它的 {@code validateContext} 要求上下文实体是 {@code AbstractClientPlayer}
 * （因为它读的是 BC 塞在 {@code AbstractClientPlayer.attackAnimation} 字段里的动画名）。
 * NPC 不是玩家 → 校验失败 → 变量取不到值 → 模型里的 BC 分支静默失效。
 * 这就是"玩家攻击动作正常、NPC 不正常"的原因。
 *
 * <h2>做法</h2>
 * YSM 的 {@code CtrlBinding} 是个<b>单例</b>，内部 {@code bindings} 就是一个
 * {@code Object2ReferenceOpenHashMap<String, Object>}，而注册方法
 * （{@code livingEntityVar} 等）只是往这个 map 里 put。
 * 所以我们可以在 YSM 初始化之后，用 <b>{@code livingEntityVar}</b> 覆盖同名 key：
 * <ul>
 *   <li>实体是玩家 → 走 YSM 原来的逻辑（读 BC 的字段），行为不变；</li>
 *   <li>实体是我们的 NPC → 读<b>本 mod 自己</b>记录的当前攻击动画名。</li>
 * </ul>
 * 本 mod 的 {@code ClientNpcAnimationMixin} 本来就在驱动 NPC 的 BC 攻击动画，
 * 动画名它是知道的，只需要暴露出来（见 {@link NpcAnimationAccess}）。
 *
 * <p>这样模型作者写的 BC 脚本对 NPC 也能生效，<b>不需要模型做任何改动</b>。
 *
 * <h2>安全性</h2>
 * <ul>
 *   <li>只覆盖<b>一个</b> key，不动 YSM 其它绑定；</li>
 *   <li>玩家分支<b>委托回 YSM 原来的那个变量对象</b>，不重新实现，
 *       所以 YSM 升级改了读取方式也不会失效；</li>
 *   <li>全程 catch Throwable，失败只打一次日志，不影响渲染。</li>
 * </ul>
 *
 * <p><b>客户端专用</b>，引用 YSM 类型，只能在 {@link YsmCompat#isLoaded()} 为真时触碰。
 */
@OnlyIn(Dist.CLIENT)
final class YsmBetterCombatBinding {

    /** YSM/模型作者约定的变量名，不能改。 */
    private static final String VAR_NAME = "bcombat_attack_animation";

    /** 只安装一次。 */
    private static boolean installed;

    private YsmBetterCombatBinding() {
    }

    /**
     * 安装绑定覆盖。可以反复调用，只有第一次会生效。
     *
     * <p>必须在 YSM 的 {@code CtrlBinding} 单例已经初始化之后调用 ——
     * 否则我们 put 进去的值会被 YSM 自己的构造覆盖掉。
     * 所以调用点放在"第一次渲染 NPC 时"（那时 YSM 早已完成初始化），
     * 而不是 mod 构造期。
     */
    static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            // CtrlBinding.INSTANCE.get()
            OoOOOOOo0oo0000oooOOOoOO binding =
                    OoOOOOOo0oo0000oooOOOoOO.Oo0Oo0o00O00Oo0OOoOOoooo.get();
            if (binding == null) {
                return;
            }
            // 先取出 YSM 原来那个变量对象，玩家分支要委托给它
            Object original = binding.getProperty(VAR_NAME);

            // livingEntityVar(name, evaluator) —— 用 LivingEntity 级的校验替换
            // 原来的 AbstractClientPlayer 级校验，这样 NPC 也能通过。
            binding.O00OOOooOoooOoo0o0o0oO0O(VAR_NAME, ctx -> evaluate(ctx, original));

            // ★ 成功路径不打日志（用户要求只保留 ERROR 级）。
            // 需要确认是否装上时，加 -Dcnpccombat.ysm.debug=true 看诊断日志。
        } catch (Throwable t) {
            // 这条保留为 ERROR：绑定失败意味着模型侧的 BetterCombat 脚本
            // 对 NPC 完全失效（属于功能损坏，用户需要知道）。
            CnpcCombat.LOGGER.error(
                    "[cnpccombat] could not extend YSM ctrl.{} to NPCs; "
                            + "model-side Better Combat scripts will not apply to NPCs",
                    VAR_NAME, t);
        }
    }

    /**
     * 求值：NPC 用我们自己的动画名，其它情况委托回 YSM 原实现。
     *
     * <p>返回空串表示"当前没有 BC 攻击动画"，与 YSM 的约定一致
     * （模型脚本里会判断非空）。
     */
    private static Object evaluate(oo0oOO0000o0Ooooo0OoOo0O<LivingEntity> ctx, Object original) {
        try {
            LivingEntity entity = ctx.Oo0Oo0o00O00Oo0OOoOOoooo();   // ctx.entity()
            if (entity instanceof NpcAnimationAccess animated) {
                String name = animated.cnpc$getCurrentAttackAnimation();
                return name == null ? "" : name;
            }
            // 不是 NPC（正常情况下就是玩家）→ 交回 YSM 原来的变量对象。
            // ContextVariable.evaluate(IContext) 是 public abstract，
            // 有编译期类型可用，不需要反射。
            if (original instanceof oOO0OOooo00oo0oOOOOoO00O<?> variable) {
                return asLivingVariable(variable).o0OOooo0o0OO00OoOOOo0o0O(ctx);
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 只做一次不安全的泛型转型，单独抽出来便于加注释。
     *
     * <p>YSM 原来那个变量是 {@code ClientPlayerEntityVariable}，泛型实参是
     * {@code AbstractClientPlayer}。我们用 {@code LivingEntity} 的上下文去调它，
     * 泛型上不匹配，但**运行时是安全的**：它的 {@code evaluate} 只会把上下文
     * 交给 YSM 自己的 lambda，而那个 lambda 在取实体前会先过
     * {@code validateContext}（要求是 AbstractClientPlayer）。
     * 也就是说非玩家进去会被它自己挡掉并返回默认值，不会 ClassCastException。
     */
    @SuppressWarnings("unchecked")
    private static oOO0OOooo00oo0oOOOOoO00O<LivingEntity> asLivingVariable(
            oOO0OOooo00oo0oOOOOoO00O<?> variable) {
        return (oOO0OOooo00oo0oOOOOoO00O<LivingEntity>) variable;
    }
}

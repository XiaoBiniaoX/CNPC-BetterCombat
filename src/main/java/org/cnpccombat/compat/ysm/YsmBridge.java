package org.cnpccombat.compat.ysm;

import com.elfmcys.yesstevemodel.o0OooO00ooo0OO000O0OoOoO;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * YSM 隔离层：所有直接引用 YSM 类型的调用都收在这里和它的下游
 * （{@link YsmNpcAnimatable} / {@link YsmNpcRenderer} /
 * {@link YsmNpcAnimationControllers}）。
 *
 * <p>调用方<b>必须</b>先过 {@link YsmCompat#isLoaded()} 才能进这个类的任何方法。
 * 见 {@link YsmCompat} 关于"懒类加载"的说明。
 *
 * <p><b>客户端专用</b>。
 */
@OnlyIn(Dist.CLIENT)
public final class YsmBridge {

    /**
     * 每个 NPC 一份独立的 animatable。
     *
     * <p><b>必须每 NPC 独立</b>：YSM 把"当前模型"和"动画状态"都存在 animatable 实例上。
     * 前几轮共用一个实例导致过两个 bug —— 所有 NPC 动作 1:1 同步、
     * 给 NPC 换模型连玩家也跟着换。
     *
     * <p>用 {@link WeakHashMap} 让 NPC 卸载后自动回收，避免切世界后泄漏。
     * 只在客户端渲染线程访问，不需要同步。
     */
    private static final Map<LivingEntity, YsmNpcAnimatable> ANIMATABLES = new WeakHashMap<>();

    /**
     * 实体 id → 上一次见到的实体**对象身份**（identityHashCode）。
     *
     * <p>用于检测"客户端换掉了实体对象"：玩家死亡时 CNPC 会重建 NPC 实体，
     * 实体 id 不变但对象变了，此时必须重置动画控制器状态
     * （详见 {@code YsmNpcAnimatable.controllersNeedReset} 的注释）。
     * 按实体 id 存储，条目数等于场景里的 NPC 数量，不会泄漏。
     */
    private static final Map<Integer, Integer> LAST_OBJ_ID = new java.util.HashMap<>();



    /** 渲染器只需要一个实例：它不持有单个实体的状态，状态都在 animatable 上。 */
    private static YsmNpcRenderer renderer;

    /**
     * 出错后永久禁用，避免每帧刷异常把日志刷爆、界面卡死
     * （前几轮出现过 latest.log 涨到 9.8MB、连 ESC 都无响应）。
     */
    private static volatile boolean disabled;

    private YsmBridge() {
    }

    /**
     * 列出本地可用的 YSM 模型 id，喂给 GUI 选择列表。
     *
     * <p>读的是 YSM 的 ClientModelManager，也就是<b>玩家自己的 YSM 模型库</b>
     * （{@code config/yes_steve_model/} 下解析出来的那些）——
     * 这满足"可以读 YSM 的模型文件"这一要求。
     */
    public static List<String> availableModels() {
        if (disabled) {
            return Collections.emptyList();
        }
        try {
            // ClientModelManager.getModelAssemblyMap()
            Map<String, ?> models = o0OooO00ooo0OO000O0OoOoO.o0OOooo0o0OO00OoOOOo0o0O();
            if (models == null || models.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> ids = new ArrayList<>(models.keySet());
            Collections.sort(ids);
            return ids;
        } catch (Throwable t) {
            fail("listing models", t);
            return Collections.emptyList();
        }
    }

    /** 指定模型 id 在本地是否存在。 */
    public static boolean hasModel(String modelId) {
        if (disabled || modelId == null || modelId.isBlank()) {
            return false;
        }
        try {
            // ClientModelManager.getModelContext(id).isPresent()
            return o0OooO00ooo0OO000O0OoOoO.Oo0Oo0o00O00Oo0OOoOOoooo(modelId).isPresent();
        } catch (Throwable t) {
            fail("checking model " + modelId, t);
            return false;
        }
    }

    /**
     * 用 YSM 模型渲染这个 NPC。
     *
     * @return true 表示已接管渲染，调用方应取消 CNPC 的原生渲染；
     *         false 表示没接管（模型不存在/未就绪/出错），调用方继续走原生渲染。
     */
    public static boolean render(LivingEntity npc, String modelId, float partialTick,
                                 PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        // ★ 每个 return false 的出口都要有日志。
        // 上一轮教训：日志点全在"渲染成功之后"，玩家死亡后渲染没进来，
        // 日志直接断掉 —— 完全看不出是哪一层挡住的，白费一轮测试。
        if (disabled) {
            YsmDebug.log("gate", "SKIP: compat disabled");
            return false;
        }
        if (npc == null) {
            YsmDebug.log("gate", "SKIP: npc == null");
            return false;
        }
        if (modelId == null || modelId.isBlank()) {
            YsmDebug.log("gate", "SKIP: modelId blank (npc={})", npc.getId());
            return false;
        }
        try {
            // 把 YSM 的 ctrl.bcombat_attack_animation 扩展到 NPC。
            // 必须等 YSM 的 CtrlBinding 单例初始化完才能覆盖，所以放在这里
            // （第一次渲染 NPC 时）而不是 mod 构造期。内部只会真正执行一次。
            YsmBetterCombatBinding.install();
            // 第一次渲染 NPC 时打一条"诊断已生效"的标记。
            // 有了它就能区分"没问题"和"日志没开"——上一轮正是因为
            // JVM 参数没加上，跑完一轮零日志，白费一次测试。
            YsmDebug.markActive();

            // ★★★★★ 诊断「玩家死亡后 NPC 动画永久损坏」（第二十一轮）
            //
            // 日志铁证：玩家死亡的**同一帧**，NPC 上两个值一起翻转：
            //     死前 isKilled=false isAlive=true  health=18
            //     死后 isKilled=true  isAlive=false health=18   ← NPC 还在追人
            // 而 `isKilled() = isRemoved() || entityData.get(IsDead)`
            // 且 vanilla `isAlive() = !isRemoved() && ...`
            // → 两者同时翻转，唯一的共同解释是 **isRemoved() 变成了 true**。
            //
            // 也就是说：玩家死亡时客户端把这个 NPC 实体标记为 removed 了，
            // 但 CNPC 仍在用它做逻辑与渲染（所以还会追人）。
            // 这同时解释了「不可逆、只能大退」：被标记 removed 的对象不会恢复。
            //
            // 这里把对象身份（identityHashCode）与 isRemoved 打出来，
            // 用于区分两种情况：
            //   (a) 同一个对象被标记 removed  → 需要容忍 removed 继续渲染
            //   (b) 客户端换了新对象          → 旧 animatable 是陈旧缓存，应重建
            // ★ 旧的 [ident] 每帧日志已删除（它的使命是定位"陈旧实体对象"，
            // 那个根因已在下面的 swapped 分支修好）。
            // 现在只在**发生对象替换**时记录一条 —— 那是真正值得关注的事件。

            // ★★★★★ 检测「客户端换掉了 NPC 的实体对象」（玩家死亡时必然发生）。
            //
            // ANIMATABLES 按对象为键，所以新对象会拿到**全新**的 animatable，
            // 但它的动画控制器是由模型侧持有的，状态会残留在"正在播 walk"上。
            // 而 setAnimation 对同名同循环类型的请求会短路 → 动画实例永不重建
            // → 骨骼冻在 bind pose（走路/攻击动画全部失效）。
            // 所以这里要通知它：下一次 setAnimation 前先彻底 reset。
            int objId = System.identityHashCode(npc);
            Integer prevId = LAST_OBJ_ID.get(npc.getId());
            boolean swapped = prevId != null && prevId.intValue() != objId;
            LAST_OBJ_ID.put(npc.getId(), objId);

            // ★★★★★ 第三十一轮：这才是真正的根因，前面几十轮全走偏了。
            //
            // YSM 的 AnimatableEntity 把实体引用声明成
            //     protected final TEntity entity;
            // **final，构造时定死**；MovementState 里也持有一份 final 引用。
            //
            // 玩家死亡时客户端会给 Orphie 建一个**新的实体对象**，但
            // ANIMATABLES 是以实体对象为键的 WeakHashMap，旧键仍然存活
            // （日志 cached=true / cacheSize=1）→ computeIfAbsent 复用了
            // **旧 animatable**，而它死抓着**旧实体**。
            //
            // 于是出现那组看似矛盾的日志：
            //   [ident]  读渲染传入的**新**实体 → eTick 正常增长、swinging=true
            //   [gspeed] 读 animatable 里的**旧**实体 → step=0、deltaMovement 冻结
            // 两者根本不是同一个对象。旧实体不再 tick，所以它的
            // getX()/deltaMovement/walkAnimation 全是死数据 →
            // ysm.ground_speed2 恒为 0 → post_main 状态机永远停在待机。
            //
            // 修法：对象被替换时**丢弃旧 animatable**，用新实体重建。
            // 这样实体引用、MovementState、控制器全部是新的，
            // 不需要再去反射补 velocity / limbSwing（那些都是治标）。
            // ★ 注意不能用 ANIMATABLES.remove(npc)：npc 已经是**新**对象，
            // 而缓存里的键是**旧**对象（引用相等），删不掉。
            // 必须按实体 id 找出所有"同 id 但不是当前对象"的条目再删。
            if (swapped) {
                int removed = 0;
                var it = ANIMATABLES.keySet().iterator();
                while (it.hasNext()) {
                    LivingEntity key = it.next();
                    if (key != npc && key.getId() == npc.getId()) {
                        it.remove();
                        removed++;
                    }
                }
                YsmDebug.log("swap",
                        "entity object replaced (npc={} {} -> {}) -> dropped {} stale animatable(s)",
                        npc.getId(), prevId, objId, removed);
            }

            YsmNpcAnimatable animatable = ANIMATABLES.computeIfAbsent(
                    npc, YsmNpcAnimatable::new);
            if (!animatable.bindModel(modelId)) {
                // 模型还在异步加载，或者 id 无效。这一帧退回 CNPC 原生渲染，
                // 下一帧再试 —— 不算错误，不要禁用。
                YsmDebug.log("gate",
                        "SKIP: model not ready (npc={} modelId={} cacheSize={})",
                        npc.getId(), modelId, ANIMATABLES.size());
                return false;
            }
            YsmNpcRenderer r = renderer();
            if (r == null) {
                YsmDebug.log("gate", "SKIP: renderer unavailable (level==null?)");
                return false;
            }
            r.renderNpc(animatable, partialTick, poseStack, bufferSource, light);
            return true;
        } catch (Throwable t) {
            fail("rendering NPC with model " + modelId, t);
            return false;
        }
    }

    /**
     * 懒创建渲染器。需要 {@code EntityRendererProvider.Context}，
     * 而它只能从 {@code EntityRenderDispatcher} 拿到，所以不能在 mod 构造期建。
     */
    private static YsmNpcRenderer renderer() {
        if (renderer != null) {
            return renderer;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        renderer = new YsmNpcRenderer(new EntityRendererProvider.Context(
                mc.getEntityRenderDispatcher(),
                mc.getItemRenderer(),
                mc.getBlockRenderer(),
                mc.getEntityRenderDispatcher().getItemInHandRenderer(),
                mc.getResourceManager(),
                mc.getEntityModels(),
                mc.font));
        return renderer;
    }

    /**
     * 出错处理：只打一次日志然后永久禁用。
     *
     * <p>渲染代码每帧都跑，如果每次都打日志会瞬间把日志刷爆并卡死界面。
     * 日志用 ASCII —— Log4j 用系统默认字符集写文件，中文在 GBK 环境下是乱码。
     */
    private static void fail(String what, Throwable t) {
        disabled = true;
        ANIMATABLES.clear();
        renderer = null;
        org.cnpccombat.CnpcCombat.LOGGER.error(
                "[cnpccombat] YSM compat disabled after failure while {}. "
                        + "NPCs fall back to vanilla CNPC rendering.", what, t);
    }
}

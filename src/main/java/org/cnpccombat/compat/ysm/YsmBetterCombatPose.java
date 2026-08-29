package org.cnpccombat.compat.ysm;

import com.elfmcys.yesstevemodel.OOOO0O0O000O000000oOOO0o;
import com.elfmcys.yesstevemodel.Oo0o00oOOo0OO000000O0oO0;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.cnpccombat.anim.NpcAnimator;

import java.util.List;

/**
 * B 方案：把 BetterCombat 的骨骼动画<b>直接写进 YSM 模型的骨骼</b>。
 *
 * <h2>为什么需要 B 方案</h2>
 * A 方案（按名字播模型自带的 BC 动画）只在模型作者专门做了 BC 动画时有效。
 * 实测全库只有 {@code wine_fox/18_wedding} 有，其余模型（含全部官方模型）
 * 都只有通用的 {@code swing_hand} —— A 线必然 MISS。
 *
 * <h2>为什么这是 YSM 从没做过的事</h2>
 * YSM 所有第三方兼容都是「读一个动画名 → 播自己模型里的同名动画」，
 * <b>从不复用别人的骨骼数据</b>（见 task_plan_ysm.md 第 0 条：
 * 它引用 playerAnimator 的 3 个类全都只取字符串）。
 * 原因是两套骨骼完全独立：
 * <ul>
 *   <li>playerAnimator / BetterCombat 驱动的是 <b>vanilla humanoid 的 ModelPart</b></li>
 *   <li>YSM 渲染的是<b>自己的 geo 骨骼</b>（{@code IBone}）</li>
 * </ul>
 * 所以 BC 的动画对 YSM 模型天然无效 —— 玩家身上也一样。
 *
 * <p>本类做的就是补上这一环：<b>把 BC 算出来的每个部位的旋转，
 * 搬运到 YSM 骨骼上</b>。这样任何 YSM 模型都能做出 BC 的攻击动作，
 * 不需要模型作者适配。
 *
 * <h2>骨骼名映射</h2>
 * BC/playerAnimator 用 vanilla 的部位名（{@code head}/{@code torso}/
 * {@code rightArm}/…），YSM 模型用 Bedrock 风格的骨骼名
 * （{@code Head}/{@code UpBody}/{@code RightArm}/…）。
 * 这里按 YSM 官方模型的命名约定做映射，并对每个部位准备多个候选名，
 * 因为不同作者的命名习惯不完全一致。
 *
 * <h2>★ 覆盖而非叠加（第 N 轮踩的坑）</h2>
 * BC 给出的是「相对模型静止姿态的<b>绝对</b>角度」，不是增量 ——
 * 证据：本 mod 给 CNPC 原生 humanoid 模型施加 BC 动画时，用的是
 * {@code AnimationApplier.updatePart(part, modelPart)}，那是**直接赋值**。
 *
 * <p>我最初用 {@code +=} 叠加到 YSM 已算好的动画姿态上，
 * 结果两份旋转相加 → <b>手臂转到身后去了</b>（用户报"180° 错位"）。
 *
 * <p>所以对 BC 实际驱动的部位必须**覆盖**。代价是这些部位在攻击期间
 * 不再受 YSM 动画影响（手臂不会同时做走路摆动），
 * 这与 BC 在 vanilla 模型上的行为一致 —— BC 攻击时手臂本来就由它独占。
 * 未被 BC 驱动的部位（腿、尾巴、耳朵等）完全不碰，走路动画照常。
 *
 * <h2>★ 必须校验动画是否真的在播</h2>
 * {@code AnimationApplier.isActive()} 在动画播完后可能仍为 true
 * （ModifierLayer 里还挂着已结束的 player），此时 {@code get3DTransform}
 * 返回的是**最后一帧的残留值**。若无条件应用，NPC 会顶着攻击结束时的
 * 姿态不动，或在多个残留值之间乱摆（用户报"双手双脚无规律乱摆、
 * 头缓慢转圈"）。
 * → 用本 mod 自己的 {@code cnpc$isAttackAnimationActive()} 把门，
 *   它基于 {@code attackVisualTicks} 倒计时，动画结束就是 false。
 *
 * <p><b>客户端专用</b>，引用 YSM 类型，只能在 YSM 确实安装时触碰。
 */
@OnlyIn(Dist.CLIENT)
final class YsmBetterCombatPose {

    /**
     * BC 部位名 → YSM 骨骼候选名。
     *
     * <p>顺序即优先级：命中第一个存在的骨骼就用它。
     * 候选名取自 YSM 官方模型（default / wine_fox 系列）的实际骨骼命名。
     */
    private static final String[][] BONE_MAP = {
            // BC 的 "torso" 对应 YSM 的上半身。UpBody 是官方模型的标准名，
            // Body/Chest 是其它作者常见的写法。
            {"torso", "UpBody", "Body", "Chest", "body"},
            {"head", "Head", "head"},
            {"rightArm", "RightArm", "rightArm", "arm_right"},
            {"leftArm", "LeftArm", "leftArm", "arm_left"},
            {"rightLeg", "RightLeg", "rightLeg", "leg_right"},
            {"leftLeg", "LeftLeg", "leftLeg", "leg_left"},
    };

    /**
     * vanilla {@code HumanoidModel} 各部位的初始 pivot（像素），
     * 取自 {@code HumanoidModel.createMesh} 里的 {@code PartPose.offset(...)}。
     *
     * <p>用途：BC 给出的位移是**相对 vanilla ModelPart 初始 pivot 的绝对坐标**，
     * 减掉这个基准才得到"偏移量"，才能安全地写进 YSM 骨骼
     * （两套骨骼的 pivot 原点不同，直接照搬绝对坐标会让肢体飞出模型）。
     *
     * <p>数值来自字节码实测：
     * head/torso = (0,0,0)、rightArm = (-5,2,0)、leftArm = (5,2,0)、
     * rightLeg = (-1.9,12,0)、leftLeg = (1.9,12,0)。
     */
    private static final java.util.Map<String, float[]> VANILLA_PIVOT =
            java.util.Map.of(
                    "head", new float[]{0.0F, 0.0F, 0.0F},
                    "torso", new float[]{0.0F, 0.0F, 0.0F},
                    "rightArm", new float[]{-5.0F, 2.0F, 0.0F},
                    "leftArm", new float[]{5.0F, 2.0F, 0.0F},
                    "rightLeg", new float[]{-1.9F, 12.0F, 0.0F},
                    "leftLeg", new float[]{1.9F, 12.0F, 0.0F});

    private YsmBetterCombatPose() {
    }

    /**
     * 把 BC 当前帧的骨骼旋转叠加到 YSM 模型骨骼上。
     *
     * <p>必须在 YSM 自己的动画算完之后调用（即 {@code processAnimation} 之后、
     * 真正提交顶点之前），否则会被 YSM 的动画覆盖掉。
     *
     * @return true 表示确实施加了 BC 动画（用于日志判断 B 线是否生效）
     */
    static boolean apply(LivingEntity entity, OOOO0O0O000O000000oOOO0o model, float partialTick) {
        if (entity == null || model == null) {
            return false;
        }
        // ★ 先确认攻击动画**真的在播**。
        // AnimationApplier.isActive() 在动画播完后可能仍为 true（层里还挂着
        // 已结束的 player），此时 get3DTransform 返回最后一帧的残留值 ——
        // 无条件应用会让 NPC 顶着残留姿态乱摆（用户报"手脚无规律乱摆、头转圈"）。
        // cnpc$isAttackAnimationActive() 基于 attackVisualTicks 倒计时，动画结束即 false。
        if (!(entity instanceof org.cnpccombat.api.NpcAnimationAccess access)
                || !access.cnpc$isAttackAnimationActive()) {
            return false;
        }

        // 取本 mod 驱动 NPC 的 BC 动画栈（ClientNpcAnimationMixin 里那个 applier）。
        AnimationApplier applier = NpcAnimator.getAnimation(entity);
        if (applier == null) {
            return false;
        }
        // setTickDelta 决定插值位置，不设会导致动作卡在整 tick 上、看起来一顿一顿。
        applier.setTickDelta(partialTick);
        if (!applier.isActive()) {
            return false;
        }

        boolean applied = false;
        for (String[] entry : BONE_MAP) {
            String bcPart = entry[0];
            Oo0o00oOOo0OO000000O0oO0 bone = findBone(model, entry);
            if (bone == null) {
                // 骨骼名没命中 —— 这个部位的 BC 动画就丢了。
                // 日志会列出候选名，方便对照模型实际骨骼补映射表。
                YsmDebug.log("pose-miss:" + bcPart,
                        "no bone for BC part {} (candidates: {})",
                        bcPart, java.util.Arrays.toString(
                                java.util.Arrays.copyOfRange(entry, 1, entry.length)));
                continue;
            }

            // ★★ fallback 必须传骨骼的**当前值**，不能传 ZERO。
            //
            // 看 playerAnimator 的 AnimationApplier.updatePart 是怎么做的：
            //   get3DTransform(part, ROTATION, new Vec3f(part.xRot, part.yRot, part.zRot))
            // 它把 ModelPart 的当前值当 fallback 传进去 —— 因为一个动画
            // **只驱动部分轴**，没被驱动的轴要保留原值。
            //
            // 我之前传 Vec3f.ZERO，于是 BC 没驱动的轴被**清零**：
            // 模型静止姿态里手臂本来有基础角度（比如自然下垂），清零后就摊平，
            // 再叠上 BC 的挥击角度 → 手臂朝向完全错乱、像往背后打。
            //
            // 传当前值后，未驱动的轴保持 YSM 算好的姿态，只有 BC 真正
            // 驱动的轴被替换 —— 与 BC 在 vanilla 模型上的行为完全一致。
            //
            // BC 的旋转是弧度；YSM 的 IBone 也是弧度（证据：YSM 自己写 IBone
            // 时用 Math.toRadians()，见 LivingAnimatable.applyHeadTracking），
            // 单位一致不需换算。
            Vec3f fallback = new Vec3f(
                    bone.Oo0Oo0o00O00Oo0OOoOOoooo(),    // getRotationX
                    bone.o0OOooo0o0OO00OoOOOo0o0O(),    // getRotationY
                    bone.O00OOOooOoooOoo0o0o0oO0O());   // getRotationZ
            Vec3f rotation = applier.get3DTransform(bcPart, TransformType.ROTATION, fallback);
            if (rotation == null) {
                continue;
            }
            // ★★ BC 动画同时驱动**旋转和位移**（实测
            // two_handed_slash_horizontal_right 的 rightArm 有 6 个通道：
            // pitch/yaw/roll + x/y/z，且位移量级很大：x=-5.6、y=2.5、z=-1.5）。
            //
            // 只搬旋转会缺位移补偿 —— 那个 roll=2.44rad(140°) 本来是配合位移
            // 才形成正确挥击姿势的，单独应用就把手臂转到背后去了。
            // 这正是"往背后打"的直接原因。
            //
            // 但**位移不能直接照搬**：BC 的位移是相对 vanilla humanoid
            // ModelPart 初始 pivot 的像素偏移（rightArm pivot = (-5,2,0)），
            // 而 YSM 模型的骨骼 pivot 由模型作者自定，两套坐标原点不同。
            // 直接赋值会让手臂飞到模型外面。
            //
            // 折中：位移按**相对量**叠加到骨骼当前位置上。
            // fallback 传骨骼当前位移，于是 BC 未驱动的轴保持不变；
            // 被驱动的轴得到 BC 的绝对值，再减去 vanilla 的初始 pivot
            // 换算成"偏移量"后叠加。
            Vec3f posFallback = new Vec3f(
                    bone.oOOOo0OOO0ooooo0O00OO0o0(),    // getPositionX
                    bone.OOOOo0O0oO0OOo0O0O0Oo0O0(),    // getPositionY
                    bone.Ooooo0oooO0oooOOOoO0000O());   // getPositionZ
            Vec3f position = applier.get3DTransform(bcPart, TransformType.POSITION, posFallback);
            // ★ 覆盖（=）而不是叠加（+=）：BC 给的是**绝对角度**。
            // 叠加会与 YSM 已算好的动画姿态相加 -> 手臂转到身后（180° 错位）。
            // 轴序也一致：vanilla ModelPart 用 rotationZYX(zRot,yRot,xRot)，
            // YSM RenderUtils 用 rotateZYX(Z,Y,X)，符号相同，可直接搬。
            //
            // 混淆名对照（按 OpenYsm 的 IBone 声明顺序逐一对位得出）：
            //   Oo0Oo0o00O00Oo0OOoOOoooo(float) = setRotationX
            //   o0OOooo0o0OO00OoOOOo0o0O(float) = setRotationY
            //   O00OOOooOoooOoo0o0o0oO0O(float) = setRotationZ
            // 把"BC 给的值"与"骨骼原值"一起打出来。
            // 这是定位手臂错位的关键：能直接看出 BC 的量级是否合理、
            // 以及我们写进去的值与模型原姿态差多少。
            YsmDebug.log("pose:" + bcPart,
                    "bone={} rotIn=({},{},{}) rotBC=({},{},{}) posIn=({},{},{}) posBC=({},{},{})",
                    bone.oOOo0Ooo0oOoo0O0OOOOo0oo(),
                    fmt(fallback.getX()), fmt(fallback.getY()), fmt(fallback.getZ()),
                    fmt(rotation.getX()), fmt(rotation.getY()), fmt(rotation.getZ()),
                    fmt(posFallback.getX()), fmt(posFallback.getY()), fmt(posFallback.getZ()),
                    position == null ? "n/a" : fmt(position.getX()),
                    position == null ? "n/a" : fmt(position.getY()),
                    position == null ? "n/a" : fmt(position.getZ()));

            bone.Oo0Oo0o00O00Oo0OOoOOoooo(rotation.getX());
            bone.o0OOooo0o0OO00OoOOOo0o0O(rotation.getY());
            bone.O00OOOooOoooOoo0o0o0oO0O(rotation.getZ());

            // ★★★ 位移**不搬**（第十三轮日志实测后的决定）。
            //
            // 一度以为"只搬旋转导致手臂错位"，于是尝试把位移也搬过来，
            // 但日志显示这条路走不通：
            //   rightLeg posBC=(-2.067, 7.658, -0.172)   vanilla pivot=(-1.9, 12, 0)
            //   rightArm posBC=(-2.894, 1.777, -1.093)   vanilla pivot=(-5, 2, 0)
            // 减去 pivot 得到 (-0.167, -4.342, ...) / (2.106, -0.223, ...) ——
            // 会把腿上提 4 格、手臂右移 2 格，明显是错的。
            //
            // 根本原因：BC 的位移是按 **vanilla 骨架的比例**算的
            // （手臂/腿都是 12px、肩宽 10px），语义是"把这个部位挪到某个绝对坐标"。
            // 而 YSM 模型的骨架比例、pivot 位置、层级结构都由模型作者自定，
            // 同一个绝对坐标在两套骨架里对应的部位位置完全不同。
            // 换算需要知道两套骨架的对应关系，这不是一个缩放系数能解决的。
            //
            // 而实测**旋转本身是合理的**（量级 0.1~1.0 rad，与模型原姿态同级），
            // 只搬旋转已经能表达 BC 的挥击动作 —— 位移主要是细节修饰
            // （身体前倾时手臂跟着平移一点），缺了不影响动作辨识度，
            // 远好过把肢体挪到错误位置。
            //
            // 保留 position 的读取和日志，便于将来若要做骨架比例映射时参考。
            applied = true;
        }
        return applied;
    }

    /** 按候选名顺序找骨骼，命中第一个存在的。 */
    private static Oo0o00oOOo0OO000000O0oO0 findBone(OOOO0O0O000O000000oOOO0o model,
                                                     String[] entry) {
        // entry[0] 是 BC 的部位名，从 1 开始才是 YSM 骨骼候选名。
        for (int i = 1; i < entry.length; i++) {
            Oo0o00oOOo0OO000000O0oO0 bone = boneByName(model, entry[i]);
            if (bone != null) {
                return bone;
            }
        }
        return null;
    }

    /**
     * 按名字在模型的骨骼表里查找。
     *
     * <p>{@code model.O00OOOooOoooOoo0o0o0oO0O()} 是
     * {@code Int2ReferenceMap<IBone>}（bone id -> bone），
     * 没有按名字索引的入口，所以遍历。骨骼数量在两百上下，
     * 每帧最多查 6 次，开销可以忽略；而且只在攻击动画激活时才会走到这里。
     */
    private static Oo0o00oOOo0OO000000O0oO0 boneByName(OOOO0O0O000O000000oOOO0o model,
                                                       String name) {
        try {
            var bones = model.O00OOOooOoooOoo0o0o0oO0O();   // getBoneMap()
            if (bones == null) {
                return null;
            }
            for (Oo0o00oOOo0OO000000O0oO0 bone : bones.values()) {
                // oOOo0Ooo0oOoo0O0OOOOo0oo() = getName()
                if (name.equals(bone.oOOo0Ooo0oOoo0O0OOOOo0oo())) {
                    return bone;
                }
            }
        } catch (Throwable ignored) {
            // 骨骼表结构与预期不符 —— 放弃这个骨骼，不影响渲染。
        }
        return null;
    }

    /** 日志用：保留 3 位小数，避免刷出一长串浮点噪声。 */
    private static String fmt(Number n) {
        if (n == null) {
            return "n/a";
        }
        return String.format(java.util.Locale.ROOT, "%.3f", n.floatValue());
    }

    private static boolean isZero(Vec3f v) {
        return v == null
                || (Math.abs(v.getX()) < 1.0E-5F
                && Math.abs(v.getY()) < 1.0E-5F
                && Math.abs(v.getZ()) < 1.0E-5F);
    }

    /**
     * 诊断用：打出几根关键骨骼在 {@code renderEarly} 时刻的实际旋转。
     *
     * <p>用途：区分"YSM 没求值动画"与"求值了但姿势不对"。
     * 若这些值在走路时**恒定不变**，说明动画管线根本没跑到骨骼，
     * 问题不在我们的 predicate 判定。
     */
    static void dumpBones(OOOO0O0O000O000000oOOO0o model, LivingEntity entity) {
        if (!YsmDebug.enabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"RightArm", "LeftArm", "RightLeg", "LeftLeg", "UpBody"}) {
            Oo0o00oOOo0OO000000O0oO0 bone = boneByName(model, name);
            if (bone == null) {
                continue;
            }
            sb.append(name).append("=(")
                    .append(fmt(bone.Oo0Oo0o00O00Oo0OOoOOoooo())).append(',')
                    .append(fmt(bone.o0OOooo0o0OO00OoOOOo0o0O())).append(',')
                    .append(fmt(bone.O00OOOooOoooOoo0o0o0oO0O())).append(") ");
        }
        YsmDebug.log("bones", "swinging={} {}", entity.swinging, sb.toString().trim());
    }

    /** 供日志用：列出模型里实际存在的骨骼名（前若干个）。 */
    static List<String> sampleBoneNames(OOOO0O0O000O000000oOOO0o model, int limit) {
        List<String> out = new java.util.ArrayList<>();
        try {
            var bones = model.O00OOOooOoooOoo0o0o0oO0O();
            if (bones == null) {
                return out;
            }
            for (Oo0o00oOOo0OO000000O0oO0 bone : bones.values()) {
                out.add(bone.oOOo0Ooo0oOoo0O0OOOOo0oo());
                if (out.size() >= limit) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // 仅诊断用，失败无所谓
        }
        return out;
    }
}

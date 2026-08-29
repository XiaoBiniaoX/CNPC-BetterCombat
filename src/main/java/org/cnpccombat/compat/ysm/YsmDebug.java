package org.cnpccombat.compat.ysm;

import org.cnpccombat.CnpcCombat;

/**
 * YSM 兼容的诊断日志开关。
 *
 * <h2>为什么需要它</h2>
 * 前几轮反复出现"改一处、猜一处、下一轮又不对"的情况，根因是缺少
 * <b>运行时的真实状态观测</b>。
 *
 * <h2>为什么默认开启</h2>
 * 第一版用 JVM 系统属性 {@code -Dcnpccombat.ysm.debug=true} 控制，结果用户
 * 那边参数没加上，跑完一轮**一条日志都没有**，白费一次测试。
 * 教训：<b>诊断开关不能依赖使用者改启动器配置</b> ——
 * 那是额外一步，出错了还很难发现（表现为"没日志"，与"没问题"无法区分）。
 *
 * <p>所以改成默认开启，靠三层保护避免刷爆日志
 * （本项目第 6 轮出现过日志涨到 9.8MB、连 ESC 都无响应）：
 * <ol>
 *   <li><b>按 key 限流</b>：同一类每 {@value #THROTTLE_MS} 毫秒最多一条；</li>
 *   <li><b>总量上限</b>：累计 {@value #MAX_LINES} 条后自动永久停止；</li>
 *   <li>仍可用 {@code -Dcnpccombat.ysm.debug=false} 显式关掉。</li>
 * </ol>
 * 按 60fps 估算，限流后每类每秒 1 条，几百条足够覆盖一次复现，
 * 且总量封顶后不会持续增长。
 *
 * <h2>日志一律 ASCII</h2>
 * Log4j 用系统默认字符集写 latest.log（中文 Windows = GBK），
 * 源码里 UTF-8 的中文日志到文件里会变乱码，排查时反而看不懂。
 * 所以这里所有日志内容都用 ASCII。
 */
final class YsmDebug {

    /** 同一个 key 的最小打印间隔（毫秒），避免每帧刷屏。 */
    private static final long THROTTLE_MS = 1000L;

    /** 累计打印上限，到达后永久停止，防止长时间挂机把日志撑大。 */
    private static final int MAX_LINES = 4000;

    /**
     * 是否启用诊断日志。
     *
     * <h2>★ 第三十三轮改为默认 <b>关闭</b>（用户要求"只保留 ERROR 级日志"）</h2>
     * YSM 兼容已经全部跑通，那些逐帧／状态变化的 INFO 日志使命结束，
     * 留着只会污染 latest.log。
     *
     * <p>但**排查能力必须可恢复**：需要时加
     * {@code -Dcnpccombat.ysm.debug=true} 即可让全部诊断日志回来
     * （日志点本身都还在代码里，只是默认不输出）。
     *
     * <p>注意历史教训：默认开启那一版是因为"用户没加启动参数 → 跑完一轮零日志
     * → 白费一次测试"。现在默认关闭是**用户明确要求**，
     * 且功能已验证通过，不再需要每次都观测。
     * 真正的故障（异常、YSM 被禁用）走 {@link #error}，不受这个开关影响。
     */
    private static final boolean ENABLED = "true".equalsIgnoreCase(
            System.getProperty("cnpccombat.ysm.debug", "false"));

    /** 已打印行数，用于总量封顶。 */
    private static int lines;

    /** key -> 上次打印时间。只在渲染线程访问，不需要同步。 */
    private static final java.util.Map<String, Long> LAST_LOG = new java.util.HashMap<>();

    private YsmDebug() {
    }

    static boolean enabled() {
        return ENABLED && lines < MAX_LINES;
    }

    /** 是否已打过"诊断生效"标记。 */
    private static boolean marked;

    /**
     * 给<b>不能引用 YSM 类型</b>的调用方（如通用 client mixin）用的日志入口。
     *
     * <p>本类自身不引用任何 YSM 类型，所以可以被 mixin 安全调用；
     * 但为了让边界清晰，这里单独开一个方法，并在 {@code YsmDiag} 里做转发。
     */
    static void logExternal(String key, String format, Object... args) {
        log(key, format, args);
    }

    /**
     * 打一条"诊断日志已生效"的标记，只打一次。
     *
     * <p>作用是让"没有问题"和"日志没开"可区分 —— 少了这条标记，
     * 零日志既可能是一切正常，也可能是开关没生效（上一轮就栽在这）。
     */
    static void markActive() {
        if (!enabled() || marked) {
            return;
        }
        marked = true;
        emit("[cnpccombat/ysm] debug logging ACTIVE"
                + " (disable with -Dcnpccombat.ysm.debug=false)");
    }

    /**
     * 限流打印。
     *
     * @param key    限流分组。相同 key 共享一个时间窗口。
     * @param format SLF4J 风格的格式串（{@code {}} 占位），<b>必须是 ASCII</b>
     * @param args   参数
     */
    static void log(String key, String format, Object... args) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(key);
        if (last != null && now - last < THROTTLE_MS) {
            return;
        }
        LAST_LOG.put(key, now);
        emit("[cnpccombat/ysm][" + key + "] " + format, args);
    }

    /** 不限流打印（用于"只发生一次"的事件，如控制器装配结果）。 */
    static void once(String format, Object... args) {
        if (!enabled()) {
            return;
        }
        emit("[cnpccombat/ysm] " + format, args);
    }

    /**
     * 真正的故障日志：<b>ERROR 级，不受 {@link #ENABLED} 开关影响</b>。
     *
     * <p>只用于"功能已经坏了、用户需要知道"的情况：
     * 渲染抛异常、YSM 兼容被永久禁用、反射目标找不到等。
     * 不要用它打状态观测 —— 那属于诊断日志（{@link #log}）。
     *
     * <p>ERROR 级同样受总量封顶保护，避免每帧抛异常时把日志刷爆
     * （本项目第 6 轮出现过 latest.log 涨到 9.8MB、连 ESC 都无响应）。
     */
    static void error(String format, Object... args) {
        if (lines >= MAX_LINES) {
            return;
        }
        lines++;
        CnpcCombat.LOGGER.error("[cnpccombat/ysm] " + format, args);
    }

    /** 统一出口，负责总量封顶。 */
    private static void emit(String format, Object... args) {
        if (lines >= MAX_LINES) {
            return;
        }
        lines++;
        if (lines == MAX_LINES) {
            CnpcCombat.LOGGER.info(
                    "[cnpccombat/ysm] debug log limit reached ({} lines), stopping.",
                    MAX_LINES);
            return;
        }
        CnpcCombat.LOGGER.info(format, args);
    }
}

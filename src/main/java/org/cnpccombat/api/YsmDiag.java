package org.cnpccombat.api;

import org.cnpccombat.CnpcCombat;

/**
 * YSM 相关的诊断日志入口，供<b>不能引用 YSM 类型</b>的调用方使用。
 *
 * <h2>为什么需要单独一个类</h2>
 * 真正的实现 {@code org.cnpccombat.compat.ysm.YsmDebug} 位于隔离包里，
 * 而通用的客户端 mixin（如给 vanilla {@code LivingEntityRenderer} 打的补丁、
 * 给 {@code RenderNPCInterface} 打的补丁）在<b>未装 YSM 时也会加载</b>，
 * 不能碰 {@code compat.ysm} 包下的任何东西（哪怕那个类本身不引用 YSM 类型，
 * 也不该让边界变模糊）。
 *
 * <p>所以这里提供一个零依赖的日志出口，逻辑与 {@code YsmDebug} 保持一致：
 * 默认开启、按 key 限流、总量封顶。
 *
 * <h2>日志一律 ASCII</h2>
 * Log4j 用系统默认字符集写 latest.log（中文 Windows = GBK），
 * UTF-8 的中文到文件里会变乱码。
 */
public final class YsmDiag {

    /** 同一个 key 的最小打印间隔（毫秒）。 */
    private static final long THROTTLE_MS = 1000L;

    /** 累计打印上限，到达后永久停止。 */
    private static final int MAX_LINES = 4000;

    /**
     * 默认<b>关闭</b>（用户要求只保留 ERROR 级日志）。
     * 需要排查时加 {@code -Dcnpccombat.ysm.debug=true}，与
     * {@code YsmDebug} 用的是同一个开关。
     */
    private static final boolean ENABLED = "true".equalsIgnoreCase(
            System.getProperty("cnpccombat.ysm.debug", "false"));

    private static final java.util.Map<String, Long> LAST_LOG = new java.util.HashMap<>();

    private static int lines;

    private YsmDiag() {
    }

    public static boolean enabled() {
        return ENABLED && lines < MAX_LINES;
    }

    /**
     * 限流打印。
     *
     * @param key    限流分组，相同 key 共享时间窗口
     * @param format SLF4J 风格格式串（{@code {}} 占位），<b>必须是 ASCII</b>
     */
    public static void log(String key, String format, Object... args) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(key);
        if (last != null && now - last < THROTTLE_MS) {
            return;
        }
        LAST_LOG.put(key, now);
        if (lines >= MAX_LINES) {
            return;
        }
        lines++;
        CnpcCombat.LOGGER.info("[cnpccombat/ysm][" + key + "] " + format, args);
    }
}

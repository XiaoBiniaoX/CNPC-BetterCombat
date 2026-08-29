package org.cnpccombat.compat.ysm;

import net.minecraftforge.fml.ModList;

/**
 * YSM（是，史蒂夫模型）可选依赖的**唯一入口闸门**。
 *
 * <p>这个类是全项目里唯一允许在"未确认 YSM 是否安装"的情况下被触碰的 YSM 相关类。
 * 它自己<b>不引用任何 YSM 类型</b>，所以加载它永远安全。
 *
 * <h2>为什么可选依赖能成立</h2>
 * Java 的类加载是<b>懒</b>的：一个方法体里引用的类型，只有真正执行到那条指令时才会被解析。
 * 因此只要满足两条，未装 YSM 就绝不会 {@code NoClassDefFoundError}：
 * <ol>
 *   <li>所有<b>直接</b>引用 YSM 类型的代码都放在 {@link YsmBridge} 及其下游
 *       （即"隔离类"），这些类在未装 YSM 时永远不被加载；</li>
 *   <li>进入隔离类之前，一律先过 {@link #isLoaded()}。</li>
 * </ol>
 *
 * <p><b>注意</b>：判定不能用 {@code Class.forName} 去探 YSM 的混淆类名 ——
 * 混淆名会随 YSM 版本变化，探测失败会被误判成"没装"。{@code ModList} 查 modid
 * 是稳定的。
 *
 * <p>日志字符串一律 ASCII：Log4j 用系统默认字符集写 latest.log，
 * 中文在 GBK 环境下会变乱码，反而看不懂。
 */
public final class YsmCompat {
    /** YSM 的 modid。2.x / 3.x 都是这个值。 */
    public static final String MODID = "yes_steve_model";

    /**
     * 三态缓存：null = 还没查过，TRUE/FALSE = 已确定。
     *
     * <p>不用 {@code boolean + boolean checked} 两个字段，避免多线程下读到
     * "checked=true 但值还没写入"的中间态。单个引用的写入是原子的。
     *
     * <p>不在 static 初始化块里查：{@code ModList.get()} 在 mod 构造期之前会抛
     * {@code NullPointerException}，而这个类可能被很早触碰。
     */
    private static volatile Boolean loaded;

    private YsmCompat() {
    }

    /**
     * YSM 是否已安装。
     *
     * <p>结果会缓存 —— 但只在拿到确定答案后才缓存。{@code ModList.get()} 在
     * mod 加载早期可能还不可用（抛异常），那种情况下返回 false 但<b>不</b>缓存，
     * 留到下次真正需要时再查。
     */
    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached != null) {
            return cached;
        }
        try {
            boolean present = ModList.get().isLoaded(MODID);
            loaded = present;
            return present;
        } catch (Throwable ignored) {
            // ModList 尚未就绪（早于 mod 构造期）。不缓存，下次再问。
            return false;
        }
    }
}

package org.cnpccombat.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.List;

/**
 * YSM 功能的**对外门面**：调用方只用这个类，不需要自己判 YSM 装没装。
 *
 * <p>作用是把 {@link YsmCompat#isLoaded()} 的判断和"进入会触碰 YSM 类型的代码"
 * 这两件事分开：
 * <ul>
 *   <li>本类<b>不</b>引用任何 YSM 类型，所以随时可以安全加载；</li>
 *   <li>{@link YsmBridge}（以及它下游的 animatable/renderer）引用 YSM 类型，
 *       只在 isLoaded 为真的分支里被提及 —— JVM 的类加载是懒的，
 *       走不到那条指令就不会去解析那些类，因此未装 YSM 不会 NoClassDefFoundError。</li>
 * </ul>
 *
 * <p>注意：<b>不能</b>把 YsmBridge 的调用和 isLoaded 判断写在同一个方法的不同分支里
 * 而指望"用不到就不加载"—— 那是成立的（解析发生在指令执行时），
 * 但把门面单独抽出来能让边界一目了然，避免以后有人不小心在 isLoaded 外面加了调用。
 *
 * <p><b>客户端专用</b>：YSM 模型是纯渲染功能。
 */
@OnlyIn(Dist.CLIENT)
public final class YsmFacade {

    private YsmFacade() {
    }

    /** YSM 是否可用（装了 YSM 才显示相关 GUI）。 */
    public static boolean isAvailable() {
        return YsmCompat.isLoaded();
    }

    /** 本地可用的 YSM 模型 id 列表；未装 YSM 返回空列表。 */
    public static List<String> availableModels() {
        if (!YsmCompat.isLoaded()) {
            return Collections.emptyList();
        }
        return YsmBridge.availableModels();
    }

    /**
     * 尝试用 YSM 模型渲染 NPC。
     *
     * @return true = 已接管，调用方应取消原生渲染；false = 未接管，继续原生渲染
     */
    public static boolean render(LivingEntity npc, String modelId, float partialTick,
                                 PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        if (!YsmCompat.isLoaded()) {
            return false;
        }
        return YsmBridge.render(npc, modelId, partialTick, poseStack, bufferSource, light);
    }
}

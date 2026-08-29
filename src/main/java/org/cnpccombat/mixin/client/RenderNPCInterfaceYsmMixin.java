package org.cnpccombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.cnpccombat.api.NpcYsmModelData;
import org.cnpccombat.api.YsmDiag;
import org.cnpccombat.compat.ysm.YsmFacade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 当 NPC 设置了 YSM 模型时，用 YSM 模型代替 CNPC 的原生模型渲染。
 *
 * <h2>注入点</h2>
 * 注在 {@code render} 里对 {@code LivingEntityRenderer.render} 的那次调用上。
 * 那一句是"画模型"的地方，此前的代码已经算好了尺寸/朝向/阴影并把变换压进了 PoseStack，
 * 所以在这里接管能自动继承 CNPC 的缩放与朝向设置。
 *
 * <h2>为什么必须手工补 refmap</h2>
 * {@code RenderNPCInterface.render} 是 vanilla {@code LivingEntityRenderer.render} 的
 * override，运行时是 SRG 名 {@code m_7392_}。而 MixinGradle 的 annotation processor
 * <b>只自动映射 Minecraft 类</b>，对 CNPC 类的方法名无能为力（只会打一条不起眼的
 * "Unable to determine descriptor" 提示，不会让构建失败）。
 * 所以 build.gradle 的 {@code npcClassMappings} 里必须有这个 mixin 的 {@code render} 条目。
 * 这个坑本项目已经踩过两次，见 findings.md。
 *
 * <p>注意 {@code @At} 的 target 是 <b>Minecraft</b> 类的方法，那个 AP 能自动处理，
 * 不需要手工映射。
 *
 * <h2>可选依赖</h2>
 * 这个类<b>不直接引用任何 YSM 类型</b> —— 真正碰 YSM 的调用隔离在
 * {@link YsmFacade} 之后，只有 YSM 确实安装时才会进去。
 * 未装 YSM 时这个 mixin 照常加载，只是永远走不到接管分支。
 */
@Mixin(RenderNPCInterface.class)
@OnlyIn(Dist.CLIENT)
public abstract class RenderNPCInterfaceYsmMixin {

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;"
                            + "render(Lnet/minecraft/world/entity/LivingEntity;FF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            ),
            cancellable = true
    )
    private void cnpc$renderYsmModel(
            EntityNPCInterface npc, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int light,
            CallbackInfo ci) {
        // ★ 每个提前返回的出口都打日志。这是渲染链的**最外层闸门**，
        // 上一轮玩家死亡后日志直接断掉，就是因为这里静默返回、看不出原因。
        if (npc == null) {
            return;
        }
        if (!YsmFacade.isAvailable()) {
            YsmDiag.log("entry", "SKIP: YSM not available");
            return;
        }
        // 模型名存在 DataDisplay 上（模型/外观界面保存的是 DISPLAY 菜单）。
        if (!(npc.display instanceof NpcYsmModelData data)) {
            YsmDiag.log("entry", "SKIP: display is not NpcYsmModelData (npc={})", npc.getId());
            return;
        }
        String modelId = data.cnpc$getYsmModel();
        if (modelId == null || modelId.isBlank()) {
            YsmDiag.log("entry", "SKIP: no model selected (npc={})", npc.getId());
            return;
        }
        // 证明"渲染入口进来了"——玩家死亡后若这条也消失，说明整个 render
        // 方法没被调用（问题在 CNPC/vanilla 层，不在我们这里）。
        YsmDiag.log("entry", "ENTER npc={} modelId={} deathTime={} partialTick={}",
                npc.getId(), modelId, npc.deathTime, partialTick);

        if (!YsmFacade.render(npc, modelId, partialTick, poseStack, bufferSource, light)) {
            // 没接管（模型还在加载/id 无效/YSM 出错）→ 什么都不做，
            // 让 CNPC 继续画它自己的模型。
            return;
        }

        // 接管成功。CNPC 在调用 super.render 之前把自己设成了 currentNpc，
        // 并指望调用结束后把它清掉；我们取消了那一段，所以必须自己清，
        // 否则这个静态字段会一直指着上一个渲染过的 NPC，影响 CNPC 其它逻辑。
        RenderNPCInterface.currentNpc = null;

        // 名牌/标题/血条：CNPC 的名牌是在 vanilla 渲染管线里由 renderNameTag 画的，
        // 被我们一起取消了，所以在这里补一次。这个方法自带
        // "该不该显示/距离够不够"的判断，直接调即可。
        ((RenderNPCInterface<EntityNPCInterface, ?>) (Object) this)
                .renderNameTag(npc, npc.getDisplayName(), poseStack, bufferSource, light);

        ci.cancel();
    }
}

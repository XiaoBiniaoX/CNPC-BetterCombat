package org.cnpccombat.anim;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * <b>客户端专用</b>：签名里有 {@code ModelPart}（{@code net.minecraft.client.*}），
 * 服务端没有这个类。由 {@code HumanoidModelMixin} 实现（在 mixins.json 的
 * {@code "client"} 段），仅供客户端渲染路径使用。
 */
@OnlyIn(Dist.CLIENT)
public interface HumanoidModelAccess {
    ModelPart cnpc$getHead();

    ModelPart cnpc$getHat();

    ModelPart cnpc$getBody();

    ModelPart cnpc$getLeftArm();

    ModelPart cnpc$getRightArm();

    ModelPart cnpc$getLeftLeg();

    ModelPart cnpc$getRightLeg();

    HumanoidBodyPose cnpc$getInitialBodyPose();
}

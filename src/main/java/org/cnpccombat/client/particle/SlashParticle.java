package org.cnpccombat.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.cnpccombat.particle.SlashParticleEffect;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 刀光粒子（移植自 BC 1.21.1 的 SlashParticle，Yarn→官方映射）。
 *
 * <p>与原版 Billboard 粒子的区别：四角在 XZ 平面（地面朝向的“扇形刀光”），
 * 按 yaw/pitch/roll/localYaw 旋转，双面渲染，寿命 6 tick，按年龄切帧。
 */
@OnlyIn(Dist.CLIENT)
public class SlashParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float pitch;
    private final float yaw;
    private final float localYaw;
    private final float roll;
    private final boolean light;

    public SlashParticle(ClientLevel level, double x, double y, double z,
                         SlashParticleEffect effect, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.light = effect.getLight();
        this.pitch = effect.getPitch();
        this.yaw = effect.getYaw();
        this.roll = effect.getRoll();
        this.localYaw = effect.getLocalYaw();

        long rgba = effect.getColorRGBA();
        setColor(((rgba >> 24) & 0xFF) / 255.0F,
                ((rgba >> 16) & 0xFF) / 255.0F,
                ((rgba >> 8) & 0xFF) / 255.0F);
        this.alpha = (rgba & 0xFF) / 255.0F;

        this.lifetime = 6;
        this.quadSize = effect.getScale();
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return this.light ? 15728880 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** BC 的 buildGeometry：旋转矩阵 + XZ 平面四角 + 正反两遍顶点。 */
    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float partialTick) {
        var cameraPos = camera.getPosition();
        float x = (float) (this.xo - cameraPos.x());
        float y = (float) (this.yo - cameraPos.y());
        float z = (float) (this.zo - cameraPos.z());
        float size = this.getQuadSize(partialTick);
        float minU = this.getU0();
        float maxU = this.getU1();
        float minV = this.getV0();
        float maxV = this.getV1();

        Matrix4f rotationMatrix = new Matrix4f();
        rotationMatrix.identity();
        rotationMatrix.rotate((float) Math.toRadians(-this.yaw), new Vector3f(0.0F, 1.0F, 0.0F));
        rotationMatrix.rotate((float) Math.toRadians(this.pitch), new Vector3f(1.0F, 0.0F, 0.0F));
        rotationMatrix.rotate((float) Math.toRadians(this.roll), new Vector3f(0.0F, 0.0F, 1.0F));
        rotationMatrix.rotate((float) Math.toRadians(-this.localYaw), new Vector3f(0.0F, 1.0F, 0.0F));

        Vector4f[] corners = new Vector4f[]{
                new Vector4f(-size, 0.0F, -size, 1.0F),
                new Vector4f(-size, 0.0F, size, 1.0F),
                new Vector4f(size, 0.0F, size, 1.0F),
                new Vector4f(size, 0.0F, -size, 1.0F)};
        for (Vector4f corner : corners) {
            rotationMatrix.transform(corner);
            corner.add(x, y, z, 0.0F);
        }

        int light = this.getLightColor(partialTick);
        vertexConsumer.vertex(corners[0].x(), corners[0].y(), corners[0].z())
                .uv(maxU, maxV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(corners[1].x(), corners[1].y(), corners[1].z())
                .uv(maxU, minV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(corners[2].x(), corners[2].y(), corners[2].z())
                .uv(minU, minV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(corners[3].x(), corners[3].y(), corners[3].z())
                .uv(minU, maxV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        // 反面（BC 双面渲染）
        vertexConsumer.vertex(corners[3].x(), corners[3].y(), corners[3].z())
                .uv(minU, maxV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(corners[2].x(), corners[2].y(), corners[2].z())
                .uv(minU, minV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(corners[1].x(), corners[1].y(), corners[1].z())
                .uv(maxU, minV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        vertexConsumer.vertex(corners[0].x(), corners[0].y(), corners[0].z())
                .uv(maxU, maxV).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }
}

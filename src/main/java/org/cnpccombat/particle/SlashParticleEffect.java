package org.cnpccombat.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * 刀光粒子效果（移植自 Better Combat 1.21.1 的 SlashParticleEffect）。
 *
 * <p>1.20.1 与 1.21.1 的差异：
 * <ul>
 *   <li>1.21.1 用 {@code MapCodec} + {@code PacketCodec}；1.20.1 的 {@link ParticleType}
 *       构造需要 {@link ParticleOptions.Deserializer}，网络序列化走 {@link #writeToNetwork}
 *       / {@code fromNetwork}，命令行序列化走 {@link #writeToString} / {@code fromCommand}。</li>
 *   <li>codec() 只被 1.20.1 的命令参数等少数路径使用，按 1.20.1 的 {@code Codec} 写法实现。</li>
 * </ul>
 */
public class SlashParticleEffect implements ParticleOptions {
    private final ParticleType<SlashParticleEffect> type;
    private final float scale;
    private final float pitch;
    private final float yaw;
    private final float localYaw;
    private final float roll;
    private final boolean light;
    private final long colorRGBA;

    public SlashParticleEffect(ParticleType<SlashParticleEffect> type, float scale, float pitch,
                               float yaw, float localYaw, float roll, boolean light, long colorRGBA) {
        this.type = type;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.localYaw = localYaw;
        this.roll = roll;
        this.light = light;
        this.colorRGBA = colorRGBA;
    }

    @Override
    public ParticleType<?> getType() {
        return this.type;
    }

    public float getScale() {
        return this.scale;
    }

    public float getPitch() {
        return this.pitch;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getLocalYaw() {
        return this.localYaw;
    }

    public float getRoll() {
        return this.roll;
    }

    public boolean getLight() {
        return this.light;
    }

    public long getColorRGBA() {
        return this.colorRGBA;
    }

    /** 网络写出顺序必须与 {@link #DESERIALIZER#fromNetwork} 一致。 */
    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(this.scale);
        buf.writeFloat(this.pitch);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.localYaw);
        buf.writeFloat(this.roll);
        buf.writeBoolean(this.light);
        buf.writeLong(this.colorRGBA);
    }

    /** {@code writeToString} 与 {@code fromCommand} 互为逆操作（/particle 命令用）。 */
    @Override
    public String writeToString() {
        ResourceLocation id = BuiltInRegistries.PARTICLE_TYPE.getKey(this.type);
        return String.format(Locale.ROOT, "%s %s %s %s %s %s %s %s",
                id, this.scale, this.pitch, this.yaw, this.localYaw, this.roll, this.light, this.colorRGBA);
    }

    /** 1.20.1 网络解码 + 命令行解析都走这个 Deserializer（type 由参数传入，无构造顺序问题）。 */
    public static final ParticleOptions.Deserializer<SlashParticleEffect> DESERIALIZER =
            new ParticleOptions.Deserializer<SlashParticleEffect>() {
                @Override
                public SlashParticleEffect fromCommand(ParticleType<SlashParticleEffect> particleType,
                                                       StringReader reader) throws CommandSyntaxException {
                    reader.skipWhitespace();
                    float scale = reader.readFloat();
                    reader.skipWhitespace();
                    float pitch = reader.readFloat();
                    reader.skipWhitespace();
                    float yaw = reader.readFloat();
                    reader.skipWhitespace();
                    float localYaw = reader.readFloat();
                    reader.skipWhitespace();
                    float roll = reader.readFloat();
                    reader.skipWhitespace();
                    boolean light = reader.readBoolean();
                    reader.skipWhitespace();
                    long color = reader.readLong();
                    return new SlashParticleEffect(particleType, scale, pitch, yaw, localYaw, roll, light, color);
                }

                @Override
                public SlashParticleEffect fromNetwork(ParticleType<SlashParticleEffect> particleType,
                                                       FriendlyByteBuf buf) {
                    float scale = buf.readFloat();
                    float pitch = buf.readFloat();
                    float yaw = buf.readFloat();
                    float localYaw = buf.readFloat();
                    float roll = buf.readFloat();
                    boolean light = buf.readBoolean();
                    long color = buf.readLong();
                    return new SlashParticleEffect(particleType, scale, pitch, yaw, localYaw, roll, light, color);
                }
            };

    /** 字段名与 BC 1.21.1 的 MapCodec 保持一致（scale/pitch/yaw/local_yaw/roll/light/color_rgba）。 */
    public static Codec<SlashParticleEffect> createCodec(ParticleType<SlashParticleEffect> particleType) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("scale").forGetter(SlashParticleEffect::getScale),
                Codec.FLOAT.fieldOf("pitch").forGetter(SlashParticleEffect::getPitch),
                Codec.FLOAT.fieldOf("yaw").forGetter(SlashParticleEffect::getYaw),
                Codec.FLOAT.fieldOf("local_yaw").forGetter(SlashParticleEffect::getLocalYaw),
                Codec.FLOAT.fieldOf("roll").forGetter(SlashParticleEffect::getRoll),
                Codec.BOOL.fieldOf("light").forGetter(SlashParticleEffect::getLight),
                Codec.LONG.fieldOf("color_rgba").forGetter(SlashParticleEffect::getColorRGBA)
        ).apply(instance, (scale, pitch, yaw, localYaw, roll, light, colorRGBA) ->
                new SlashParticleEffect(particleType, scale, pitch, yaw, localYaw, roll, light, colorRGBA)));
    }
}

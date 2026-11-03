package org.cnpccombat.compat.bcp;

import malfu.bc_particle.BetterCombatParticleMods;
import malfu.bc_particle.config.ColorChangerSettings;
import malfu.bc_particle.config.ColorLightSettings;
import malfu.bc_particle.config.ParticleSettings;
import malfu.bc_particle.config.ParticleSettingsLoader;
import malfu.bc_particle.network.ClientNetworkHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 用 bc_particle 自己的粒子渲染 NPC 刀光（仅客户端，且仅在其已安装时加载）。
 *
 * <p>位置计算逐条对齐 {@code BCParticleUtil.sendPacketParticleDataForMobs}：
 * 每个 {@link ParticleSettings} 单独算生成点，再交给它自己的
 * {@link ClientNetworkHandler#spawnParticleFromNetwork} 生成 top/bottom 两层粒子。
 * 这样动画名 → 粒子类型的映射完全由 bc_particle 的资源数据决定，
 * Malfu 等动作 mod 覆盖 {@code particle_settings.json} 后即可正确适配。
 */
public final class BcParticleClientBridge {
    private BcParticleClientBridge() {
    }

    public static void spawn(ClientLevel level, LivingEntity attacker, String animation,
                             boolean offHand, float weaponRange) {
        if (!BetterCombatParticleMods.config.showBMCParticle) {
            return;
        }
        List<ParticleSettings> settings = ParticleSettingsLoader.INSTANCE.getSettings(animation);
        if (settings.isEmpty()) {
            return;
        }

        // 与 bc_particle 一致：左撇子与副手取异或，决定镜像方向。
        boolean mirror = (attacker.getMainArm() == HumanoidArm.LEFT) ^ offHand;
        double flip = mirror ? -1.0D : 1.0D;

        float yaw = attacker.getYRot();
        float pitch = attacker.getXRot();
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F).normalize();
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();

        ItemStack stack = offHand ? attacker.getOffhandItem() : attacker.getMainHandItem();
        ColorLightSettings color = ColorChangerSettings.resolveWeaponEffects(stack);

        double baseX = attacker.getX();
        double baseY = attacker.getEyeY() - 0.25D;
        double baseZ = attacker.getZ();

        for (ParticleSettings setting : settings) {
            Vec3 pos = new Vec3(baseX, baseY + setting.yAddition(), baseZ)
                    .add(forward.scale(setting.zAddition()))
                    .add(right.scale(setting.xAddition() * flip));
            Vec3 stabPos = pos.add(forward.scale(weaponRange - 1.5D));
            ClientNetworkHandler.spawnParticleFromNetwork(
                    level,
                    pos.x, pos.y, pos.z,
                    stabPos.x, stabPos.y, stabPos.z,
                    pitch, yaw, weaponRange,
                    mirror,
                    List.of(setting),
                    color.light(), color.colorHex(), color.colorHexSec());
        }
    }
}

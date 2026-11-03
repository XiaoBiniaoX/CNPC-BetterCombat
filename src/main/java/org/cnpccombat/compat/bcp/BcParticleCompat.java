package org.cnpccombat.compat.bcp;

import net.minecraftforge.fml.ModList;

/**
 * 「更好的战斗粒子」(bc_particle) **可选**兼容入口。
 *
 * <p>本类不引用 bc_particle 的任何类型，未安装时也可以安全加载。
 * 真正触碰其 API 的代码全部隔离在 {@code org.cnpccombat.compat.bcp.BcParticleClientBridge}
 * （仅客户端），调用前必须先过 {@link #isLoaded()}。
 */
public final class BcParticleCompat {
    public static final String MOD_ID = "bc_particle";

    private BcParticleCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}

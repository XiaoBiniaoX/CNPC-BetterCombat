package org.cnpccombat.api;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

/** 每 NPC 的显示设置；不改变服务端攻击和伤害逻辑。 */
public interface NpcAnimationSettings {
    boolean cnpc$isBcAnimationEnabled();

    void cnpc$setBcAnimationEnabled(boolean enabled);

    static boolean isEnabled(LivingEntity entity) {
        return !(entity instanceof EntityNPCInterface npc)
                || ((NpcAnimationSettings) npc.display).cnpc$isBcAnimationEnabled();
    }
}

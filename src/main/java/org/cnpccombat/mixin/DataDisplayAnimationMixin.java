package org.cnpccombat.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataDisplay;
import org.cnpccombat.api.NpcAnimationSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataDisplay.class)
public abstract class DataDisplayAnimationMixin implements NpcAnimationSettings {
    @Unique
    private static final String CNPC$ANIMATION_KEY = "CnpcCombatAnimationEnabled";

    @Unique
    private boolean cnpc$bcAnimationEnabled = true;

    @Override
    public boolean cnpc$isBcAnimationEnabled() {
        return this.cnpc$bcAnimationEnabled;
    }

    @Override
    public void cnpc$setBcAnimationEnabled(boolean enabled) {
        this.cnpc$bcAnimationEnabled = enabled;
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void cnpc$readAnimationSetting(CompoundTag compound, CallbackInfo ci) {
        this.cnpc$bcAnimationEnabled = !compound.contains(CNPC$ANIMATION_KEY, Tag.TAG_BYTE)
                || compound.getBoolean(CNPC$ANIMATION_KEY);
    }

    @Inject(method = "save", at = @At("TAIL"), remap = false)
    private void cnpc$saveAnimationSetting(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound.putBoolean(CNPC$ANIMATION_KEY, this.cnpc$bcAnimationEnabled);
    }
}

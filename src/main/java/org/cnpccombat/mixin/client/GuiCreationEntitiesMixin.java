package org.cnpccombat.mixin.client;

import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.cnpccombat.api.NpcAnimGroupMirror;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiCreationEntities.class)
public abstract class GuiCreationEntitiesMixin extends GuiCreationScreenInterface {
    @Unique
    private static final int CNPC$ATTACK_ANIMATIONS_ID = 250;

    private GuiCreationEntitiesMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpc$addAttackAnimationsToggle(CallbackInfo ci) {
        boolean enabled = this.npc.display instanceof NpcAnimGroupMirror mirror
                && mirror.cnpc$areAttackAnimationsEnabled();
        this.addLabel(new GuiLabel(250, "cnpccombat.gui.attackAnimations", this.guiLeft + 124, this.guiTop + 27));
        this.addButton(new GuiButtonNop(
                (IGuiInterface) this,
                CNPC$ATTACK_ANIMATIONS_ID,
                this.guiLeft + 260,
                this.guiTop + 22,
                60,
                20,
                button -> {
                    if (this.npc.display instanceof NpcAnimGroupMirror mirror) {
                        mirror.cnpc$setAttackAnimationsEnabled(((GuiButtonNop) button).getValue() == 1);
                    }
                },
                enabled ? 1 : 0,
                "gui.no",
                "gui.yes"
        ));
    }
}

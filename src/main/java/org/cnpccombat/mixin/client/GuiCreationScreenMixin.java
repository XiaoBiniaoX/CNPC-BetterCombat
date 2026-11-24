package org.cnpccombat.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import org.cnpccombat.client.gui.GuiStringSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiCreationScreenInterface.class)
public abstract class GuiCreationScreenMixin extends GuiNPCInterface {
    private GuiCreationScreenMixin() {
        super(null);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/model/GuiCreationScreenInterface;drawNpc(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/LivingEntity;IIFI)V",
            remap = false), cancellable = true)
    private void cnpc$pauseSelectionPreview(GuiGraphics graphics, int x, int y, float partialTick, CallbackInfo ci) {
        // 不覆写 drawNpc：让 Gecko 等可选扩展保留自己的框内预览与子界面判断。
        if ((Object) this instanceof GuiCreationEntities && this.getSubGui() instanceof GuiStringSelection) {
            ci.cancel();
        }
    }
}

package red.jackf.whereisit.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.jackf.whereisit.client.render.Rendering;
import red.jackf.whereisit.config.WhereIsItConfig;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixinFront {
    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void afterRenderSlotHighlightFront(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (WhereIsItConfig.INSTANCE.instance().getClient().renderHighlightAboveItems) {
            Rendering.renderSlotHighlight((AbstractContainerScreen<?>) (Object) this, guiGraphics, partialTick, true, mouseX, mouseY);
        }
    }
}
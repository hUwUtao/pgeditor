package work.stdpi.pge.editor.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.stdpi.pge.editor.logic.ViewportController;

@Mixin(targets = "net.minecraft.class_276")
public class FramebufferMixin {
    @Inject(method = "method_1235", at = @At("TAIL"))
    private void pge$reapplyViewportAfterFramebufferBind(boolean setViewport, CallbackInfo ci) {
        if (!setViewport) {
            return;
        }
        if (!ViewportController.INSTANCE.isActive() || !ViewportController.INSTANCE.isWindowMetricsOverridden()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) {
            return;
        }

        ViewportController.INSTANCE.apply(client.getWindow());
    }
}

package work.stdpi.pge.editor.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.input.CharInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.render.EditorUI;
import work.stdpi.pge.editor.WindowAccessor;

public class InputMixin {
    @Mixin(Mouse.class)
    public abstract static class MouseMixin {
        @Shadow private double x;
        @Shadow private double y;

        @Inject(method = "onCursorPos", at = @At("RETURN"))
        private void remapMouse(long win, double x, double y, CallbackInfo ci) {
            if (ViewportController.INSTANCE.isActive()
                && ViewportController.INSTANCE.isWindowMetricsOverridden()
                && MinecraftClient.getInstance().currentScreen == null) {
                var window = MinecraftClient.getInstance().getWindow();
                double s = (double) ((WindowAccessor) (Object) window).pge$getRealFramebufferWidth()
                    / ((WindowAccessor) (Object) window).pge$getRealScaledWidth();
                this.x -= ViewportController.INSTANCE.getX() * s;
                this.y -= ViewportController.INSTANCE.getY() * s;
            }
            EditorUI.INSTANCE.onMove(x, y);
        }

        @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
        private void onMouse(long win, MouseInput input, int action, CallbackInfo ci) {
            if (EditorUI.INSTANCE.onMouse(input.button(), action, input.modifiers())) {
                ci.cancel();
            }
        }
    }

    @Mixin(Keyboard.class)
    public static class KeyboardMixin {
        @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
        private void onKey(long win, int action, KeyInput input, CallbackInfo ci) {
            if (EditorUI.INSTANCE.onKey(input.key(), action, input.modifiers())) {
                ci.cancel();
            }
        }

        @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
        private void onChar(long win, CharInput input, CallbackInfo ci) {
            if (EditorUI.INSTANCE.onChar(input.codepoint())) {
                ci.cancel();
            }
        }
    }
}

package work.stdpi.pge.editor.mixin;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.WindowAccessor;

@Mixin(Window.class)
public abstract class WindowMixin implements WindowAccessor {

    @Override
    @Accessor("width")
    public abstract int pge$getRealWindowWidth();

    @Override
    @Accessor("height")
    public abstract int pge$getRealWindowHeight();

    @Override
    @Accessor("framebufferWidth")
    public abstract int pge$getRealFramebufferWidth();

    @Override
    @Accessor("framebufferHeight")
    public abstract int pge$getRealFramebufferHeight();

    @Override
    @Accessor("scaledWidth")
    public abstract int pge$getRealScaledWidth();

    @Override
    @Accessor("scaledHeight")
    public abstract int pge$getRealScaledHeight();

    private double pge$getScale() {
        int sw = pge$getRealScaledWidth();
        return sw > 0 ? (double) pge$getRealFramebufferWidth() / sw : 1.0;
    }

    private double pge$getWindowScale() {
        int realWindowWidth = pge$getRealWindowWidth();
        return realWindowWidth > 0 ? (double) pge$getRealFramebufferWidth() / realWindowWidth : pge$getScale();
    }

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void getWidth(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive() && ViewportController.INSTANCE.isWindowMetricsOverridden()) {
            cir.setReturnValue((int) Math.round(ViewportController.INSTANCE.getWidth() / pge$getWindowScale()));
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void getHeight(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive() && ViewportController.INSTANCE.isWindowMetricsOverridden()) {
            cir.setReturnValue((int) Math.round(ViewportController.INSTANCE.getHeight() / pge$getWindowScale()));
        }
    }

    @Inject(method = "getFramebufferWidth", at = @At("HEAD"), cancellable = true)
    private void getFramebufferWidth(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive() && ViewportController.INSTANCE.isWindowMetricsOverridden()) {
            cir.setReturnValue((int) (ViewportController.INSTANCE.getWidth() * pge$getScale()));
        }
    }

    @Inject(method = "getFramebufferHeight", at = @At("HEAD"), cancellable = true)
    private void getFramebufferHeight(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive() && ViewportController.INSTANCE.isWindowMetricsOverridden()) {
            cir.setReturnValue((int) (ViewportController.INSTANCE.getHeight() * pge$getScale()));
        }
    }

    @Inject(method = "getScaledWidth", at = @At("HEAD"), cancellable = true)
    private void getScaledWidth(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive() && ViewportController.INSTANCE.isWindowMetricsOverridden()) {
            cir.setReturnValue(ViewportController.INSTANCE.getWidth());
        }
    }

    @Inject(method = "getScaledHeight", at = @At("HEAD"), cancellable = true)
    private void getScaledHeight(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive() && ViewportController.INSTANCE.isWindowMetricsOverridden()) {
            cir.setReturnValue(ViewportController.INSTANCE.getHeight());
        }
    }
}

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

    @Inject(method = "getScaledWidth", at = @At("HEAD"), cancellable = true)
    private void getScaledWidth(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive()) {
            cir.setReturnValue(Math.max(1, ViewportController.INSTANCE.getWidth()));
        }
    }

    @Inject(method = "getScaledHeight", at = @At("HEAD"), cancellable = true)
    private void getScaledHeight(CallbackInfoReturnable<Integer> cir) {
        if (ViewportController.INSTANCE.isActive()) {
            cir.setReturnValue(Math.max(1, ViewportController.INSTANCE.getHeight()));
        }
    }
}

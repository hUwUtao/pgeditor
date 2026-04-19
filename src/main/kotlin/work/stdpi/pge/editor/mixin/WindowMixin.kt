package work.stdpi.pge.editor.mixin

import net.minecraft.client.util.Window
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import work.stdpi.pge.editor.WindowAccessor
import work.stdpi.pge.editor.logic.ViewportController

@Mixin(Window::class)
abstract class WindowMixin : WindowAccessor {
  @Accessor("width") abstract override fun `pge$getRealWindowWidth`(): Int

  @Accessor("height") abstract override fun `pge$getRealWindowHeight`(): Int

  @Accessor("framebufferWidth") abstract override fun `pge$getRealFramebufferWidth`(): Int

  @Accessor("framebufferHeight") abstract override fun `pge$getRealFramebufferHeight`(): Int

  @Accessor("scaledWidth") abstract override fun `pge$getRealScaledWidth`(): Int

  @Accessor("scaledHeight") abstract override fun `pge$getRealScaledHeight`(): Int

  private fun `pge$getScale`(): Double {
    val sw = `pge$getRealScaledWidth`()
    return if (sw > 0) `pge$getRealFramebufferWidth`().toDouble() / sw else 1.0
  }

  private fun `pge$getWindowScale`(): Double {
    val realWindowWidth = `pge$getRealWindowWidth`()
    return if (realWindowWidth > 0) `pge$getRealFramebufferWidth`().toDouble() / realWindowWidth
    else `pge$getScale`()
  }

  @Inject(method = ["getWidth"], at = [At("HEAD")], cancellable = true)
  private fun getWidth(cir: CallbackInfoReturnable<Int?>) {
    if (ViewportController.isActive && ViewportController.isWindowMetricsOverridden) {
        cir.returnValue = Math.round(ViewportController.width / `pge$getWindowScale`()).toInt()
    }
  }

  @Inject(method = ["getHeight"], at = [At("HEAD")], cancellable = true)
  private fun getHeight(cir: CallbackInfoReturnable<Int?>) {
    if (ViewportController.isActive && ViewportController.isWindowMetricsOverridden) {
        cir.returnValue = Math.round(ViewportController.height / `pge$getWindowScale`()).toInt()
    }
  }

  @Inject(method = ["getFramebufferWidth"], at = [At("HEAD")], cancellable = true)
  private fun getFramebufferWidth(cir: CallbackInfoReturnable<Int?>) {
    if (ViewportController.isActive && ViewportController.isWindowMetricsOverridden) {
        cir.returnValue = (ViewportController.width * `pge$getScale`()).toInt()
    }
  }

  @Inject(method = ["getFramebufferHeight"], at = [At("HEAD")], cancellable = true)
  private fun getFramebufferHeight(cir: CallbackInfoReturnable<Int?>) {
    if (ViewportController.isActive && ViewportController.isWindowMetricsOverridden) {
        cir.returnValue = (ViewportController.height * `pge$getScale`()).toInt()
    }
  }

  @Inject(method = ["getScaledWidth"], at = [At("HEAD")], cancellable = true)
  private fun getScaledWidth(cir: CallbackInfoReturnable<Int?>) {
    if (ViewportController.isActive && ViewportController.isWindowMetricsOverridden) {
        cir.returnValue = ViewportController.width
    }
  }

  @Inject(method = ["getScaledHeight"], at = [At("HEAD")], cancellable = true)
  private fun getScaledHeight(cir: CallbackInfoReturnable<Int?>) {
    if (ViewportController.isActive && ViewportController.isWindowMetricsOverridden) {
        cir.returnValue = ViewportController.height
    }
  }
}

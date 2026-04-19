package work.stdpi.pge.editor.mixin

import net.minecraft.client.Keyboard
import net.minecraft.client.MinecraftClient
import net.minecraft.client.Mouse
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.client.input.MouseInput
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import work.stdpi.pge.editor.WindowAccessor
import work.stdpi.pge.editor.logic.ViewportController
import work.stdpi.pge.editor.render.EditorUI

class InputMixin {
  @Mixin(Mouse::class)
  abstract class MouseMixin {
    @Shadow private var x = 0.0
    @Shadow private var y = 0.0

    @Inject(method = ["onCursorPos"], at = [At("RETURN")])
    private fun remapMouse(win: Long, x: Double, y: Double, ci: CallbackInfo?) {
      if (ViewportController.isActive &&
          ViewportController.isWindowMetricsOverridden &&
          MinecraftClient.getInstance().currentScreen == null) {
        val window = MinecraftClient.getInstance().window
        val s =
            (window as Any? as WindowAccessor).`pge$getRealFramebufferWidth`().toDouble() /
                (window as Any? as WindowAccessor).`pge$getRealScaledWidth`()
        this.x -= ViewportController.x * s
        this.y -= ViewportController.y * s
      }
      EditorUI.onMove(x, y)
    }

    @Inject(method = ["onMouseButton"], at = [At("HEAD")], cancellable = true)
    private fun onMouse(win: Long, input: MouseInput, action: Int, ci: CallbackInfo) {
      if (EditorUI.onMouse(input.button(), action, input.modifiers())) {
        ci.cancel()
      }
    }

    @Inject(method = ["onMouseScroll"], at = [At("HEAD")], cancellable = true)
    private fun onScroll(
        win: Long,
        horizontalAmount: Double,
        verticalAmount: Double,
        ci: CallbackInfo
    ) {
      if (EditorUI.onScroll(horizontalAmount, verticalAmount)) {
        ci.cancel()
      }
    }
  }

  @Mixin(Keyboard::class)
  class KeyboardMixin {
    @Inject(method = ["onKey"], at = [At("HEAD")], cancellable = true)
    private fun onKey(win: Long, action: Int, input: KeyInput, ci: CallbackInfo) {
      if (EditorUI.onKey(input.key(), action, input.modifiers())) {
        ci.cancel()
      }
    }

    @Inject(method = ["onChar"], at = [At("HEAD")], cancellable = true)
    private fun onChar(win: Long, input: CharInput, ci: CallbackInfo) {
      if (EditorUI.onChar(input.codepoint())) {
        ci.cancel()
      }
    }
  }
}

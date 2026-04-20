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
import work.stdpi.pge.editor.realFramebufferWidth
import work.stdpi.pge.editor.realFramebufferHeight
import work.stdpi.pge.editor.realMetrics
import work.stdpi.pge.editor.realScaledWidth
import work.stdpi.pge.editor.realScaledHeight
import work.stdpi.pge.editor.logic.EditorManager
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
        val metrics = window.realMetrics()
        val scaleX = metrics.realFramebufferWidth.toDouble() / metrics.realScaledWidth
        val scaleY = metrics.realFramebufferHeight.toDouble() / metrics.realScaledHeight
        this.x -= ViewportController.x * scaleX
        this.y -= ViewportController.y * scaleY
      }
      EditorUI.onMove(x, y)
    }

    @Inject(method = ["onMouseButton"], at = [At("HEAD")], cancellable = true)
    private fun onMouse(win: Long, input: MouseInput, action: Int, ci: CallbackInfo) {
      if (EditorUI.onMouse(input.button(), action, input.modifiers()) ||
          EditorUI.shouldBlockGameMouseInput()) {
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
      if (EditorUI.onScroll(horizontalAmount, verticalAmount) ||
          EditorUI.shouldBlockGameMouseInput()) {
        ci.cancel()
      }
    }
  }

  @Mixin(Keyboard::class)
  class KeyboardMixin {
    @Inject(method = ["onKey"], at = [At("HEAD")], cancellable = true)
    private fun onKey(win: Long, action: Int, input: KeyInput, ci: CallbackInfo) {
      if (EditorManager.handleToggleKey(input.key(), action, input.modifiers()) ||
          EditorUI.onKey(input.key(), action, input.modifiers()) ||
          EditorUI.shouldBlockGameKeyboardInput(input.key())) {
        ci.cancel()
      }
    }

    @Inject(method = ["onChar"], at = [At("HEAD")], cancellable = true)
    private fun onChar(win: Long, input: CharInput, ci: CallbackInfo) {
      if (EditorUI.onChar(input.codepoint()) || EditorUI.shouldBlockGameKeyboardInput()) {
        ci.cancel()
      }
    }
  }
}

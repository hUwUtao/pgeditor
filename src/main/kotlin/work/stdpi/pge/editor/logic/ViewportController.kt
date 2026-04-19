package work.stdpi.pge.editor.logic

import com.mojang.blaze3d.opengl.GlStateManager
import net.minecraft.client.util.Window
import work.stdpi.pge.editor.WindowAccessor
import work.stdpi.pge.editor.logic.EditorManager.DockSide
import kotlin.math.max
import kotlin.math.min

object ViewportController {
  var isActive: Boolean = false
  var isWindowMetricsOverridden: Boolean = false
  var x: Int = 0
    private set

  var y: Int = 0
    private set

  var width: Int = 0
    private set

  var height: Int = 0
    private set

  fun calculate(window: Window, side: DockSide, percent: Float) {
    val sw = (window as Any? as WindowAccessor).`pge$getRealScaledWidth`()
    val sh = (window as Any? as WindowAccessor).`pge$getRealScaledHeight`()

    if (sw <= 0 || sh <= 0) return

    var editorW = (sw * percent).toInt()
    var editorH = (sh * percent).toInt()
    editorW = max(200, min(editorW, sw - 50))
    editorH = max(200, min(editorH, sh - 50))

    when (side) {
      DockSide.LEFT -> set(editorW, 0, sw - editorW, sh)
      DockSide.RIGHT -> set(0, 0, sw - editorW, sh)
      DockSide.TOP -> set(0, editorH, sw, sh - editorH)
      DockSide.BOTTOM -> set(0, 0, sw, sh - editorH)
    }
  }

  private fun set(x: Int, y: Int, w: Int, h: Int) {
    this.x = x
    this.y = y
    this.width = w
    this.height = h
  }

  fun apply(window: Window) {
    if (!this.isActive) return
    val realScaledWidth = (window as Any? as WindowAccessor).`pge$getRealScaledWidth`()
    val realFramebufferWidth = (window as Any? as WindowAccessor).`pge$getRealFramebufferWidth`()
    val realScaledHeight = (window as Any? as WindowAccessor).`pge$getRealScaledHeight`()
    if (realScaledWidth <= 0 || realScaledHeight <= 0) return

    val s = realFramebufferWidth.toDouble() / realScaledWidth
    val px = (x * s).toInt()
    val py = ((realScaledHeight - (y + height)) * s).toInt()
    val pw = (width * s).toInt()
    val ph = (height * s).toInt()
    if (pw > 0 && ph > 0) GlStateManager._viewport(px, py, pw, ph)
  }
}

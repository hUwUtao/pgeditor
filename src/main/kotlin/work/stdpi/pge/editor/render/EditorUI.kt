package work.stdpi.pge.editor.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.Window
import org.lwjgl.glfw.GLFW
import work.stdpi.pge.editor.realFramebufferHeight
import work.stdpi.pge.editor.realFramebufferWidth
import work.stdpi.pge.editor.realMetrics
import work.stdpi.pge.editor.realScaledHeight
import work.stdpi.pge.editor.realScaledWidth
import work.stdpi.pge.editor.logic.EditorManager
import work.stdpi.pge.editor.logic.EditorManager.DockSide
import work.stdpi.pge.editor.logic.ViewportController
import work.stdpi.pge.editor.render.applet.AbstractTerminalApplet
import work.stdpi.pge.editor.render.canvas.CanvasRect
import work.stdpi.pge.editor.render.canvas.CanvasSpace
import work.stdpi.pge.editor.render.canvas.input.CanvasCharEvent
import work.stdpi.pge.editor.render.canvas.input.CanvasKeyEvent
import work.stdpi.pge.editor.render.canvas.input.CanvasPointerEvent
import work.stdpi.pge.editor.render.canvas.input.CanvasScrollEvent
import kotlin.math.max

object EditorUI {
  private var focused = false
  private var windowFocused = true
  private var editorBounds = CanvasRect.ZERO
  private var appletBounds = CanvasRect.ZERO

  fun render(context: DrawContext) {
    if (!EditorManager.isEnabled) return
    val mc = MinecraftClient.getInstance()
    val win = mc.window
    val metrics = WindowMetrics.from(win)
    val applet = EditorManager.currentApplet
    editorBounds = computeEditorBounds(metrics)
    if (!editorBounds.isVisible) return

    val nowWindowFocused =
        GLFW.glfwGetWindowAttrib(win.handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE
    if (nowWindowFocused && !windowFocused && focused) {
      applet.onCanvasFocusGained()
    } else if (!nowWindowFocused && windowFocused) {
      if (focused) applet.onCanvasFocusLost()
      focused = false
    }
    windowFocused = nowWindowFocused

    appletBounds =
        when (applet.space) {
          CanvasSpace.FRAMEBUFFER -> editorBounds.toFramebuffer(metrics)
          CanvasSpace.SCALED -> editorBounds
        }

    context.matrices.pushMatrix()
    when (applet.space) {
      CanvasSpace.FRAMEBUFFER ->
          context.matrices.scale(1.0f / metrics.framebufferScaleX, 1.0f / metrics.framebufferScaleY)
      CanvasSpace.SCALED -> context.matrices.scale(metrics.liedScaleX, metrics.liedScaleY)
    }
    context.fill(
        appletBounds.x,
        appletBounds.y,
        appletBounds.x + appletBounds.width,
        appletBounds.y + appletBounds.height,
        PANEL_BG)
    applet.render(context, appletBounds)
    context.matrices.popMatrix()
  }

  fun onMouse(b: Int, a: Int, m: Int): Boolean {
    if (!EditorManager.isEnabled) return false
    val mc = MinecraftClient.getInstance()
    val applet = EditorManager.currentApplet
    val (mx, my) =
        when (applet.space) {
          CanvasSpace.FRAMEBUFFER -> mc.mouse.x.toInt() to mc.mouse.y.toInt()
          CanvasSpace.SCALED -> WindowMetrics.from(mc.window).framebufferToScaled(mc.mouse.x, mc.mouse.y)
        }

    if (appletBounds.contains(mx, my)) {
      if (!focused) {
        applet.onCanvasFocusGained()
      }
      focused = true
      return applet.onCanvasPointerButton(
          CanvasPointerEvent(
              localX = mx - appletBounds.x,
              localY = my - appletBounds.y,
              bounds = appletBounds,
              button = b,
              action = a,
              mods = m))
    }
    if (focused) applet.onCanvasFocusLost()
    focused = false
    return false
  }

  fun shouldBlockGameMouseInput(): Boolean {
    if (!EditorManager.isEnabled) return false
    if (MinecraftClient.getInstance().currentScreen != null) return false
    return focused
  }

  fun shouldBlockGameKeyboardInput(key: Int? = null): Boolean {
    if (!EditorManager.isEnabled) return false
    if (key != null && EditorManager.isToggleKey(key)) return false
    return focused
  }

  fun onMove(x: Double, y: Double) {
    if (!EditorManager.isEnabled || !focused) return
    val applet = EditorManager.currentApplet
    val (mx, my) =
        when (applet.space) {
          CanvasSpace.FRAMEBUFFER -> x.toInt() to y.toInt()
          CanvasSpace.SCALED ->
              WindowMetrics.from(MinecraftClient.getInstance().window).framebufferToScaled(x, y)
        }
    if (appletBounds.contains(mx, my)) {
      applet.onCanvasPointerMove(
          CanvasPointerEvent(
              localX = mx - appletBounds.x,
              localY = my - appletBounds.y,
              bounds = appletBounds))
    }
  }

  fun onScroll(horizontalAmount: Double, verticalAmount: Double): Boolean {
    if (!EditorManager.isEnabled || !focused) return false
    val mc = MinecraftClient.getInstance()
    val applet = EditorManager.currentApplet
    val (mx, my) =
        when (applet.space) {
          CanvasSpace.FRAMEBUFFER -> mc.mouse.x.toInt() to mc.mouse.y.toInt()
          CanvasSpace.SCALED -> WindowMetrics.from(mc.window).framebufferToScaled(mc.mouse.x, mc.mouse.y)
        }

    if (appletBounds.contains(mx, my)) {
      return applet.onCanvasScroll(
          CanvasScrollEvent(
              localX = mx - appletBounds.x,
              localY = my - appletBounds.y,
              bounds = appletBounds,
              horizontalAmount = horizontalAmount,
              verticalAmount = verticalAmount,
              mods = 0))
    }
    return false
  }

  fun onKey(k: Int, a: Int, m: Int): Boolean {
    if (!EditorManager.isEnabled) return false
    if (EditorManager.isToggleKey(k)) return false
    val applet = EditorManager.currentApplet
    if (focused) {
      return applet.onCanvasKey(CanvasKeyEvent(k, a, m))
    }
    return false
  }

  fun onChar(c: Int): Boolean {
    val applet = EditorManager.currentApplet
    if (EditorManager.isEnabled && focused) {
      return applet.onCanvasChar(CanvasCharEvent(c))
    }
    return false
  }

  private fun computeEditorBounds(metrics: WindowMetrics): CanvasRect {
    val gx = ViewportController.x
    val gy = ViewportController.y
    val gw = ViewportController.width
    val gh = ViewportController.height
    return when (EditorManager.side) {
      DockSide.LEFT -> CanvasRect(0, 0, gx, metrics.scaledHeight)
      DockSide.RIGHT -> CanvasRect(gx + gw, 0, metrics.scaledWidth - (gx + gw), metrics.scaledHeight)
      DockSide.TOP -> CanvasRect(0, 0, metrics.scaledWidth, gy)
      DockSide.BOTTOM -> CanvasRect(0, gy + gh, metrics.scaledWidth, metrics.scaledHeight - (gy + gh))
    }
  }

  private fun CanvasRect.toFramebuffer(metrics: WindowMetrics): CanvasRect =
      CanvasRect(
          Math.round(x * metrics.framebufferScaleX),
          Math.round(y * metrics.framebufferScaleY),
          max(1, Math.round((x + width) * metrics.framebufferScaleX) - Math.round(x * metrics.framebufferScaleX)),
          max(1, Math.round((y + height) * metrics.framebufferScaleY) - Math.round(y * metrics.framebufferScaleY)))

  private data class WindowMetrics(
      val scaledWidth: Int,
      val scaledHeight: Int,
      val framebufferScaleX: Float,
      val framebufferScaleY: Float,
      val liedScaleX: Float,
      val liedScaleY: Float
  ) {
    fun framebufferToScaled(x: Double, y: Double): Pair<Int, Int> {
      val scaledX = if (framebufferScaleX != 0.0f) x / framebufferScaleX else x
      val scaledY = if (framebufferScaleY != 0.0f) y / framebufferScaleY else y
      return scaledX.toInt() to scaledY.toInt()
    }

    companion object {
      fun from(window: Window): WindowMetrics {
        val real = window.realMetrics()
        val scaledWidth = real.realScaledWidth
        val scaledHeight = real.realScaledHeight
        val framebufferWidth = real.realFramebufferWidth
        val framebufferHeight = real.realFramebufferHeight
        val framebufferScaleX = if (scaledWidth > 0) framebufferWidth.toFloat() / scaledWidth else 1.0f
        val framebufferScaleY =
            if (scaledHeight > 0) framebufferHeight.toFloat() / scaledHeight else 1.0f
        return WindowMetrics(
            scaledWidth = scaledWidth,
            scaledHeight = scaledHeight,
            framebufferScaleX = framebufferScaleX,
            framebufferScaleY = framebufferScaleY,
            liedScaleX = if (scaledWidth > 0) window.scaledWidth.toFloat() / scaledWidth else 1.0f,
            liedScaleY = if (scaledHeight > 0) window.scaledHeight.toFloat() / scaledHeight else 1.0f)
      }
    }
  }
}

private const val PANEL_BG = -0xe1e1e2

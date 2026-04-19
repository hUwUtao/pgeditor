package work.stdpi.pge.editor.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.stdpi.pge.editor.WindowAccessor
import work.stdpi.pge.editor.logic.EditorManager
import work.stdpi.pge.editor.logic.EditorManager.DockSide
import work.stdpi.pge.editor.logic.ViewportController
import kotlin.math.max

object EditorUI {
  private val term = TerminalRenderer()
  private val glyphGrid = GlyphGridRenderer()
  private val miniGame = MiniGameRenderer()
  private var regionLogged = false
  private var focused = false
  private var windowFocused = true
  private var ex = 0
  private var ey = 0
  private var ew = 0
  private var eh = 0

  fun render(context: DrawContext) {
    if (!EditorManager.isEnabled) return
    val mc = MinecraftClient.getInstance()
    val win = mc.window

    val rsw = (win as Any? as WindowAccessor).`pge$getRealScaledWidth`()
    val rsh = (win as Any? as WindowAccessor).`pge$getRealScaledHeight`()
    val rfw = (win as Any? as WindowAccessor).`pge$getRealFramebufferWidth`()
    val rfh = (win as Any? as WindowAccessor).`pge$getRealFramebufferHeight`()

    // EDITOR zone
    val gx = ViewportController.x
    val gy = ViewportController.y
    val gw = ViewportController.width
    val gh = ViewportController.height

    when (EditorManager.side) {
      DockSide.LEFT -> {
        ex = 0
        ey = 0
        ew = gx
        eh = rsh
      }
      DockSide.RIGHT -> {
        ex = gx + gw
        ey = 0
        ew = rsw - ex
        eh = rsh
      }
      DockSide.TOP -> {
        ex = 0
        ey = 0
        ew = rsw
        eh = gy
      }
      DockSide.BOTTOM -> {
        ex = 0
        ey = gy + gh
        ew = rsw
        eh = rsh - ey
      }
    }

    if (ew <= 0 || eh <= 0) return

    if (!regionLogged) {
      LOGGER.info(
          "editor region side={} editor=({}, {}) {}x{} viewport=({}, {}) {}x{} screen={}x{}",
          EditorManager.side,
          ex,
          ey,
          ew,
          eh,
          gx,
          gy,
          gw,
          gh,
          rsw,
          rsh)
      regionLogged = true
    }

    val nowWindowFocused =
        GLFW.glfwGetWindowAttrib(win.handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE
    if (nowWindowFocused && !windowFocused && focused) {
      LOGGER.info("minecraft window focus restored while terminal focused")
      term.focus()
    }
    windowFocused = nowWindowFocused

    if (EditorManager.renderMode == EditorManager.RenderMode.TERMINAL) {
      val framebufferScaleX = if (rsw > 0) rfw.toFloat() / rsw else 1.0f
      val framebufferScaleY = if (rsh > 0) rfh.toFloat() / rsh else 1.0f
      val exPx = Math.round(ex * framebufferScaleX)
      val eyPx = Math.round(ey * framebufferScaleY)
      val ewPx = max(1, Math.round(ew * framebufferScaleX))
      val ehPx = max(1, Math.round(eh * framebufferScaleY))

      context.matrices.pushMatrix()
      context.matrices.scale(1.0f / framebufferScaleX, 1.0f / framebufferScaleY)
      context.fill(exPx, eyPx, exPx + ewPx, eyPx + ehPx, -0xe1e1e2)
      term.render(context, exPx, eyPx, ewPx, ehPx)
      context.matrices.popMatrix()
    } else if (EditorManager.renderMode == EditorManager.RenderMode.GIZMO_GRID) {
      val liedScaleX = win.scaledWidth.toFloat() / rsw
      val liedScaleY = win.scaledHeight.toFloat() / rsh
      context.matrices.pushMatrix()
      context.matrices.scale(liedScaleX, liedScaleY)
      context.fill(ex, ey, ex + ew, ey + eh, -0xe1e1e2)
      glyphGrid.render(context, ex, ey, ew, eh)
      context.matrices.popMatrix()
    } else {
      val liedScaleX = win.scaledWidth.toFloat() / rsw
      val liedScaleY = win.scaledHeight.toFloat() / rsh
      context.matrices.pushMatrix()
      context.matrices.scale(liedScaleX, liedScaleY)
      context.fill(ex, ey, ex + ew, ey + eh, -0xe1e1e2)
      miniGame.render(context, ex, ey, ew, eh)
      context.matrices.popMatrix()
    }
  }

  fun onMouse(b: Int, a: Int, m: Int): Boolean {
    if (!EditorManager.isEnabled) return false
    val mc = MinecraftClient.getInstance()
    val win = mc.window
    if (EditorManager.renderMode == EditorManager.RenderMode.TERMINAL) {
      val framebufferScaleX =
          if ((win as Any? as WindowAccessor).`pge$getRealScaledWidth`() > 0)
              (win as Any? as WindowAccessor).`pge$getRealFramebufferWidth`().toFloat() /
                  (win as Any? as WindowAccessor).`pge$getRealScaledWidth`()
          else 1.0f
      val framebufferScaleY =
          if ((win as Any? as WindowAccessor).`pge$getRealScaledHeight`() > 0)
              (win as Any? as WindowAccessor).`pge$getRealFramebufferHeight`().toFloat() /
                  (win as Any? as WindowAccessor).`pge$getRealScaledHeight`()
          else 1.0f
      val exPx = Math.round(ex * framebufferScaleX)
      val eyPx = Math.round(ey * framebufferScaleY)
      val ewPx = max(1, Math.round(ew * framebufferScaleX))
      val ehPx = max(1, Math.round(eh * framebufferScaleY))
      val mx = mc.mouse.x.toInt()
      val my = mc.mouse.y.toInt()

      if (mx >= exPx && mx < exPx + ewPx && my >= eyPx && my < eyPx + ehPx) {
        focused = true
        LOGGER.info("terminal mouse focus acquired at {},{}", mx - exPx, my - eyPx)
        term.focus()
        term.onMouse(mx - exPx, my - eyPx, b, a, m, ewPx, ehPx)
        return true
      }
      if (focused) {
        LOGGER.info("terminal mouse focus lost")
      }
      focused = false
      return false
    }
    val s =
        (win as Any? as WindowAccessor).`pge$getRealFramebufferWidth`().toDouble() /
            (win as Any? as WindowAccessor).`pge$getRealScaledWidth`()
    val mx = (mc.mouse.x / s).toInt()
    val my = (mc.mouse.y / s).toInt()

    if (mx >= ex && mx < ex + ew && my >= ey && my < ey + eh) {
      focused = true
      if (EditorManager.renderMode == EditorManager.RenderMode.TERMINAL) {
        LOGGER.info("terminal mouse focus acquired at {},{}", mx - ex, my - ey)
        term.focus()
        term.onMouse(mx - ex, my - ey, b, a, m, ew, eh)
      } else if (EditorManager.renderMode == EditorManager.RenderMode.GIZMO_GRID) {
        glyphGrid.onMouse(mx - ex, my - ey, b, a, m, ew, eh)
      } else {
        miniGame.onMouse(mx - ex, my - ey, b, a, m, ew, eh)
      }
      return true
    }
    if (focused) {
      LOGGER.info("terminal mouse focus lost")
    }
    focused = false
    return false
  }

  fun onMove(x: Double, y: Double) {
    if (!EditorManager.isEnabled) return
    val mc = MinecraftClient.getInstance()
    val win = mc.window
    if (EditorManager.renderMode == EditorManager.RenderMode.TERMINAL) {
      val framebufferScaleX =
          if ((win as Any? as WindowAccessor).`pge$getRealScaledWidth`() > 0)
              (win as Any? as WindowAccessor).`pge$getRealFramebufferWidth`().toFloat() /
                  (win as Any? as WindowAccessor).`pge$getRealScaledWidth`()
          else 1.0f
      val framebufferScaleY =
          if ((win as Any? as WindowAccessor).`pge$getRealScaledHeight`() > 0)
              (win as Any? as WindowAccessor).`pge$getRealFramebufferHeight`().toFloat() /
                  (win as Any? as WindowAccessor).`pge$getRealScaledHeight`()
          else 1.0f
      val exPx = Math.round(ex * framebufferScaleX)
      val eyPx = Math.round(ey * framebufferScaleY)
      val ewPx = max(1, Math.round(ew * framebufferScaleX))
      val ehPx = max(1, Math.round(eh * framebufferScaleY))
      val mx = x.toInt()
      val my = y.toInt()
      if (mx >= exPx && mx < exPx + ewPx && my >= eyPx && my < eyPx + ehPx) {
        term.onMove(mx - exPx, my - eyPx, ewPx, ehPx)
      }
      return
    }
    val s =
        (win as Any? as WindowAccessor).`pge$getRealFramebufferWidth`().toDouble() /
            (win as Any? as WindowAccessor).`pge$getRealScaledWidth`()
    val mx = (x / s).toInt()
    val my = (y / s).toInt()
    if (mx >= ex && mx < ex + ew && my >= ey && my < ey + eh) {
      if (EditorManager.renderMode == EditorManager.RenderMode.TERMINAL) {
        term.onMove(mx - ex, my - ey, ew, eh)
      } else if (EditorManager.renderMode == EditorManager.RenderMode.GIZMO_GRID) {
        glyphGrid.onMove(mx - ex, my - ey, ew, eh)
      } else {
        miniGame.onMove(mx - ex, my - ey, ew, eh)
      }
    }
  }

  fun onScroll(horizontalAmount: Double, verticalAmount: Double): Boolean {
    if (!EditorManager.isEnabled) return false
    if (EditorManager.renderMode != EditorManager.RenderMode.TERMINAL) return false

    val mc = MinecraftClient.getInstance()
    val win = mc.window
    val framebufferScaleX =
        if ((win as Any? as WindowAccessor).`pge$getRealScaledWidth`() > 0)
            (win as Any? as WindowAccessor).`pge$getRealFramebufferWidth`().toFloat() /
                (win as Any? as WindowAccessor).`pge$getRealScaledWidth`()
        else 1.0f
    val framebufferScaleY =
        if ((win as Any? as WindowAccessor).`pge$getRealScaledHeight`() > 0)
            (win as Any? as WindowAccessor).`pge$getRealFramebufferHeight`().toFloat() /
                (win as Any? as WindowAccessor).`pge$getRealScaledHeight`()
        else 1.0f
    val exPx = Math.round(ex * framebufferScaleX)
    val eyPx = Math.round(ey * framebufferScaleY)
    val ewPx = max(1, Math.round(ew * framebufferScaleX))
    val ehPx = max(1, Math.round(eh * framebufferScaleY))
    val mx = mc.mouse.x.toInt()
    val my = mc.mouse.y.toInt()

    if (mx >= exPx && mx < exPx + ewPx && my >= eyPx && my < eyPx + ehPx) {
      return term.onScroll(mx - exPx, my - eyPx, horizontalAmount, verticalAmount, 0, ewPx, ehPx)
    }
    return false
  }

  fun onKey(k: Int, a: Int, m: Int): Boolean {
    if (!EditorManager.isEnabled) return false
    if (k == GLFW.GLFW_KEY_BACKSLASH) return false
    val terminalGameplayCapture =
        EditorManager.renderMode == EditorManager.RenderMode.TERMINAL &&
            MinecraftClient.getInstance().currentScreen == null
    if (focused || terminalGameplayCapture) {
      if (EditorManager.renderMode == EditorManager.RenderMode.TERMINAL) {
        LOGGER.info("terminal key key={} action={} mods={}", k, a, m)
        term.onKey(k, a, m)
        return true
      } else if (EditorManager.renderMode == EditorManager.RenderMode.GIZMO_GRID) {
        return glyphGrid.onKey(k, a)
      } else {
        return miniGame.onKey(k, a)
      }
    }
    return false
  }

  fun onChar(c: Int): Boolean {
    val terminalGameplayCapture =
        EditorManager.isEnabled &&
            EditorManager.renderMode == EditorManager.RenderMode.TERMINAL &&
            MinecraftClient.getInstance().currentScreen == null
    if (EditorManager.isEnabled &&
        EditorManager.renderMode == EditorManager.RenderMode.TERMINAL &&
        (focused || terminalGameplayCapture)) {
      LOGGER.info("terminal char codepoint={}", c)
      term.onChar(c)
      return true
    }
    return false
  }
  private val LOGGER: Logger = LoggerFactory.getLogger("pge-editor/ui")
}

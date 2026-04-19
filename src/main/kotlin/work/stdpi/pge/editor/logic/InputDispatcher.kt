package work.stdpi.pge.editor.logic

import org.lwjgl.glfw.GLFW
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

object InputDispatcher {
  fun dispatchMouse(widget: Component?, x: Int, y: Int, button: Int, action: Int, mods: Int) {
    if (widget == null) return
    val type =
        if (action == GLFW.GLFW_PRESS) MouseEvent.MOUSE_PRESSED else MouseEvent.MOUSE_RELEASED
    val awtBtn =
        when (button) {
          GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseEvent.BUTTON1
          GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseEvent.BUTTON3
          GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseEvent.BUTTON2
          else -> MouseEvent.NOBUTTON
        }

    val ev =
        MouseEvent(widget, type, System.currentTimeMillis(), getMods(mods), x, y, 1, false, awtBtn)
    SwingUtilities.invokeLater(
        Runnable {
          widget.requestFocusInWindow()
          widget.dispatchEvent(ev)
          if (action == GLFW.GLFW_RELEASE) {
            widget.dispatchEvent(
                MouseEvent(
                    widget,
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    getMods(mods),
                    x,
                    y,
                    1,
                    false,
                    awtBtn))
          }
        })
  }

  fun dispatchMove(widget: Component?, x: Int, y: Int) {
    if (widget == null) return
    val ev =
        MouseEvent(
            widget,
            MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),
            0,
            x,
            y,
            0,
            false,
            MouseEvent.NOBUTTON)
    SwingUtilities.invokeLater(Runnable { widget.dispatchEvent(ev) })
  }

  fun dispatchKey(widget: Component?, key: Int, action: Int, mods: Int) {
    if (widget == null) return
    val type = if (action == GLFW.GLFW_RELEASE) KeyEvent.KEY_RELEASED else KeyEvent.KEY_PRESSED
    val awtKey = translate(key)
    if (awtKey == KeyEvent.VK_UNDEFINED) return
    val ev =
        KeyEvent(
            widget,
            type,
            System.currentTimeMillis(),
            getMods(mods),
            awtKey,
            KeyEvent.CHAR_UNDEFINED)
    SwingUtilities.invokeLater(
        Runnable {
          widget.requestFocusInWindow()
          widget.dispatchEvent(ev)
        })
  }

  fun dispatchChar(widget: Component?, codepoint: Int) {
    if (widget == null) return
    val ev =
        KeyEvent(
            widget,
            KeyEvent.KEY_TYPED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_UNDEFINED,
            codepoint.toChar())
    SwingUtilities.invokeLater(
        Runnable {
          widget.requestFocusInWindow()
          widget.dispatchEvent(ev)
        })
  }

  private fun getMods(mods: Int): Int {
    var m = 0
    if ((mods and GLFW.GLFW_MOD_SHIFT) != 0) m = m or KeyEvent.SHIFT_DOWN_MASK
    if ((mods and GLFW.GLFW_MOD_CONTROL) != 0) m = m or KeyEvent.CTRL_DOWN_MASK
    if ((mods and GLFW.GLFW_MOD_ALT) != 0) m = m or KeyEvent.ALT_DOWN_MASK
    return m
  }

  private fun translate(k: Int): Int {
    return when (k) {
      GLFW.GLFW_KEY_ENTER -> KeyEvent.VK_ENTER
      GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE
      GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB
      GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE
      GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP
      GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN
      GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT
      GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT
      else ->
          if ((k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z) ||
              (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9))
              k
          else KeyEvent.VK_UNDEFINED
    }
  }
}

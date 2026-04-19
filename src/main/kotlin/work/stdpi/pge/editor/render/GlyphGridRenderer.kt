package work.stdpi.pge.editor.render

import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

class GlyphGridRenderer {
  private val atlas = MonoGlyphAtlas()
  private var cursorCol = 3
  private var cursorRow = 2
  private val startedAt = System.nanoTime()

  fun render(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
    val cellWidth = atlas.cellWidth
    val cellHeight = atlas.cellHeight
    val cols = max(1, (width - PADDING_X * 2) / cellWidth)
    val rows = max(1, (height - PADDING_Y * 2) / cellHeight)

    context.fill(x, y, x + width, y + height, -0xf4f0ec)
    drawSampleBuffer(context, x, y, cols, rows, cellHeight)
    drawCursor(context, x, y, cols, rows, cellWidth, cellHeight)
  }

  fun onMouse(
      localX: Int,
      localY: Int,
      button: Int,
      action: Int,
      mods: Int,
      width: Int,
      height: Int
  ): Boolean {
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
      moveCursorTo(localX, localY, width, height)
      return true
    }
    return true
  }

  fun onMove(localX: Int, localY: Int, width: Int, height: Int) {}

  fun onKey(key: Int, action: Int): Boolean {
    if (action == GLFW.GLFW_RELEASE) {
      return false
    }

    when (key) {
      GLFW.GLFW_KEY_LEFT,
      GLFW.GLFW_KEY_A -> cursorCol = max(0, cursorCol - 1)
      GLFW.GLFW_KEY_RIGHT,
      GLFW.GLFW_KEY_D -> cursorCol += 1
      GLFW.GLFW_KEY_UP,
      GLFW.GLFW_KEY_W -> cursorRow = max(0, cursorRow - 1)
      GLFW.GLFW_KEY_DOWN,
      GLFW.GLFW_KEY_S -> cursorRow += 1
      GLFW.GLFW_KEY_HOME -> cursorCol = 0
      GLFW.GLFW_KEY_END -> cursorCol = 9999
      else -> {
        return false
      }
    }
    return true
  }

  private fun drawSampleBuffer(
      context: DrawContext,
      panelX: Int,
      panelY: Int,
      cols: Int,
      rows: Int,
      cellHeight: Int
  ) {
    val lines = buildLines(cols, rows)
    val visibleRows = min(rows, lines.size)

    for (row in 0..<visibleRows) {
      val line = lines[row]
      if (line.isEmpty()) {
        continue
      }

      val drawY: Int = panelY + PADDING_Y + row * cellHeight + atlas.baselineOffset
      if (row == rows - 1) {
        drawRun(context, panelX, drawY, line, 0, -0xe8ded5, -0x19120d, cellHeight)
      } else {
        drawRun(context, panelX, drawY, line, 0, -0xf4f0ec, -0x19120d, cellHeight)
      }
    }
  }

  private fun drawRun(
      context: DrawContext,
      panelX: Int,
      drawY: Int,
      text: String,
      startCol: Int,
      background: Int,
      foreground: Int,
      cellHeight: Int
  ) {
    val cellWidth = atlas.cellWidth
    val rx: Int = panelX + PADDING_X + startCol * cellWidth
    val ry = drawY
    context.fill(rx, ry, rx + text.length * cellWidth, ry + cellHeight, background)
    atlas.drawText(context, text, rx, drawY, foreground)
  }

  private fun drawCursor(
      context: DrawContext,
      x: Int,
      y: Int,
      cols: Int,
      rows: Int,
      cellWidth: Int,
      cellHeight: Int
  ) {
    cursorCol = clamp(cursorCol, 0, max(0, cols - 1))
    cursorRow = clamp(cursorRow, 0, max(0, rows - 1))

    val cx: Int = x + PADDING_X + cursorCol * cellWidth
    val cy: Int = y + PADDING_Y + cursorRow * cellHeight
    val blink = ((System.nanoTime() - startedAt) / 350000000L) % 2L
    val body = if (blink == 0L) -0x33764b06 else -0x77764b06
    context.fill(cx, cy, cx + cellWidth, cy + cellHeight, body)
  }

  private fun moveCursorTo(localX: Int, localY: Int, width: Int, height: Int) {
    val cellWidth = atlas.cellWidth
    val cellHeight = atlas.cellHeight
    cursorCol =
        clamp((localX - PADDING_X) / cellWidth, 0, max(0, (width - PADDING_X * 2) / cellWidth - 1))
    cursorRow =
        clamp(
            (localY - PADDING_Y) / cellHeight, 0, max(0, (height - PADDING_Y * 2) / cellHeight - 1))
  }

  private fun buildLines(cols: Int, rows: Int): Array<String> {
    val lines = Array(max(rows, 12)) { "" }
    lines[0] = padTo("  1  fn render_terminal(buffer: &Grid, viewport: Rect) {", cols)
    lines[1] = padTo("  2      draw_background_runs(buffer, viewport);", cols)
    lines[2] = padTo("  3      draw_glyph_runs(buffer, viewport);", cols)
    lines[3] = padTo("  4      draw_cursor(buffer.cursor());", cols)
    lines[4] = padTo("  5  }", cols)
    lines[6] = padTo("  7  let status = \"NORMAL  /src/render/term.rs\";", cols)
    lines[7] = padTo("  8  let note   = \"one quad per glyph, but batched\";", cols)
    lines[9] = padTo(" 10  ascii  !\"#$%&'()*+,-./0123456789:;<=>?", cols)
    lines[10] = padTo(" 11  alpha  abcdefghijklmnopqrstuvwxyz", cols)
    lines[11] = padTo(" 12  box    ─ │ ┌ ┐ └ ┘ ├ ┤ ┬ ┴ ┼", cols)
    if (rows > 0) {
      lines[min(rows - 1, lines.size - 1)] = padTo(" NORMAL  glyph-atlas-demo", cols)
    }
    return lines
  }

  private fun padTo(value: String, cols: Int): String {
    if (value.length >= cols) {
      return value.substring(0, cols)
    }
    return value
  }

  private fun clamp(value: Int, min: Int, max: Int): Int {
    return max(min, min(max, value))
  }

  companion object {
    private const val PADDING_X = 2
    private const val PADDING_Y = 2
  }
}

package work.stdpi.pge.editor.render

import com.jediterm.core.Color
import com.jediterm.core.input.InputEvent
import com.jediterm.core.input.KeyEvent
import com.jediterm.core.input.MouseEvent
import com.jediterm.core.input.MouseWheelEvent
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.typeahead.TypeAheadTerminalModel
import com.jediterm.core.typeahead.TypeAheadTerminalModel.LineWithCursorX
import com.jediterm.core.typeahead.TypeAheadTerminalModel.ShellType
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.emulator.ColorPaletteImpl
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.*
import com.jediterm.terminal.ui.JediTermExecutorServiceManager
import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.stdpi.pge.editor.logic.EditorManager
import work.stdpi.pge.editor.logic.EditorManager.TerminalFontWeight
import work.stdpi.pge.editor.logic.EditorManager.TerminalSupersample
import work.stdpi.pge.editor.terminal.ITerminalHandle
import work.stdpi.pge.editor.terminal.ITerminalIo
import java.io.IOException
import java.util.BitSet
import kotlin.math.max
import kotlin.math.min

class TerminalRenderer(
    private val terminalIo: ITerminalIo
) {
  private val atlas = MonoGlyphAtlas()
  private val palette: ColorPalette = ColorPaletteImpl.XTERM_PALETTE
  private val display = NativeDisplay()

  private var textBuffer: TerminalTextBuffer? = null
  private var terminal: JediTerminal? = null
  private var starter: TerminalStarter? = null
  private var terminalHandle: ITerminalHandle? = null
  private var executorServiceManager: TerminalExecutorServiceManager? = null

  private var pixelWidth = 0
  private var pixelHeight = 0
  private var columns = 0
  private var rows = 0
  private var appliedCellWidthPx = -1
  private var appliedFontWeight: TerminalFontWeight? = null
  private var appliedSupersample: TerminalSupersample? = null
  private var activeMouseButton = MouseButtonCodes.NONE
  private var pendingTerminalRefresh = false
  private var initialized = false
  private val rowCacheLock = Any()
  private var rowPlans: Array<RowPlan?> = arrayOfNulls<RowPlan>(0)
  private val dirtyRows = BitSet()
  private var allRowsDirty = true

  fun render(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
    syncAtlasSettings()
    ensureInitialized(width, height)
    resizeIfNeeded(width, height)

    context.fill(x, y, x + width, y + height, DEFAULT_BG)
    val buffer = textBuffer ?: return
    if (!initialized) {
      return
    }

    val cellWidth = atlas.cellWidth
    val cellHeight = atlas.cellHeight
    buffer.lock()
    try {
      rebuildDirtyRowPlans()
    } finally {
      buffer.unlock()
    }

    val plans = rowPlans
    for (row in 0..<min(rows, plans.size)) {
      drawRowPlan(context, x, y, row, plans[row], cellWidth, cellHeight)
    }
    drawCursor(context, x, y, cellWidth, cellHeight)
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
    ensureInitialized(width, height)
    resizeIfNeeded(width, height)

    val col = clamp((localX - PADDING_X) / atlas.cellWidth, 0, max(0, columns - 1))
    val row = clamp((localY - PADDING_Y) / atlas.cellHeight, 0, max(0, rows - 1))
    val modifiers = toMouseModifiers(mods)

    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT ||
        button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ||
        button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
      val mappedButton = toMouseButton(button)
      if (action == GLFW.GLFW_PRESS) {
        activeMouseButton = mappedButton
        terminal?.mousePressed(col, row, MouseEvent(mappedButton, modifiers))
      } else if (action == GLFW.GLFW_RELEASE) {
        terminal?.mouseReleased(col, row, MouseEvent(mappedButton, modifiers))
        if (activeMouseButton == mappedButton) {
          activeMouseButton = MouseButtonCodes.NONE
        }
      }
      return true
    }
    return false
  }

  fun onMove(localX: Int, localY: Int, width: Int, height: Int) {
    if (!initialized) {
      return
    }
    resizeIfNeeded(width, height)

    val col = clamp((localX - PADDING_X) / atlas.cellWidth, 0, max(0, columns - 1))
    val row = clamp((localY - PADDING_Y) / atlas.cellHeight, 0, max(0, rows - 1))
    val event = MouseEvent(activeMouseButton, 0)
    if (activeMouseButton == MouseButtonCodes.NONE) {
      terminal?.mouseMoved(col, row, event)
    } else {
      terminal?.mouseDragged(col, row, event)
    }
  }

  fun onScroll(
      localX: Int,
      localY: Int,
      horizontalAmount: Double,
      verticalAmount: Double,
      mods: Int,
      width: Int,
      height: Int
  ): Boolean {
    if (!initialized || verticalAmount == 0.0) {
      return false
    }
    resizeIfNeeded(width, height)

    val col = clamp((localX - PADDING_X) / atlas.cellWidth, 0, max(0, columns - 1))
    val row = clamp((localY - PADDING_Y) / atlas.cellHeight, 0, max(0, rows - 1))
    val modifiers = toMouseModifiers(mods)
    val wheelButton =
        if (verticalAmount > 0.0) MouseButtonCodes.SCROLLDOWN else MouseButtonCodes.SCROLLUP
    terminal?.mouseWheelMoved(col, row, MouseWheelEvent(wheelButton, modifiers))
    return true
  }

  fun onKey(key: Int, action: Int, mods: Int): Boolean {
    if (!initialized || action == GLFW.GLFW_RELEASE) {
      return false
    }

    if ((mods and GLFW.GLFW_MOD_CONTROL) != 0) {
      if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
        EditorManager.adjustTerminalCellWidthPx(1)
        syncAtlasSettings()
        resizeIfNeeded(pixelWidth, pixelHeight)
        return true
      }
      if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
        EditorManager.adjustTerminalCellWidthPx(-1)
        syncAtlasSettings()
        resizeIfNeeded(pixelWidth, pixelHeight)
        return true
      }
    }

    val translatedKey = toTerminalKey(key)
    val translatedModifiers = toKeyModifiers(mods)

    if (key == GLFW.GLFW_KEY_TAB) {
      if ((mods and GLFW.GLFW_MOD_SHIFT) != 0) {
        starter?.sendBytes(byteArrayOf(27, '['.code.toByte(), 'Z'.code.toByte()), true)
      } else {
        starter?.sendBytes(byteArrayOf('\t'.code.toByte()), true)
      }
      return true
    }

    if (translatedKey != -1) {
      val bytes = terminal?.getCodeForKey(translatedKey, translatedModifiers)
      if (bytes != null) {
        starter?.sendBytes(bytes, true)
        return true
      }
    }

    if ((mods and GLFW.GLFW_MOD_CONTROL) != 0 && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
      val ctrlChar = (key - GLFW.GLFW_KEY_A + 1).toChar()
      starter?.sendBytes(byteArrayOf(ctrlChar.code.toByte()), true)
      return true
    }

    return false
  }

  fun onChar(codepoint: Int): Boolean {
    if (!initialized) {
      return false
    }
    starter?.sendString(String(Character.toChars(codepoint)), true)
    return true
  }

  fun focus() {}

  private fun syncAtlasSettings() {
    val requestedCellWidth = EditorManager.terminalCellWidthPx
    if (requestedCellWidth != appliedCellWidthPx) {
      atlas.setCellWidthPx(requestedCellWidth)
      appliedCellWidthPx = requestedCellWidth
      pendingTerminalRefresh = initialized
    }
    val requestedFontWeight = EditorManager.terminalFontWeight
    if (requestedFontWeight != appliedFontWeight) {
      atlas.setFontWeight(requestedFontWeight)
      appliedFontWeight = requestedFontWeight
      pendingTerminalRefresh = initialized
    }
    val requestedSupersample = EditorManager.terminalSupersample
    if (requestedSupersample != appliedSupersample) {
      atlas.setSupersample(requestedSupersample.value)
      appliedSupersample = requestedSupersample
      pendingTerminalRefresh = initialized
    }
  }

  private fun ensureInitialized(width: Int, height: Int) {
    if (initialized) {
      return
    }

    pixelWidth = width
    pixelHeight = height
    columns = fitColumns(width)
    rows = fitRows(height)

    val styleState = StyleState()
    styleState.setDefaultStyle(
        TextStyle(
            TerminalColor.rgb(
                (DEFAULT_FG shr 16) and 0xFF, (DEFAULT_FG shr 8) and 0xFF, DEFAULT_FG and 0xFF),
            TerminalColor.rgb(
                (DEFAULT_BG shr 16) and 0xFF, (DEFAULT_BG shr 8) and 0xFF, DEFAULT_BG and 0xFF)))

    val buffer = TerminalTextBuffer(columns, rows, styleState)
    textBuffer = buffer
    rowPlans = arrayOfNulls<RowPlan>(rows)
    markAllRowsDirty()
    buffer.addChangesListener(
        object : TextBufferChangesListener {
          override fun linesChanged(fromIndex: Int) {
            markRowsDirty(max(0, fromIndex), rows)
          }

          override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {}

          override fun historyCleared() {
            markAllRowsDirty()
          }

          override fun widthResized() {
            markAllRowsDirty()
          }
        })
    val createdTerminal = JediTerminal(display, buffer, styleState)
    terminal = createdTerminal
    val executorManager = JediTermExecutorServiceManager()
    executorServiceManager = executorManager

    try {
      val openedHandle = terminalIo.open(columns, rows)
      terminalHandle = openedHandle
      val typeAheadModel = NoOpTypeAheadModel(createdTerminal, buffer)
      val typeAheadManager = TerminalTypeAheadManager(typeAheadModel)
      val createdStarter =
          TerminalStarter(
              createdTerminal,
              openedHandle.ttyConnector,
              TtyBasedArrayDataStream(
                  openedHandle.ttyConnector, Runnable { typeAheadManager.onTerminalStateChanged() }),
              typeAheadManager,
              executorManager)
      starter = createdStarter
      executorManager.unboundedExecutorService.submit(Runnable { createdStarter.start() })
      initialized = true
      LOGGER.info(
          "initialized native terminal {}x{} cells for {}x{} px", columns, rows, width, height)
    } catch (e: IOException) {
      LOGGER.error("failed to start native terminal backend", e)
    }
  }

  private fun resizeIfNeeded(width: Int, height: Int) {
    if (!initialized) {
      return
    }

    val newColumns = fitColumns(width)
    val newRows = fitRows(height)
    if (width == pixelWidth &&
        height == pixelHeight &&
        newColumns == columns &&
        newRows == rows &&
        !pendingTerminalRefresh) {
      return
    }

    pixelWidth = width
    pixelHeight = height
    columns = newColumns
    rows = newRows
    rowPlans = arrayOfNulls<RowPlan>(rows)
    markAllRowsDirty()
    terminalHandle?.resize(columns, rows)
    starter?.postResize(TermSize(columns, rows), RequestOrigin.User)
    pendingTerminalRefresh = false
    LOGGER.info("resized native terminal to {}x{} cells for {}x{} px", columns, rows, width, height)
  }

  private fun drawRowPlan(
      context: DrawContext,
      x: Int,
      y: Int,
      row: Int,
      rowPlan: RowPlan?,
      cellWidth: Int,
      cellHeight: Int
  ) {
    if (rowPlan == null) {
      return
    }
    for (run in rowPlan.runs) {
      drawRun(
          context,
          x,
          y,
          row,
          run.startCol,
          run.text,
          run.foreground,
          run.background,
          cellWidth,
          cellHeight)
    }
  }

  private fun buildRowPlan(row: Int): RowPlan {
    var startCol = 0
    var runForeground: Int = DEFAULT_FG
    var runBackground: Int = DEFAULT_BG
    val builder = StringBuilder(columns)
    val runs = ArrayList<RunPlan>()

    for (col in 0..<columns) {
      val ch = getSafeCharAt(col, row)
      val style = getSafeStyleAt(col, row)

      if (builder.isEmpty()) {
        startCol = col
        runForeground = style.foreground
        runBackground = style.background
      } else if (runForeground != style.foreground || runBackground != style.background) {
        runs.add(RunPlan(startCol, builder.toString(), runForeground, runBackground))
        builder.setLength(0)
        startCol = col
        runForeground = style.foreground
        runBackground = style.background
      }

      builder.append(ch)
    }

    if (!builder.isEmpty()) {
      runs.add(RunPlan(startCol, builder.toString(), runForeground, runBackground))
    }
    return RowPlan(runs)
  }

  private fun drawRun(
      context: DrawContext,
      x: Int,
      y: Int,
      row: Int,
      startCol: Int,
      text: String,
      foreground: Int,
      background: Int,
      cellWidth: Int,
      cellHeight: Int
  ) {
    val drawX: Int = x + PADDING_X + startCol * cellWidth
    val drawY: Int = y + PADDING_Y + row * cellHeight
    context.fill(drawX, drawY, drawX + text.length * cellWidth, drawY + cellHeight, background)

    val visibleLength = trimTrailingSpaces(text)
    if (visibleLength > 0) {
      atlas.drawText(context, text.substring(0, visibleLength), drawX, drawY, foreground)
    }
  }

  private fun drawCursor(context: DrawContext, x: Int, y: Int, cellWidth: Int, cellHeight: Int) {
    if (!display.curVis || rows <= 0 || columns <= 0) {
      return
    }

    val shape = display.curShape ?: CursorShape.STEADY_BLOCK
    if (shape.isBlinking && ((System.nanoTime() / 350000000L) % 2L) == 0L) {
      return
    }

    val cursorCol = clamp(display.curX, 0, max(0, columns - 1))
    val cursorRow = clamp(display.curY - 1, 0, max(0, rows - 1))
    val drawX: Int = x + PADDING_X + cursorCol * cellWidth
    val drawY: Int = y + PADDING_Y + cursorRow * cellHeight

    when (shape) {
      CursorShape.BLINK_VERTICAL_BAR,
      CursorShape.STEADY_VERTICAL_BAR -> {
        val barWidth = max(1, cellWidth / 6)
        context.fill(drawX, drawY, drawX + barWidth, drawY + cellHeight, CURSOR_ACCENT)
      }
      CursorShape.BLINK_UNDERLINE,
      CursorShape.STEADY_UNDERLINE ->
          context.fill(
              drawX, drawY + cellHeight - 2, drawX + cellWidth, drawY + cellHeight, CURSOR_ACCENT)
      CursorShape.BLINK_BLOCK,
      CursorShape.STEADY_BLOCK -> {
        val glyph = getSafeCharAt(cursorCol, cursorRow)
        val style = getSafeStyleAt(cursorCol, cursorRow)
        context.fill(drawX, drawY, drawX + cellWidth, drawY + cellHeight, CURSOR_ACCENT)
        if (glyph != ' ') {
          atlas.drawText(context, glyph.toString(), drawX, drawY, style.background)
        }
      }
    }
  }

  private fun getSafeCharAt(col: Int, row: Int): Char {
    try {
      return normalizeGlyph(textBuffer?.getCharAt(col, row) ?: ' ')
    } catch (ignored: RuntimeException) {
      return ' '
    }
  }

  private fun getSafeStyleAt(col: Int, row: Int): CellStyle {
    try {
      return resolveStyle(textBuffer?.getStyleAt(col, row))
    } catch (ignored: RuntimeException) {
      return CellStyle(DEFAULT_FG, DEFAULT_BG)
    }
  }

  private fun resolveStyle(style: TextStyle?): CellStyle {
    var foreground: Int = DEFAULT_FG
    var background: Int = DEFAULT_BG

    if (style != null) {
      var fgColor = style.foreground
      val bgColor = style.background

      if (style.hasOption(TextStyle.Option.BOLD) &&
          fgColor != null &&
          fgColor.isIndexed &&
          fgColor.colorIndex < 8) {
        fgColor = TerminalColor.index(fgColor.colorIndex + 8)
      }

      if (fgColor != null) {
        foreground = toArgb(palette.getForeground(fgColor))
      }
      if (bgColor != null) {
        background = toArgb(palette.getBackground(bgColor))
      }
      if (style.hasOption(TextStyle.Option.INVERSE)) {
        val tmp = foreground
        foreground = background
        background = tmp
      }
      if (style.hasOption(TextStyle.Option.HIDDEN)) {
        foreground = background
      }
    }

    return CellStyle(foreground, background)
  }

  private fun normalizeGlyph(ch: Char): Char {
    if (ch.code == 0 || ch == '\uE000' || Character.isISOControl(ch)) {
      return ' '
    }
    return ch
  }

  private fun trimTrailingSpaces(text: String): Int {
    var end = text.length
    while (end > 0 && text.get(end - 1) == ' ') {
      end--
    }
    return end
  }

  private fun fitColumns(width: Int): Int {
    return max(5, (width - PADDING_X * 2) / atlas.cellWidth)
  }

  private fun fitRows(height: Int): Int {
    return max(2, (height - PADDING_Y * 2) / atlas.cellHeight)
  }

  private fun toMouseButton(glfwButton: Int): Int {
    return when (glfwButton) {
      GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButtonCodes.LEFT
      GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButtonCodes.MIDDLE
      GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButtonCodes.RIGHT
      else -> MouseButtonCodes.NONE
    }
  }

  private fun toMouseModifiers(mods: Int): Int {
    return MOUSE_MODIFIER_FLAGS.fold(0) { acc, (glfwMask, terminalMask) ->
      if ((mods and glfwMask) != 0) acc or terminalMask else acc
    }
  }

  private fun toKeyModifiers(mods: Int): Int {
    return KEY_MODIFIER_FLAGS.fold(0) { acc, (glfwMask, terminalMask) ->
      if ((mods and glfwMask) != 0) acc or terminalMask else acc
    }
  }

  private fun toTerminalKey(key: Int): Int {
    return when (key) {
      GLFW.GLFW_KEY_ENTER,
      GLFW.GLFW_KEY_KP_ENTER -> KeyEvent.VK_ENTER
      GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE
      GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB
      GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE
      GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP
      GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN
      GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT
      GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT
      GLFW.GLFW_KEY_HOME -> KeyEvent.VK_HOME
      GLFW.GLFW_KEY_END -> KeyEvent.VK_END
      GLFW.GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP
      GLFW.GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN
      GLFW.GLFW_KEY_INSERT -> KeyEvent.VK_INSERT
      GLFW.GLFW_KEY_DELETE -> KeyEvent.VK_DELETE
      GLFW.GLFW_KEY_F1 -> KeyEvent.VK_F1
      GLFW.GLFW_KEY_F2 -> KeyEvent.VK_F2
      GLFW.GLFW_KEY_F3 -> KeyEvent.VK_F3
      GLFW.GLFW_KEY_F4 -> KeyEvent.VK_F4
      GLFW.GLFW_KEY_F5 -> KeyEvent.VK_F5
      GLFW.GLFW_KEY_F6 -> KeyEvent.VK_F6
      GLFW.GLFW_KEY_F7 -> KeyEvent.VK_F7
      GLFW.GLFW_KEY_F8 -> KeyEvent.VK_F8
      GLFW.GLFW_KEY_F9 -> KeyEvent.VK_F9
      GLFW.GLFW_KEY_F10 -> KeyEvent.VK_F10
      GLFW.GLFW_KEY_F11 -> KeyEvent.VK_F11
      GLFW.GLFW_KEY_F12 -> KeyEvent.VK_F12
      else -> -1
    }
  }

  private fun toArgb(color: Color): Int {
    return color.rgb
  }

  private fun clamp(value: Int, min: Int, max: Int): Int {
    return max(min, min(max, value))
  }

  private fun rebuildDirtyRowPlans() {
    if (rowPlans.size != rows) {
      rowPlans = arrayOfNulls<RowPlan>(rows)
      markAllRowsDirty()
    }

    val dirtySnapshot: BitSet
    synchronized(rowCacheLock) {
      if (allRowsDirty) {
        dirtySnapshot = BitSet(rows)
        dirtySnapshot.set(0, rows)
        allRowsDirty = false
        dirtyRows.clear()
      } else if (dirtyRows.isEmpty) {
        return
      } else {
        dirtySnapshot = dirtyRows.get(0, rows)
        dirtyRows.clear()
      }
    }

    var row = dirtySnapshot.nextSetBit(0)
    while (row >= 0) {
      if (row >= rows) {
        break
      }
      rowPlans[row] = buildRowPlan(row)
      row = dirtySnapshot.nextSetBit(row + 1)
    }
  }

  private fun markRowsDirty(fromInclusive: Int, toExclusive: Int) {
    synchronized(rowCacheLock) {
      dirtyRows.set(max(0, fromInclusive), max(max(0, fromInclusive), toExclusive))
    }
  }

  private fun markAllRowsDirty() {
    synchronized(rowCacheLock) {
      allRowsDirty = true
      dirtyRows.clear()
    }
  }

  @JvmRecord private data class CellStyle(val foreground: Int, val background: Int)

  @JvmRecord
  private data class RunPlan(val startCol: Int, val text: String, val foreground: Int, val background: Int)

  @JvmRecord private data class RowPlan(val runs: List<RunPlan>)

  private class NativeDisplay : TerminalDisplay {
    var curX = 0
    var curY = 1
    var curVis = true
    var curShape: CursorShape? = null
    private var title = "PGE"
    private var mouseMode = MouseMode.MOUSE_REPORTING_NONE
    private var mouseFormat = MouseFormat.MOUSE_FORMAT_XTERM

    override fun setCursor(x: Int, y: Int) {
      curX = x
      curY = y
    }

    override fun setCursorShape(cursorShape: CursorShape?) {
      this.curShape = cursorShape
    }

    override fun beep() {}

    override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) {}

    override fun setCursorVisible(isCursorVisible: Boolean) {
      this.curVis = isCursorVisible
    }

    override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) {}

    override fun getWindowTitle(): String {
      return title
    }

    override fun setWindowTitle(windowTitle: String) {
      title = windowTitle
    }

    override fun getSelection(): TerminalSelection? {
      return null
    }

    override fun terminalMouseModeSet(mouseMode: MouseMode) {
      this.mouseMode = mouseMode
    }

    override fun setMouseFormat(mouseFormat: MouseFormat) {
      this.mouseFormat = mouseFormat
    }

    override fun ambiguousCharsAreDoubleWidth(): Boolean {
      return false
    }
  }

  private class NoOpTypeAheadModel(
      private val terminal: JediTerminal,
      private val buffer: TerminalTextBuffer
  ) : TypeAheadTerminalModel {
    override fun insertCharacter(ch: Char, index: Int) {}

    override fun removeCharacters(from: Int, count: Int) {}

    override fun moveCursor(index: Int) {}

    override fun forceRedraw() {}

    override fun clearPredictions() {}

    override fun lock() {
      buffer.lock()
    }

    override fun unlock() {
      buffer.unlock()
    }

    override fun isUsingAlternateBuffer(): Boolean {
      return buffer.isUsingAlternateBuffer
    }

    override fun getCurrentLineWithCursor(): LineWithCursorX {
      return LineWithCursorX(
          StringBuffer(buffer.getLine(terminal.cursorY - 1).getText()),
          terminal.cursorX - 1)
    }

    override fun getTerminalWidth(): Int {
      return terminal.terminalWidth
    }

    override fun isTypeAheadEnabled(): Boolean {
      return false
    }

    override fun getLatencyThreshold(): Long {
      return TerminalTypeAheadSettings.DEFAULT.latencyThreshold
    }

    override fun getShellType(): ShellType {
      return ShellType.Unknown
    }
  }

  companion object {
    private val LOGGER: Logger = LoggerFactory.getLogger("pge-editor/native-terminal")
    private val MOUSE_MODIFIER_FLAGS =
        arrayOf(
            GLFW.GLFW_MOD_SHIFT to MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG,
            GLFW.GLFW_MOD_CONTROL to MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG,
            GLFW.GLFW_MOD_ALT to MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG)
    private val KEY_MODIFIER_FLAGS =
        arrayOf(
            GLFW.GLFW_MOD_SHIFT to InputEvent.SHIFT_MASK,
            GLFW.GLFW_MOD_CONTROL to InputEvent.CTRL_MASK,
            GLFW.GLFW_MOD_ALT to InputEvent.ALT_MASK,
            GLFW.GLFW_MOD_SUPER to InputEvent.META_MASK)
    private const val PADDING_X = 0
    private const val PADDING_Y = 0
    private const val DEFAULT_BG = -0xf4f0ec
    private const val DEFAULT_FG = -0x19120d
    private const val CURSOR_ACCENT = -0x754b08
  }
}

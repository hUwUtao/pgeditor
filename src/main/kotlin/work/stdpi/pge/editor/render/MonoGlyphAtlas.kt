package work.stdpi.pge.editor.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import work.stdpi.pge.editor.logic.EditorManager.TerminalFontWeight
import java.awt.*
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.*
import java.util.function.Supplier
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class MonoGlyphAtlas {
  private val glyphs: MutableMap<Char?, Glyph> = HashMap<Char?, Glyph>()
  private val coloredTextures: MutableMap<Int?, ColoredTexture> = HashMap<Int?, ColoredTexture>()
  private var cellWidthPx = 4
  private var fontWeight = TerminalFontWeight.REGULAR
  private var supersample = 8
  private var cellHeightPx = 0
  private var rasterCellWidth = 0
  private var rasterCellHeight = 0
  private var textureWidth = 0
  private var textureHeight = 0
  private var alphaMask: BufferedImage? = null

  fun ensureReady() {
    if (alphaMask != null) {
      return
    }

    updateCellMetrics()

    val font = chooseRasterFont()
    val columns = 16
    val rows = ceil(GLYPHS.length / columns.toDouble()).toInt()
    val rasterTextureWidth = columns * rasterCellWidth
    val rasterTextureHeight = rows * rasterCellHeight

    val rasterMask =
        BufferedImage(rasterTextureWidth, rasterTextureHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = rasterMask.createGraphics()
      graphics.font = font
      graphics.color = Color.WHITE
    graphics.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
    graphics.setRenderingHint(
        RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    val metrics = graphics.fontMetrics
    val rasterBaseline = centerBaseline(metrics)
    for (i in 0..<GLYPHS.length) {
      val ch: Char = GLYPHS.get(i)
      val col = i % columns
      val row = i / columns
      val cellX = col * rasterCellWidth
      val cellY = row * rasterCellHeight
      val charWidth = metrics.charWidth(ch)
      val drawX = cellX + max(0, (rasterCellWidth - charWidth) / 2)
      val drawY = cellY + rasterBaseline
      graphics.drawString(ch.toString(), drawX, drawY)
    }

    graphics.dispose()

    textureWidth = columns * cellWidthPx
    textureHeight = rows * cellHeightPx
    alphaMask = BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB)
    val downsample = alphaMask!!.createGraphics()
    downsample.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    downsample.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    downsample.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    downsample.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
    downsample.setRenderingHint(
        RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
    downsample.drawImage(rasterMask, 0, 0, textureWidth, textureHeight, null)
    downsample.dispose()

    for (i in 0..<GLYPHS.length) {
      val col = i % columns
      val row = i / columns
      glyphs.put(GLYPHS.get(i), Glyph(col * cellWidthPx, row * cellHeightPx))
    }
  }

  fun setCellWidthPx(cellWidthPx: Int) {
    val clamped = max(1, min(24, cellWidthPx))
    if (this.cellWidthPx == clamped) {
      return
    }
    this.cellWidthPx = clamped
    invalidate()
  }

  fun setFontWeight(fontWeight: TerminalFontWeight?) {
    val target = if (fontWeight != null) fontWeight else TerminalFontWeight.REGULAR
    if (this.fontWeight == target) {
      return
    }
    this.fontWeight = target
    invalidate()
  }

  fun setSupersample(supersample: Int) {
    val clamped = max(1, min(16, supersample))
    if (this.supersample == clamped) {
      return
    }
    this.supersample = clamped
    invalidate()
  }

  fun drawText(context: DrawContext, text: String, x: Int, y: Int, color: Int) {
    ensureReady()
    val atlas =
        coloredTextures.computeIfAbsent(color) { color: Int? -> this.buildColoredTexture(color!!) }
    var i = 0
    while (i < text.length) {
      val ch = text.get(i)
      val drawX = x + i * cellWidthPx
      if (ch == ' ') {
        i++
        continue
      }
      if (ch == '█') {
        val start = i
        while (i < text.length && text.get(i) == '█') {
          i++
        }
        context.fill(x + start * cellWidthPx, y, x + i * cellWidthPx, y + cellHeightPx, color)
        continue
      }
      if (drawSpecialGlyph(context, ch, drawX, y, color)) {
        i++
        continue
      }
      val glyph = glyphs.getOrDefault(ch, glyphs.get(' '))
      context.drawTexture(
          RenderPipelines.GUI_TEXTURED,
          atlas.id,
          drawX,
          y,
          glyph?.u?.toFloat() ?: 0.0F,
          glyph?.v?.toFloat() ?: 0.0F,
          cellWidthPx,
          cellHeightPx,
          textureWidth,
          textureHeight)
      i++
    }
  }

  val cellWidth: Int
    get() {
      ensureReady()
      return cellWidthPx
    }

  val cellHeight: Int
    get() {
      ensureReady()
      return cellHeightPx
    }

  val baselineOffset: Int
    get() = 0

  private fun drawSpecialGlyph(
      context: DrawContext,
      ch: Char,
      x: Int,
      y: Int,
      color: Int
  ): Boolean {
    if (drawBoxGlyph(context, ch, x, y, color)) {
      return true
    }
    return drawBlockGlyph(context, ch, x, y, color)
  }

  private fun drawBoxGlyph(context: DrawContext, ch: Char, x: Int, y: Int, color: Int): Boolean {
    val left: Boolean
    val right: Boolean
    val up: Boolean
    val down: Boolean
    var thickness = max(1, Math.round(min(cellWidthPx, cellHeightPx) / 6.0f))

    when (ch) {
      '─',
      '═',
      '╴',
      '╶' -> {
        left = ch != '╶'
        right = ch != '╴'
        up = false
        down = false
      }
      '│',
      '║',
      '╵',
      '╷' -> {
        left = false
        right = false
        up = ch != '╵'
        down = ch != '╷'
      }
      '┌',
      '╔',
      '╭',
      '┏' -> {
        left = false
        right = true
        up = false
        down = true
      }
      '┐',
      '╗',
      '╮',
      '┓' -> {
        left = true
        right = false
        up = false
        down = true
      }
      '└',
      '╚',
      '╰',
      '┗' -> {
        left = false
        right = true
        up = true
        down = false
      }
      '┘',
      '╝',
      '╯',
      '┛' -> {
        left = true
        right = false
        up = true
        down = false
      }
      '├',
      '╠',
      '┠',
      '╞',
      '╟' -> {
        left = false
        right = true
        up = true
        down = true
      }
      '┤',
      '╣',
      '┨',
      '╡',
      '╢' -> {
        left = true
        right = false
        up = true
        down = true
      }
      '┬',
      '╦',
      '┯',
      '╤',
      '╥' -> {
        left = true
        right = true
        up = false
        down = true
      }
      '┴',
      '╩',
      '┷',
      '╧',
      '╨' -> {
        left = true
        right = true
        up = true
        down = false
      }
      '┼',
      '╬',
      '┿',
      '╪',
      '╫',
      '╋',
      '╼',
      '╽',
      '╾',
      '╿' -> {
        left = true
        right = true
        up = true
        down = true
      }
      '╸',
      '╺' -> {
        left = ch != '╺'
        right = ch != '╸'
        up = false
        down = false
        thickness = max(thickness, 2)
      }
      '╹',
      '╻' -> {
        left = false
        right = false
        up = ch != '╹'
        down = ch != '╻'
        thickness = max(thickness, 2)
      }
      else -> {
        return false
      }
    }

    val centerX = x + cellWidthPx / 2
    val centerY = y + cellHeightPx / 2
    val half = max(1, thickness / 2)

    if (left || right) {
      val lineY1 = centerY - half
      val lineY2 = lineY1 + thickness
      val lineX1 = if (left) x else centerX - half
      val lineX2 = if (right) x + cellWidthPx else centerX + half + 1
      context.fill(lineX1, lineY1, lineX2, lineY2, color)
    }
    if (up || down) {
      val lineX1 = centerX - half
      val lineX2 = lineX1 + thickness
      val lineY1 = if (up) y else centerY - half
      val lineY2 = if (down) y + cellHeightPx else centerY + half + 1
      context.fill(lineX1, lineY1, lineX2, lineY2, color)
    }
    return true
  }

  private fun drawBlockGlyph(context: DrawContext, ch: Char, x: Int, y: Int, color: Int): Boolean {
    when (ch) {
      '█' -> {
        context.fill(x, y, x + cellWidthPx, y + cellHeightPx, color)
        return true
      }
      '▀' -> {
        context.fill(x, y, x + cellWidthPx, y + max(1, cellHeightPx / 2), color)
        return true
      }
      '▁',
      '▂',
      '▃',
      '▄',
      '▅',
      '▆',
      '▇' -> {
        val level = "▁▂▃▄▅▆▇".indexOf(ch) + 1
        val blockHeight = max(1, Math.round(cellHeightPx * (level / 8.0f)))
        context.fill(x, y + cellHeightPx - blockHeight, x + cellWidthPx, y + cellHeightPx, color)
        return true
      }
      '▉',
      '▊',
      '▋',
      '▌',
      '▍',
      '▎',
      '▏' -> {
        val level = "▉▊▋▌▍▎▏".indexOf(ch)
        val ratio =
            when (level) {
              0 -> 7f / 8f
              1 -> 3f / 4f
              2 -> 5f / 8f
              3 -> 1f / 2f
              4 -> 3f / 8f
              5 -> 1f / 4f
              else -> 1f / 8f
            }
        val blockWidth = max(1, Math.round(cellWidthPx * ratio))
        context.fill(x, y, x + blockWidth, y + cellHeightPx, color)
        return true
      }
      '░',
      '▒',
      '▓' -> {
        val alpha =
            when (ch) {
              '░' -> 0x55
              '▒' -> 0x99
              else -> 0xCC
            }
        val shaded = (color and 0x00FFFFFF) or (alpha shl 24)
        context.fill(x, y, x + cellWidthPx, y + cellHeightPx, shaded)
        return true
      }
      else -> {
        return false
      }
    }
  }

  private fun updateCellMetrics() {
    cellHeightPx = max(3, Math.round(cellWidthPx * CELL_HEIGHT_RATIO))
    rasterCellWidth = cellWidthPx * supersample
    rasterCellHeight = cellHeightPx * supersample
  }

  private fun chooseRasterFont(): Font {
    val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val graphics = probe.createGraphics()
    graphics.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140)
    graphics.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    graphics.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
    graphics.setRenderingHint(
        RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)

    val usableWidth = max(1, rasterCellWidth - max(1, rasterCellWidth / 10))
    val usableHeight = max(1, rasterCellHeight - max(1, rasterCellHeight / 12))
    val base = getEmbeddedFont(fontWeight)
    var best = base.deriveFont(1f)

    for (size in 1..rasterCellHeight * 2) {
      val candidate = base.deriveFont(size.toFloat())
        graphics.font = candidate
      val metrics = graphics.fontMetrics
      if (maxGlyphWidth(metrics) <= usableWidth &&
          metrics.ascent + metrics.descent <= usableHeight) {
        best = candidate
      } else {
        break
      }
    }

    graphics.dispose()
    return best
  }

  private fun getEmbeddedFont(weight: TerminalFontWeight?): Font {
    val resolvedWeight = weight ?: TerminalFontWeight.REGULAR
    return EMBEDDED_FONTS.computeIfAbsent(resolvedWeight) { key ->
      val resource = FONT_RESOURCES[key] ?: FONT_RESOURCES.getValue(TerminalFontWeight.REGULAR)
      try {
        MonoGlyphAtlas::class.java.getResourceAsStream(resource).use { stream ->
          if (stream == null) {
            return@computeIfAbsent Font(Font.MONOSPACED, Font.PLAIN, 1)
          }
          return@computeIfAbsent Font.createFont(Font.TRUETYPE_FONT, stream)
        }
      } catch (e: FontFormatException) {
        return@computeIfAbsent Font(Font.MONOSPACED, Font.PLAIN, 1)
      } catch (e: IOException) {
        return@computeIfAbsent Font(Font.MONOSPACED, Font.PLAIN, 1)
      }
    }
  }

  private fun centerBaseline(metrics: FontMetrics): Int {
    val textHeight = metrics.ascent + metrics.descent
    val topPadding = max(0, (rasterCellHeight - textHeight) / 2)
    return topPadding + metrics.ascent
  }

  private fun maxGlyphWidth(metrics: FontMetrics): Int {
    var maxWidth = 0
    for (i in 0..<GLYPHS.length) {
      maxWidth = max(maxWidth, metrics.charWidth(GLYPHS.get(i)))
    }
    return maxWidth
  }

  private fun invalidate() {
    for (texture in coloredTextures.values) {
      texture.texture!!.close()
    }
    coloredTextures.clear()
    glyphs.clear()
    alphaMask = null
    textureWidth = 0
    textureHeight = 0
    cellHeightPx = 0
    rasterCellWidth = 0
    rasterCellHeight = 0
  }

  private fun buildColoredTexture(color: Int): ColoredTexture {
    val nativeImage = NativeImage(textureWidth, textureHeight, true)
    val alpha = (color shr 24) and 0xFF
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF

    for (y in 0..<textureHeight) {
      for (x in 0..<textureWidth) {
        val mask = alphaMask!!.getRGB(x, y)
        val glyphAlpha = (mask shr 24) and 0xFF
        val finalAlpha = glyphAlpha * alpha / 255
        nativeImage.setColor(x, y, (finalAlpha shl 24) or (blue shl 16) or (green shl 8) or red)
      }
    }

    val id = Identifier.of("pge-editor", "mono_glyph_atlas_" + Integer.toHexString(color))
    val texture = NativeImageBackedTexture(Supplier { "pge_mono_glyph_atlas" }, nativeImage)
    MinecraftClient.getInstance().textureManager.registerTexture(id, texture)
    return ColoredTexture(id, texture)
  }

  @JvmRecord private data class Glyph(val u: Int, val v: Int)

  @JvmRecord
  private data class ColoredTexture(val id: Identifier?, val texture: NativeImageBackedTexture?)

  companion object {
    private const val CELL_HEIGHT_RATIO = 1.9f
    private val GLYPHS =
        " " +
            "!\"#$%&'()*+,-./0123456789:;<=>?" +
            "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_" +
            "`abcdefghijklmnopqrstuvwxyz{|}~" +
            "─│┌┐└┘├┤┬┴┼" +
            "╭╮╰╯╴╵╶╷╸╹╺╻╼╽╾╿" +
            "═║╔╗╚╝╠╣╦╩╬╞╡╥╨╪╟╢╤╧╫╋" +
            "┏┓┗┛┠┨┯┷┿" +
            "█▇▆▅▄▃▂▁▀▉▊▋▌▍▎▏░▒▓"
    private val FONT_RESOURCES: Map<TerminalFontWeight, String> =
        mapOf(
            TerminalFontWeight.LIGHT to "/assets/pge-editor/fonts/IntelOneMono-Light.ttf",
            TerminalFontWeight.REGULAR to "/assets/pge-editor/fonts/IntelOneMono-Regular.ttf",
            TerminalFontWeight.MEDIUM to "/assets/pge-editor/fonts/IntelOneMono-Medium.ttf",
            TerminalFontWeight.BOLD to "/assets/pge-editor/fonts/IntelOneMono-Bold.ttf")
    private val EMBEDDED_FONTS: MutableMap<TerminalFontWeight, Font> = mutableMapOf()
  }
}

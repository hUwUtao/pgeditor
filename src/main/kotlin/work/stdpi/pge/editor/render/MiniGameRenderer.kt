package work.stdpi.pge.editor.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MiniGameRenderer {
  private var initialized = false
  private var leftHeld = false
  private var rightHeld = false
  private var paddleX = 0f
  private var paddleY = 0f
  private var ballX = 0f
  private var ballY = 0f
  private var ballVX = 0f
  private var ballVY = 0f
  private var lastFrameNanos: Long = 0
  private var score = 0
  private var lives = 3
  private val bricks = BooleanArray(BRICK_COLUMNS * BRICK_ROWS)
  private var mouseX = -1
  private var lastMouseMoveNanos: Long = 0

  fun render(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
    if (!initialized) {
      reset(width, height)
    }

    update(width, height)

    context.fill(x, y, x + width, y + height, -0xefece6)
    drawBackdrop(context, x, y, width, height)
    drawBricks(context, x, y, width)
    drawPaddle(context, x, y)
    drawBall(context, x, y)
    drawHud(context, x, y, width, height)
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
    mouseX = localX
    lastMouseMoveNanos = System.nanoTime()
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS && lives <= 0) {
      reset(width, height)
      return true
    }
    return true
  }

  fun onMove(localX: Int, localY: Int, width: Int, height: Int) {
    mouseX = localX
    lastMouseMoveNanos = System.nanoTime()
    val minX = HUD_MARGIN.toFloat()
    val maxX = (width - HUD_MARGIN - PADDLE_WIDTH).toFloat()
    paddleX = clamp(localX - (PADDLE_WIDTH / 2f), minX, maxX)
    paddleY = (height - 26).toFloat()
  }

  fun onKey(key: Int, action: Int): Boolean {
    val pressed = action != GLFW.GLFW_RELEASE
    when (key) {
      GLFW.GLFW_KEY_A,
      GLFW.GLFW_KEY_LEFT -> {
        leftHeld = pressed
        if (pressed) mouseX = -1
      }
      GLFW.GLFW_KEY_D,
      GLFW.GLFW_KEY_RIGHT -> {
        rightHeld = pressed
        if (pressed) mouseX = -1
      }
      GLFW.GLFW_KEY_R -> {
        if (pressed) {
          initialized = false
        }
      }
      else -> {
        return false
      }
    }
    return true
  }

  private fun update(width: Int, height: Int) {
    val now = System.nanoTime()
    if (lastFrameNanos == 0L) {
      lastFrameNanos = now
      return
    }

    val dt = min((now - lastFrameNanos) / 1000000000f, 0.05f)
    lastFrameNanos = now
    paddleY = (height - 26).toFloat()

    val mouseControlActive = mouseX >= 0 && now - lastMouseMoveNanos < 1000000000L
    if (!mouseControlActive) {
      mouseX = -1
      val move = 210f * dt
      if (leftHeld) paddleX -= move
      if (rightHeld) paddleX += move
    }
    paddleX = clamp(paddleX, HUD_MARGIN.toFloat(), (width - HUD_MARGIN - PADDLE_WIDTH).toFloat())

    if (lives <= 0) {
      return
    }

    ballX += ballVX * dt
    ballY += ballVY * dt

    if (ballX <= HUD_MARGIN) {
      ballX = HUD_MARGIN.toFloat()
      ballVX = abs(ballVX)
    } else if (ballX + BALL_SIZE >= width - HUD_MARGIN) {
      ballX = (width - HUD_MARGIN - BALL_SIZE).toFloat()
      ballVX = -abs(ballVX)
    }

    if (ballY <= HUD_MARGIN) {
      ballY = HUD_MARGIN.toFloat()
      ballVY = abs(ballVY)
    }

    if (ballY >= height) {
      lives--
      if (lives > 0) {
        respawnBall(width, height)
      }
      return
    }

    if (ballX + BALL_SIZE >= paddleX &&
        ballX <= paddleX + PADDLE_WIDTH &&
        ballY + BALL_SIZE >= paddleY &&
        ballY <= paddleY + PADDLE_HEIGHT &&
        ballVY > 0) {
      val hit: Float = ((ballX + (BALL_SIZE / 2f)) - paddleX) / PADDLE_WIDTH
      ballVY = -abs(ballVY)
      ballVX = lerp(-120f, 120f, hit)
      ballY = paddleY - BALL_SIZE
    }

    collideBricks(width)
    if (score == bricks.size) {
      refillBricks()
      respawnBall(width, height)
    }
  }

  private fun collideBricks(width: Int) {
    val brickAreaWidth: Int = width - (HUD_MARGIN * 2)
    val brickWidth: Int = (brickAreaWidth - (BRICK_GAP * (BRICK_COLUMNS - 1))) / BRICK_COLUMNS
    val brickHeight = 14
    val top: Int = HUD_MARGIN + 24

    for (row in 0..<BRICK_ROWS) {
      for (col in 0..<BRICK_COLUMNS) {
        val index: Int = row * BRICK_COLUMNS + col
        if (!bricks[index]) continue

        val brickX: Int = HUD_MARGIN + col * (brickWidth + BRICK_GAP)
        val brickY: Int = top + row * (brickHeight + BRICK_GAP)
        if (ballX + BALL_SIZE < brickX ||
            ballX > brickX + brickWidth ||
            ballY + BALL_SIZE < brickY ||
            ballY > brickY + brickHeight) {
          continue
        }

        bricks[index] = false
        score++
        val overlapLeft: Float = (ballX + BALL_SIZE) - brickX
        val overlapRight = (brickX + brickWidth) - ballX
        val overlapTop: Float = (ballY + BALL_SIZE) - brickY
        val overlapBottom = (brickY + brickHeight) - ballY
        val minOverlap = min(min(overlapLeft, overlapRight), min(overlapTop, overlapBottom))
        if (minOverlap == overlapLeft || minOverlap == overlapRight) {
          ballVX = -ballVX
        } else {
          ballVY = -ballVY
        }
        return
      }
    }
  }

  private fun drawBackdrop(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
    context.fillGradient(x, y, x + width, y + height / 2, -0xeddfce, -0xe7d4bc)
    context.fillGradient(x, y + height / 2, x + width, y + height, -0xf3f0ea, -0xe8e5de)
    for (i in 0..6) {
      val stripeY = y + 18 + i * 28
      context.fill(x + HUD_MARGIN, stripeY, x + width - HUD_MARGIN, stripeY + 1, 0x22FFFFFF)
    }
  }

  private fun drawBricks(context: DrawContext, x: Int, y: Int, width: Int) {
    val brickAreaWidth: Int = width - (HUD_MARGIN * 2)
    val brickWidth: Int = (brickAreaWidth - (BRICK_GAP * (BRICK_COLUMNS - 1))) / BRICK_COLUMNS
    val brickHeight = 14
    val top: Int = HUD_MARGIN + 24
    val colors = intArrayOf(-0x1890af, -0xb5d9f, -0x163b96, -0xd56271, -0xd9b9ad)

    for (row in 0..<BRICK_ROWS) {
      for (col in 0..<BRICK_COLUMNS) {
        val index: Int = row * BRICK_COLUMNS + col
        if (!bricks[index]) continue
        val bx: Int = x + HUD_MARGIN + col * (brickWidth + BRICK_GAP)
        val by: Int = y + top + row * (brickHeight + BRICK_GAP)
        context.fill(bx, by, bx + brickWidth, by + brickHeight, colors[row % colors.size])
        context.fill(bx, by, bx + brickWidth, by + 2, 0x55FFFFFF)
      }
    }
  }

  private fun drawPaddle(context: DrawContext, x: Int, y: Int) {
    val px = x + Math.round(paddleX)
    val py = y + Math.round(paddleY)
    context.fill(px, py, px + PADDLE_WIDTH, py + PADDLE_HEIGHT, -0x181311)
    context.fill(px + 6, py + 2, px + PADDLE_WIDTH - 6, py + PADDLE_HEIGHT, -0x473a2a)
  }

  private fun drawBall(context: DrawContext, x: Int, y: Int) {
    val bx = x + Math.round(ballX)
    val by = y + Math.round(ballY)
    context.fill(bx, by, bx + BALL_SIZE, by + BALL_SIZE, -0x71351a)
    context.fill(bx + 2, by + 2, bx + BALL_SIZE, by + BALL_SIZE, -0xde6144)
  }

  private fun drawHud(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
    val mc = MinecraftClient.getInstance()
    context.drawText(
        mc.textRenderer, Text.literal("PGE Canvas MVP"), x + HUD_MARGIN, y + 8, -0x150d01, false)
    context.drawText(
        mc.textRenderer,
        Text.literal("Score " + score + "  Lives " + lives),
        x + width - 104,
        y + 8,
        -0x463726,
        false)

    if (lives <= 0) {
      val boxX = x + width / 2 - 74
      val boxY = y + height / 2 - 22
      context.fill(boxX, boxY, boxX + 148, boxY + 44, -0x33efece6)
      context.drawCenteredTextWithShadow(
          mc.textRenderer, Text.literal("Game Over"), x + width / 2, boxY + 10, -0x73aab)
      context.drawCenteredTextWithShadow(
          mc.textRenderer, Text.literal("Click or press R"), x + width / 2, boxY + 24, -0x150d01)
    } else {
      context.drawText(
          mc.textRenderer,
          Text.literal("Move: mouse / A D / arrows"),
          x + HUD_MARGIN,
          y + height - 18,
          -0x6c5842,
          false)
    }
  }

  private fun reset(width: Int, height: Int) {
    initialized = true
    score = 0
    lives = 3
    paddleX = (width - PADDLE_WIDTH) / 2f
    paddleY = (height - 26).toFloat()
    mouseX = -1
    lastMouseMoveNanos = 0L
    refillBricks()
    respawnBall(width, height)
    lastFrameNanos = System.nanoTime()
  }

  private fun refillBricks() {
    for (i in bricks.indices) {
      bricks[i] = true
    }
  }

  private fun respawnBall(width: Int, height: Int) {
    ballX = width / 2f - (BALL_SIZE / 2f)
    ballY = height * 0.65f
    ballVX = 84f
    ballVY = -124f
  }

  private fun clamp(value: Float, min: Float, max: Float): Float {
    return max(min, min(max, value))
  }

  private fun lerp(min: Float, max: Float, t: Float): Float {
    return min + (max - min) * clamp(t, 0f, 1f)
  }

  companion object {
    private const val HUD_MARGIN = 12
    private const val PADDLE_WIDTH = 58
    private const val PADDLE_HEIGHT = 8
    private const val BALL_SIZE = 8
    private const val BRICK_COLUMNS = 8
    private const val BRICK_ROWS = 5
    private const val BRICK_GAP = 6
  }
}

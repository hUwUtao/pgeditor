package work.stdpi.pge.editor.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MiniGameRenderer {
    private static final int HUD_MARGIN = 12;
    private static final int PADDLE_WIDTH = 58;
    private static final int PADDLE_HEIGHT = 8;
    private static final int BALL_SIZE = 8;
    private static final int BRICK_COLUMNS = 8;
    private static final int BRICK_ROWS = 5;
    private static final int BRICK_GAP = 6;

    private boolean initialized;
    private boolean leftHeld;
    private boolean rightHeld;
    private float paddleX;
    private float paddleY;
    private float ballX;
    private float ballY;
    private float ballVX;
    private float ballVY;
    private long lastFrameNanos;
    private int score;
    private int lives = 3;
    private final boolean[] bricks = new boolean[BRICK_COLUMNS * BRICK_ROWS];
    private int mouseX = -1;
    private long lastMouseMoveNanos;

    public void render(DrawContext context, int x, int y, int width, int height) {
        if (!initialized) {
            reset(width, height);
        }

        update(width, height);

        context.fill(x, y, x + width, y + height, 0xFF10131A);
        drawBackdrop(context, x, y, width, height);
        drawBricks(context, x, y, width);
        drawPaddle(context, x, y);
        drawBall(context, x, y);
        drawHud(context, x, y, width, height);
    }

    public boolean onMouse(int localX, int localY, int button, int action, int mods, int width, int height) {
        mouseX = localX;
        lastMouseMoveNanos = System.nanoTime();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS && lives <= 0) {
            reset(width, height);
            return true;
        }
        return true;
    }

    public void onMove(int localX, int localY, int width, int height) {
        mouseX = localX;
        lastMouseMoveNanos = System.nanoTime();
        float minX = HUD_MARGIN;
        float maxX = width - HUD_MARGIN - PADDLE_WIDTH;
        paddleX = clamp(localX - (PADDLE_WIDTH / 2f), minX, maxX);
        paddleY = height - 26;
    }

    public boolean onKey(int key, int action) {
        boolean pressed = action != GLFW.GLFW_RELEASE;
        switch (key) {
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
                leftHeld = pressed;
                if (pressed) mouseX = -1;
            }
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
                rightHeld = pressed;
                if (pressed) mouseX = -1;
            }
            case GLFW.GLFW_KEY_R -> {
                if (pressed) {
                    initialized = false;
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void update(int width, int height) {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }

        float dt = Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.05f);
        lastFrameNanos = now;
        paddleY = height - 26;

        boolean mouseControlActive = mouseX >= 0 && now - lastMouseMoveNanos < 1_000_000_000L;
        if (!mouseControlActive) {
            mouseX = -1;
            float move = 210f * dt;
            if (leftHeld) paddleX -= move;
            if (rightHeld) paddleX += move;
        }
        paddleX = clamp(paddleX, HUD_MARGIN, width - HUD_MARGIN - PADDLE_WIDTH);

        if (lives <= 0) {
            return;
        }

        ballX += ballVX * dt;
        ballY += ballVY * dt;

        if (ballX <= HUD_MARGIN) {
            ballX = HUD_MARGIN;
            ballVX = Math.abs(ballVX);
        } else if (ballX + BALL_SIZE >= width - HUD_MARGIN) {
            ballX = width - HUD_MARGIN - BALL_SIZE;
            ballVX = -Math.abs(ballVX);
        }

        if (ballY <= HUD_MARGIN) {
            ballY = HUD_MARGIN;
            ballVY = Math.abs(ballVY);
        }

        if (ballY >= height) {
            lives--;
            if (lives > 0) {
                respawnBall(width, height);
            }
            return;
        }

        if (ballX + BALL_SIZE >= paddleX && ballX <= paddleX + PADDLE_WIDTH
            && ballY + BALL_SIZE >= paddleY && ballY <= paddleY + PADDLE_HEIGHT && ballVY > 0) {
            float hit = ((ballX + (BALL_SIZE / 2f)) - paddleX) / PADDLE_WIDTH;
            ballVY = -Math.abs(ballVY);
            ballVX = lerp(-120f, 120f, hit);
            ballY = paddleY - BALL_SIZE;
        }

        collideBricks(width);
        if (score == bricks.length) {
            refillBricks();
            respawnBall(width, height);
        }
    }

    private void collideBricks(int width) {
        int brickAreaWidth = width - (HUD_MARGIN * 2);
        int brickWidth = (brickAreaWidth - (BRICK_GAP * (BRICK_COLUMNS - 1))) / BRICK_COLUMNS;
        int brickHeight = 14;
        int top = HUD_MARGIN + 24;

        for (int row = 0; row < BRICK_ROWS; row++) {
            for (int col = 0; col < BRICK_COLUMNS; col++) {
                int index = row * BRICK_COLUMNS + col;
                if (!bricks[index]) continue;

                int brickX = HUD_MARGIN + col * (brickWidth + BRICK_GAP);
                int brickY = top + row * (brickHeight + BRICK_GAP);
                if (ballX + BALL_SIZE < brickX || ballX > brickX + brickWidth
                    || ballY + BALL_SIZE < brickY || ballY > brickY + brickHeight) {
                    continue;
                }

                bricks[index] = false;
                score++;
                float overlapLeft = (ballX + BALL_SIZE) - brickX;
                float overlapRight = (brickX + brickWidth) - ballX;
                float overlapTop = (ballY + BALL_SIZE) - brickY;
                float overlapBottom = (brickY + brickHeight) - ballY;
                float minOverlap = Math.min(Math.min(overlapLeft, overlapRight), Math.min(overlapTop, overlapBottom));
                if (minOverlap == overlapLeft || minOverlap == overlapRight) {
                    ballVX = -ballVX;
                } else {
                    ballVY = -ballVY;
                }
                return;
            }
        }
    }

    private void drawBackdrop(DrawContext context, int x, int y, int width, int height) {
        context.fillGradient(x, y, x + width, y + height / 2, 0xFF122032, 0xFF182B44);
        context.fillGradient(x, y + height / 2, x + width, y + height, 0xFF0C0F16, 0xFF171A22);
        for (int i = 0; i < 7; i++) {
            int stripeY = y + 18 + i * 28;
            context.fill(x + HUD_MARGIN, stripeY, x + width - HUD_MARGIN, stripeY + 1, 0x22FFFFFF);
        }
    }

    private void drawBricks(DrawContext context, int x, int y, int width) {
        int brickAreaWidth = width - (HUD_MARGIN * 2);
        int brickWidth = (brickAreaWidth - (BRICK_GAP * (BRICK_COLUMNS - 1))) / BRICK_COLUMNS;
        int brickHeight = 14;
        int top = HUD_MARGIN + 24;
        int[] colors = {0xFFE76F51, 0xFFF4A261, 0xFFE9C46A, 0xFF2A9D8F, 0xFF264653};

        for (int row = 0; row < BRICK_ROWS; row++) {
            for (int col = 0; col < BRICK_COLUMNS; col++) {
                int index = row * BRICK_COLUMNS + col;
                if (!bricks[index]) continue;
                int bx = x + HUD_MARGIN + col * (brickWidth + BRICK_GAP);
                int by = y + top + row * (brickHeight + BRICK_GAP);
                context.fill(bx, by, bx + brickWidth, by + brickHeight, colors[row % colors.length]);
                context.fill(bx, by, bx + brickWidth, by + 2, 0x55FFFFFF);
            }
        }
    }

    private void drawPaddle(DrawContext context, int x, int y) {
        int px = x + Math.round(paddleX);
        int py = y + Math.round(paddleY);
        context.fill(px, py, px + PADDLE_WIDTH, py + PADDLE_HEIGHT, 0xFFE7ECEF);
        context.fill(px + 6, py + 2, px + PADDLE_WIDTH - 6, py + PADDLE_HEIGHT, 0xFFB8C5D6);
    }

    private void drawBall(DrawContext context, int x, int y) {
        int bx = x + Math.round(ballX);
        int by = y + Math.round(ballY);
        context.fill(bx, by, bx + BALL_SIZE, by + BALL_SIZE, 0xFF8ECAE6);
        context.fill(bx + 2, by + 2, bx + BALL_SIZE, by + BALL_SIZE, 0xFF219EBC);
    }

    private void drawHud(DrawContext context, int x, int y, int width, int height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        context.drawText(mc.textRenderer, Text.literal("PGE Canvas MVP"), x + HUD_MARGIN, y + 8, 0xFFEAF2FF, false);
        context.drawText(mc.textRenderer, Text.literal("Score " + score + "  Lives " + lives), x + width - 104, y + 8, 0xFFB9C8DA, false);

        if (lives <= 0) {
            int boxX = x + width / 2 - 74;
            int boxY = y + height / 2 - 22;
            context.fill(boxX, boxY, boxX + 148, boxY + 44, 0xCC10131A);
            context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Game Over"), x + width / 2, boxY + 10, 0xFFF8C555);
            context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Click or press R"), x + width / 2, boxY + 24, 0xFFEAF2FF);
        } else {
            context.drawText(mc.textRenderer, Text.literal("Move: mouse / A D / arrows"), x + HUD_MARGIN, y + height - 18, 0xFF93A7BE, false);
        }
    }

    private void reset(int width, int height) {
        initialized = true;
        score = 0;
        lives = 3;
        paddleX = (width - PADDLE_WIDTH) / 2f;
        paddleY = height - 26;
        mouseX = -1;
        lastMouseMoveNanos = 0L;
        refillBricks();
        respawnBall(width, height);
        lastFrameNanos = System.nanoTime();
    }

    private void refillBricks() {
        for (int i = 0; i < bricks.length; i++) {
            bricks[i] = true;
        }
    }

    private void respawnBall(int width, int height) {
        ballX = width / 2f - (BALL_SIZE / 2f);
        ballY = height * 0.65f;
        ballVX = 84f;
        ballVY = -124f;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float lerp(float min, float max, float t) {
        return min + (max - min) * clamp(t, 0f, 1f);
    }
}

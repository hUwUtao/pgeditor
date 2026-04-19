package work.stdpi.pge.editor.logic

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW
import work.stdpi.pge.editor.config.EditorConfig

object EditorManager {

    enum class DockSide { LEFT, TOP, RIGHT, BOTTOM }
    enum class RenderMode { TERMINAL, GIZMO_GRID, MINIGAME }
    enum class TerminalFontWeight { LIGHT, REGULAR, MEDIUM, BOLD }
    enum class TerminalSupersample(val value: Int) {
        X2(2), X4(4), X8(8), X12(12)
    }

    var side: DockSide = DockSide.RIGHT
        private set

    var renderMode: RenderMode = RenderMode.TERMINAL
        private set

    var percent: Float = 0.52f
        set(value) {
            field = value
            saveConfig()
            if (isEnabled) update()
        }

    var terminalCellWidthPx: Int = 20
        set(value) {
            field = value.coerceIn(1, 24)
            saveConfig()
        }

    var terminalFontWeight: TerminalFontWeight = TerminalFontWeight.REGULAR
        set(value) {
            field = value
            saveConfig()
        }

    var terminalSupersample: TerminalSupersample = TerminalSupersample.X8
        set(value) {
            field = value
            saveConfig()
        }

    var isEnabled: Boolean = false
        set(value) {
            field = value
            val client = MinecraftClient.getInstance()
            if (value) {
                update()
                ViewportController.isActive = true
                client.mouse.unlockCursor()
            } else {
                ViewportController.isActive = false
                client.mouse.lockCursor()
            }
            client.onResolutionChanged()
        }

    private lateinit var toggleKey: KeyBinding
    private var configLoaded: Boolean = false

    fun init() {
        loadConfig()
        toggleKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.pge-editor.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSLASH,
                KeyBinding.Category.MISC
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            while (toggleKey.wasPressed()) {
                val handle = client.window.handle
                // Ctrl + Toggle = Cycle Side
                if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
                    cycleSide()
                } else if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
                    cycleRenderMode()
                } else {
                    isEnabled = !isEnabled
                }
            }
            if (isEnabled) update()
        })
    }

    private fun cycleSide() {
        val next = (side.ordinal + 1) % DockSide.entries.size
        side = DockSide.entries[next]
        saveConfig()
        if (isEnabled) update()
    }

    private fun cycleRenderMode() {
        val next = (renderMode.ordinal + 1) % RenderMode.entries.size
        renderMode = RenderMode.entries[next]
        saveConfig()
    }

    fun update() {
        ViewportController.calculate(MinecraftClient.getInstance().window, side, percent)
    }

    fun adjustTerminalCellWidthPx(delta: Int) {
        terminalCellWidthPx += delta
    }

    private fun loadConfig() {
        val data = EditorConfig.load()
        side = parseEnum(data.dockSide, DockSide.RIGHT)
        renderMode = parseEnum(data.renderMode, RenderMode.TERMINAL)
        percent = data.dockPercent.coerceIn(0.2f, 0.8f)
        terminalCellWidthPx = data.terminalCellWidthPx.coerceIn(1, 24)
        terminalFontWeight = parseEnum(data.terminalFontWeight, TerminalFontWeight.REGULAR)
        terminalSupersample = parseEnum(data.terminalSupersample, TerminalSupersample.X8)
        configLoaded = true
    }

    private fun saveConfig() {
        if (!configLoaded) return

        EditorConfig.save(
            EditorConfig.Data(
                side.name,
                renderMode.name,
                percent,
                terminalCellWidthPx,
                terminalFontWeight.name,
                terminalSupersample.name
            )
        )
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return try {
            enumValueOf<T>(raw)
        } catch (ignored: IllegalArgumentException) {
            fallback
        }
    }


}

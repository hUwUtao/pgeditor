package work.stdpi.pge.editor.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.stdpi.pge.editor.logic.EditorManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object EditorConfig {
  fun load(): Data {
    if (!Files.exists(CONFIG_PATH)) {
      return Data()
    }

    try {
      Files.newBufferedReader(CONFIG_PATH).use { reader ->
        val loaded: Data? = GSON.fromJson<Data?>(reader, Data::class.java)
        return loaded ?: Data()
      }
    } catch (e: IOException) {
      LOGGER.warn("failed to load editor config from {}", CONFIG_PATH, e)
      return Data()
    } catch (e: JsonParseException) {
      LOGGER.warn("failed to load editor config from {}", CONFIG_PATH, e)
      return Data()
    }
  }

  fun save(data: Data?) {
    try {
      Files.createDirectories(CONFIG_PATH.parent)
      Files.newBufferedWriter(CONFIG_PATH).use { writer -> GSON.toJson(data, writer) }
    } catch (e: IOException) {
      LOGGER.warn("failed to save editor config to {}", CONFIG_PATH, e)
    }
  }

  @JvmRecord
  data class Data(
      val dockSide: String?,
      val renderMode: String?,
      val dockPercent: Float,
      val terminalCellWidthPx: Int,
      val terminalFontWeight: String?,
      val terminalSupersample: String?,
      val toggleKey: String?
  ) {
    constructor() :
        this(
            EditorManager.DockSide.RIGHT.name,
            "TERMINAL",
            0.52f,
            20,
            EditorManager.TerminalFontWeight.REGULAR.name,
            EditorManager.TerminalSupersample.X8.name,
            "key.keyboard.right.shift")
  }

  private val LOGGER: Logger = LoggerFactory.getLogger("pge-editor/config")
  private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
  private val CONFIG_PATH: Path = FabricLoader.getInstance().configDir.resolve("pge-editor.json")
}

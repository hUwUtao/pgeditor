package work.stdpi.pge.editor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.stdpi.pge.editor.logic.EditorManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EditorConfig {
    public static final EditorConfig INSTANCE = new EditorConfig();

    private static final Logger LOGGER = LoggerFactory.getLogger("pge-editor/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("pge-editor.json");

    private EditorConfig() {
    }

    public Data load() {
        if (!Files.exists(CONFIG_PATH)) {
            return new Data();
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            return loaded != null ? loaded : new Data();
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("failed to load editor config from {}", CONFIG_PATH, e);
            return new Data();
        }
    }

    public void save(Data data) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("failed to save editor config to {}", CONFIG_PATH, e);
        }
    }

    public record Data(
        String dockSide,
        String renderMode,
        float dockPercent,
        int terminalCellWidthPx,
        String terminalFontWeight,
        String terminalSupersample
    ) {
        public Data() {
            this(
                EditorManager.DockSide.RIGHT.name(),
                EditorManager.RenderMode.TERMINAL.name(),
                0.52f,
                20,
                EditorManager.TerminalFontWeight.REGULAR.name(),
                EditorManager.TerminalSupersample.X8.name()
            );
        }
    }
}

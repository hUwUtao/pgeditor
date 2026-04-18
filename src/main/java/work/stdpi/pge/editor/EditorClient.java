package work.stdpi.pge.editor;

import net.fabricmc.api.ClientModInitializer;
import work.stdpi.pge.editor.logic.EditorManager;

public class EditorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EditorManager.INSTANCE.init();
    }
}

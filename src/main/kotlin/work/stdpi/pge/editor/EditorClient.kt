package work.stdpi.pge.editor

import net.fabricmc.api.ClientModInitializer
import work.stdpi.pge.editor.logic.EditorManager

class EditorClient : ClientModInitializer {
  override fun onInitializeClient() {
    EditorManager.init()
  }
}

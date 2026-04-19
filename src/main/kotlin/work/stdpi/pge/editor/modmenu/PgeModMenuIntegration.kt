package work.stdpi.pge.editor.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screen.Screen

class PgeModMenuIntegration : ModMenuApi {
  override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
    return ConfigScreenFactory { parent: Screen? -> PgeSettingsScreen(parent) }
  }
}

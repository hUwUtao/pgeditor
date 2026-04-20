package work.stdpi.pge.editor.mixin

import net.minecraft.client.util.InputUtil
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(InputUtil.Key::class)
interface InputKeyAccessor {
    @Accessor("type")
    fun getType(): InputUtil.Type
}

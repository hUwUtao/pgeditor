package work.stdpi.pge.editor

import net.minecraft.client.util.Window

internal fun Window.realMetrics(): IWindowAccessor = this as IWindowAccessor

internal val IWindowAccessor.realWindowWidth: Int
  get() = `pge$getRealWindowWidth`()

internal val IWindowAccessor.realWindowHeight: Int
  get() = `pge$getRealWindowHeight`()

internal val IWindowAccessor.realFramebufferWidth: Int
  get() = `pge$getRealFramebufferWidth`()

internal val IWindowAccessor.realFramebufferHeight: Int
  get() = `pge$getRealFramebufferHeight`()

internal val IWindowAccessor.realScaledWidth: Int
  get() = `pge$getRealScaledWidth`()

internal val IWindowAccessor.realScaledHeight: Int
  get() = `pge$getRealScaledHeight`()

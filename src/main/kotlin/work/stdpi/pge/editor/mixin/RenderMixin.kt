package work.stdpi.pge.editor.mixin

import com.mojang.blaze3d.opengl.GlStateManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.InGameHud
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.RenderTickCounter
import org.joml.Matrix4f
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import work.stdpi.pge.editor.realFramebufferHeight
import work.stdpi.pge.editor.realFramebufferWidth
import work.stdpi.pge.editor.realMetrics
import work.stdpi.pge.editor.logic.ViewportController
import work.stdpi.pge.editor.render.EditorUI
import kotlin.math.max

class RenderMixin {
  @Mixin(GameRenderer::class)
  class GameRendererMixin {
    private fun `pge$useViewportMetrics`(): Boolean {
      val client = MinecraftClient.getInstance()
      return ViewportController.isActive && client.currentScreen == null
    }

    @Inject(method = ["getBasicProjectionMatrix"], at = [At("HEAD")], cancellable = true)
    private fun overrideProjectionMatrix(
        fovDegrees: Float,
        cir: CallbackInfoReturnable<Matrix4f?>
    ) {
      if (!`pge$useViewportMetrics`()) {
        return
      }

      val viewportWidth = max(1, ViewportController.width).toFloat()
      val viewportHeight = max(1, ViewportController.height).toFloat()
      val projection =
          Matrix4f()
              .perspective(
                  fovDegrees * 0.017453292f,
                  viewportWidth / viewportHeight,
                  0.05f,
                  (this as Any as GameRenderer).farPlaneDistance
              )
        cir.returnValue = projection
    }

    @Inject(method = ["render"], at = [At("HEAD")])
    private fun beforeRender(d: RenderTickCounter?, t: Boolean, ci: CallbackInfo?) {
      ViewportController.isWindowMetricsOverridden = `pge$useViewportMetrics`()
      if (ViewportController.isActive) {
        val win = MinecraftClient.getInstance().window
        val metrics = win.realMetrics()
        val rw = metrics.realFramebufferWidth
        val rh = metrics.realFramebufferHeight
        if (`pge$useViewportMetrics`()) {
          ViewportController.apply(win)
        } else {
          GlStateManager._viewport(0, 0, rw, rh)
        }
      }
    }

    @Inject(
        method = ["render"],
        at =
            [
                At(
                    value = "INVOKE",
                    target =
                        "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V")])
    private fun beforeWorldRender(d: RenderTickCounter?, t: Boolean, ci: CallbackInfo?) {
      if (`pge$useViewportMetrics`()) {
        ViewportController.apply(MinecraftClient.getInstance().window)
      }
    }

    @Inject(
        method = ["render"],
        at =
            [
                At(
                    value = "INVOKE",
                    target =
                        "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
                    shift = At.Shift.AFTER)])
    private fun fixWorldViewport(d: RenderTickCounter?, t: Boolean, ci: CallbackInfo?) {
      if (`pge$useViewportMetrics`()) {
        ViewportController.apply(MinecraftClient.getInstance().window)
      }
    }

    @Inject(
        method = ["render"],
        at =
            [
                At(
                    value = "INVOKE",
                    target =
                        "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")])
    private fun beforeHud(d: RenderTickCounter?, t: Boolean, ci: CallbackInfo?) {
      if (`pge$useViewportMetrics`()) {
        ViewportController.apply(MinecraftClient.getInstance().window)
      }
    }

    @Inject(
        method = ["render"],
        at =
            [
                At(
                    value = "INVOKE",
                    target =
                        "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
                    shift = At.Shift.AFTER)])
    private fun afterHud(d: RenderTickCounter?, t: Boolean, ci: CallbackInfo?) {
      ViewportController.isWindowMetricsOverridden = false
      if (ViewportController.isActive) {
        val win = MinecraftClient.getInstance().window
        val metrics = win.realMetrics()
        val rw = metrics.realFramebufferWidth
        val rh = metrics.realFramebufferHeight
        GlStateManager._viewport(0, 0, rw, rh)
      }
    }
  }

  @Mixin(InGameHud::class)
  class InGameHudMixin {
    @Inject(method = ["render"], at = [At("RETURN")])
    private fun renderEditor(g: DrawContext, d: RenderTickCounter?, ci: CallbackInfo?) {
      if (ViewportController.isActive) {
        ViewportController.isWindowMetricsOverridden = false
        val win = MinecraftClient.getInstance().window
        val metrics = win.realMetrics()
        val rw = metrics.realFramebufferWidth
        val rh = metrics.realFramebufferHeight
        GlStateManager._viewport(0, 0, rw, rh)
        EditorUI.render(g)
      }
    }
  }
}

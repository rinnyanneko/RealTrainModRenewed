package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.TitleScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent
import kotlin.math.max

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object TitleScreenWarningOverlay {
    @JvmStatic
    @SubscribeEvent
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        if (event.screen !is TitleScreen) {
            return
        }
        val warnings = PackRequirementWarnings.getWarnings()
        if (warnings.isEmpty()) {
            return
        }
        val minecraft = Minecraft.getInstance()
        val graphics: GuiGraphicsExtractor = event.guiGraphics
        val x = 8
        val y = 8
        var maxWidth = 0
        for (warning in warnings) {
            maxWidth = max(maxWidth, minecraft.font.width(warning))
        }
        val height = warnings.size * (minecraft.font.lineHeight + 2) + 6
        graphics.fill(x - 4, y - 4, x + maxWidth + 6, y + height, 0xB0200000.toInt())
        var lineY = y
        for (warning in warnings) {
            graphics.text(minecraft.font, warning, x, lineY, 0xFFFF66, false)
            lineY += minecraft.font.lineHeight + 2
        }
    }
}

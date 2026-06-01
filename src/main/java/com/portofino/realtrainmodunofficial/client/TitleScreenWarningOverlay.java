package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class TitleScreenWarningOverlay {
    private TitleScreenWarningOverlay() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        List<String> warnings = PackRequirementWarnings.getWarnings();
        if (warnings.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int x = 8;
        int y = 8;
        int maxWidth = 0;
        for (String warning : warnings) {
            maxWidth = Math.max(maxWidth, minecraft.font.width(warning));
        }
        int height = warnings.size() * (minecraft.font.lineHeight + 2) + 6;
        graphics.fill(x - 4, y - 4, x + maxWidth + 6, y + height, 0xB0200000);
        int lineY = y;
        for (String warning : warnings) {
            graphics.drawString(minecraft.font, warning, x, lineY, 0xFFFF66, false);
            lineY += minecraft.font.lineHeight + 2;
        }
    }
}

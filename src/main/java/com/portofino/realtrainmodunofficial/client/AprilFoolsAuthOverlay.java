package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.time.LocalDate;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class AprilFoolsAuthOverlay {
    private static final String MESSAGE = "認証キーを入力してください！";
    private static final boolean DEBUG_ALWAYS_SHOW_IN_WORLD = true;
    private static final float MESSAGE_SCALE = 2.2F;
    private static final int MARGIN_X = 10;
    private static final int MARGIN_Y = 10;

    private AprilFoolsAuthOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        if (!shouldShowOverlay()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int x = MARGIN_X;
        int y = MARGIN_Y;
        int color = getBlinkColor();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(MESSAGE_SCALE, MESSAGE_SCALE, 1.0F);
        drawShadowedString(graphics, font, MESSAGE, 0, 0, color);
        pose.popPose();
    }

    private static boolean shouldShowOverlay() {
        if (DEBUG_ALWAYS_SHOW_IN_WORLD) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return today.getMonthValue() == 4 && today.getDayOfMonth() == 1;
    }

    private static int getBlinkColor() {
        long phase = (System.currentTimeMillis() / 500L) & 1L;
        return phase == 0L ? 0xFFFFFF00 : 0xFFFF0000;
    }

    private static void drawShadowedString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.45F, 0.45F, 0.0F);
        graphics.drawString(font, text, x, y, 0xFF000000, false);
        pose.popPose();
        graphics.drawString(font, text, x, y, color, false);
    }
}

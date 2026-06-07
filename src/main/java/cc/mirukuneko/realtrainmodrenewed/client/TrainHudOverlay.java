package cc.mirukuneko.realtrainmodrenewed.client;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = Dist.CLIENT)
public final class TrainHudOverlay {
    private static final Identifier CAB_TEXTURE =
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/gui/rtm_cab.png");
    private static final int TEX_SIZE = 512;
    private static final int CAB_W = 416;
    private static final int CAB_H = 48;
    private static boolean cabHidden;

    private TrainHudOverlay() {
    }

    public static void toggleCabHidden() {
        cabHidden = !cabHidden;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        TrainEntity train = getControlledTrain(mc);
        if (train == null || !train.isDriverPassenger(mc.player)) {
            return;
        }

        GuiGraphicsExtractor g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        VehicleDefinition def = VehicleRegistry.getById(train.getVehicleId());
        boolean showCabOverlay = def == null || !def.isNotDisplayCab();

        if (!cabHidden && showCabOverlay) {
            renderDefaultRtmCab(g, font, train, def, screenW, screenH);
        }
    }

    private static void renderDefaultRtmCab(GuiGraphicsExtractor graphics, Font font, TrainEntity train,
                                            VehicleDefinition def, int screenW, int screenH) {
        float scale = Math.min(1.0F, screenW / (float) CAB_W);
        int x = Math.round((screenW - CAB_W * scale) * 0.5F);
        int y = Math.round(screenH - CAB_H * scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CAB_TEXTURE, x, y, 0.0F, 0.0F, Math.round(CAB_W * scale), Math.round(CAB_H * scale), TEX_SIZE, TEX_SIZE);
        drawMeter(graphics, 32, 19, 32, 32, 48, 240.0F * getBrakeRatio(train));
        drawMeter(graphics, 32, 19, 32, 0, 48, 240.0F * getBrakeCommandRatio(train));
        drawMeter(graphics, 72, 19, 32, 64, 48, getSpeedNeedleRotation(train, def));
        drawLever(graphics, 104, 29, train);
        drawWatch(graphics, train);
        graphics.text(font, Integer.toString(getSpeedKmh(train)), x + 70, y + 37, 0x00FF00, false);
        // ブレーキ段数表示 (B1-B8)。本家同様ノッチ番号をそのまま出す。
        graphics.text(font, Integer.toString(Math.max(0, -train.getNotch())), x + 30, y + 37, 0x00FF00, false);
        graphics.text(font, Integer.toString(getWorldTime()), x + 338, y + 8, 0x00FF00, false);
        graphics.text(font, getClockText(), x + 338, y + 18, 0x00FF00, false);
    }

    private static TrainEntity getControlledTrain(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        if (mc.player.getVehicle() instanceof TrainEntity train) {
            return train;
        }
        if (mc.player.getVehicle() instanceof TrainSeatEntity seat) {
            return seat.getTrain();
        }
        return null;
    }

    private static void drawLever(GuiGraphicsExtractor graphics, int x, int y, TrainEntity train) {
        int notch = train.getNotch();
        // rtm_cab.png のマスコン目盛りは中立(y28)から 3px 等間隔で並ぶ(実測):
        //   EB(-8)=y4(赤), B7=y7 ... B1=y25, N=y28, P1=y31 ... P5=y43。
        // よって針位置は notch に対して線形 y = 28 + 3*notch。本家RTMと同じ等間隔の針送りになる。
        float offset = 3.0F * notch;
        graphics.blit(RenderPipelines.GUI_TEXTURED, CAB_TEXTURE, x - 4, Math.round(y + offset - 2), 0.0F, 80.0F, 8, 3, TEX_SIZE, TEX_SIZE);
    }

    private static void drawWatch(GuiGraphicsExtractor graphics, TrainEntity train) {
        int startX = 320;
        int startY = 32;
        int t0 = getWorldTime(train);
        int hour12 = (t0 / 1000 + 6) % 12;
        drawMeter(graphics, startX, startY, 32, 96, 48, 360.0F * hour12 / 12.0F + 135.0F);
        int minute = (int) ((t0 % 1000) * 0.06F);
        drawMeter(graphics, startX, startY, 32, 128, 48, 360.0F * minute / 60.0F + 135.0F);
    }

    private static void drawWatch(GuiGraphicsExtractor graphics) {
        drawMeter(graphics, 320, 32, 32, 96, 48, 135.0F);
        drawMeter(graphics, 320, 32, 32, 128, 48, 135.0F);
    }

    private static void drawMeter(GuiGraphicsExtractor graphics, int x, int y, int size, int u, int v, float rotation) {
        int offset = -(size / 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CAB_TEXTURE, x + offset, y + offset, u, v, size, size, TEX_SIZE, TEX_SIZE);
    }

    private static int getSpeedKmh(TrainEntity train) {
        return Math.round(Math.abs(train.getSpeed()) * 72.0F);
    }

    private static float getSpeedNeedleRotation(TrainEntity train, VehicleDefinition def) {
        float max = 120.0F;
        if (def != null && !def.getNotchMaxSpeeds().isEmpty()) {
            for (Float speed : def.getNotchMaxSpeeds()) {
                if (speed != null) {
                    max = Math.max(max, speed);
                }
            }
        }
        return Math.min(270.0F, 270.0F * getSpeedKmh(train) / Math.max(1.0F, max));
    }

    private static float getBrakeRatio(TrainEntity train) {
        // 実際の最大ブレーキ段数で割る(段数とメーターのズレを防ぐ)。
        return Math.min(1.0F, Math.max(0.0F, -train.getNotch()) / (float) Math.max(1, train.getMaxBrakeNotch()));
    }

    private static float getBrakeCommandRatio(TrainEntity train) {
        return Math.min(1.0F, Math.max(0.0F, -train.getNotch()) / (float) Math.max(1, train.getMaxBrakeNotch()));
    }

    private static int getWorldTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (int) (mc.level.getLevelData().getGameTime() % 24000L);
    }

    private static int getWorldTime(TrainEntity train) {
        return train.level() == null ? getWorldTime() : (int) (train.level().getLevelData().getGameTime() % 24000L);
    }

    private static String getClockText() {
        int t0 = getWorldTime();
        int hour = (t0 / 1000 + 6) % 24;
        int minute = (int) ((t0 % 1000) * 0.06F);
        return hour + ":" + minute;
    }
}

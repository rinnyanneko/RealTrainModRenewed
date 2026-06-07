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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = Dist.CLIENT)
public final class TrainHudOverlay {
    private static final Identifier CAB_TEXTURE =
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "textures/gui/rtm_cab.png");
    private static final int TEX_SIZE = 512;
    private static final int CAB_W = 416;
    private static final int CAB_H = 48;
    private static final int HUD_GREEN = 0xFF00FF00;
    private static final Map<UUID, Float> BRAKE_PRESSURE = new ConcurrentHashMap<>();
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
        drawGaugeNeedle(graphics, x, y, scale, 32, 19, 14.0F, getGaugeAngle(240.0F * getBrakeRatio(train)), 0xFFFFFFFF);
        drawGaugeNeedle(graphics, x, y, scale, 32, 19, 11.0F, getGaugeAngle(240.0F * getBrakeCommandRatio(train)), 0xFFFF4040);
        drawGaugeNeedle(graphics, x, y, scale, 72, 19, 14.0F, getGaugeAngle(getSpeedNeedleRotation(train, def)), 0xFFFFFFFF);
        drawLever(graphics, x, y, scale, train);
        drawWatch(graphics, x, y, scale, train);
        drawCenteredText(graphics, font, Integer.toString(getSpeedKmh(train)), x, y, scale, 72, 37);
        // ブレーキ段数表示 (B1-B8)。本家同様ノッチ番号をそのまま出す。
        drawCenteredText(graphics, font, Integer.toString(Math.max(0, -train.getNotch())), x, y, scale, 32, 37);
        graphics.text(font, Integer.toString(getWorldTime()), scaledX(x, scale, 338), scaledY(y, scale, 8), HUD_GREEN, false);
        graphics.text(font, getClockText(), scaledX(x, scale, 338), scaledY(y, scale, 18), HUD_GREEN, false);
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

    private static void drawLever(GuiGraphicsExtractor graphics, int x, int y, float scale, TrainEntity train) {
        int notch = train.getNotch();
        // rtm_cab.png のマスコン目盛りは中立(y28)から 3px 等間隔で並ぶ(実測):
        //   EB(-8)=y4(赤), B7=y7 ... B1=y25, N=y28, P1=y31 ... P5=y43。
        // よって針位置は notch に対して線形 y = 28 + 3*notch。本家RTMと同じ等間隔の針送りになる。
        float offset = 3.0F * notch;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CAB_TEXTURE, 100, Math.round(27 + offset), 0.0F, 80.0F, 8, 3, TEX_SIZE, TEX_SIZE);
        pose.popMatrix();
    }

    private static void drawWatch(GuiGraphicsExtractor graphics, int x, int y, float scale, TrainEntity train) {
        int startX = 320;
        int startY = 32;
        int t0 = getWorldTime(train);
        int hour12 = (t0 / 1000 + 6) % 12;
        drawMeter(graphics, x, y, scale, startX, startY, 32, 96, 48, 360.0F * hour12 / 12.0F + 135.0F);
        int minute = (int) ((t0 % 1000) * 0.06F);
        drawMeter(graphics, x, y, scale, startX, startY, 32, 128, 48, 360.0F * minute / 60.0F + 135.0F);
    }

    private static void drawMeter(GuiGraphicsExtractor graphics, int x, int y, float scale,
                                  int localX, int localY, int size, int u, int v, float rotation) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + localX * scale, y + localY * scale);
        pose.rotate((float) Math.toRadians(rotation));
        pose.scale(scale, scale);
        int offset = -(size / 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CAB_TEXTURE, offset, offset, u, v, size, size, TEX_SIZE, TEX_SIZE);
        pose.popMatrix();
    }

    private static void drawGaugeNeedle(GuiGraphicsExtractor graphics, int x, int y, float scale,
                                        int localX, int localY, float length, float angleDegrees, int color) {
        int cx = scaledX(x, scale, localX);
        int cy = scaledY(y, scale, localY);
        double radians = Math.toRadians(angleDegrees);
        double dx = Math.cos(radians);
        double dy = Math.sin(radians);
        int steps = Math.max(1, Math.round(length * scale));
        int halfWidth = scale >= 0.85F ? 1 : 0;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int px = (int) Math.round(cx + dx * length * scale * t);
            int py = (int) Math.round(cy + dy * length * scale * t);
            graphics.fill(px - halfWidth, py - halfWidth, px + halfWidth + 1, py + halfWidth + 1, color);
        }
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
    }

    private static float getGaugeAngle(float rotation) {
        return 150.0F + rotation;
    }

    private static int scaledX(int x, float scale, int localX) {
        return Math.round(x + localX * scale);
    }

    private static int scaledY(int y, float scale, int localY) {
        return Math.round(y + localY * scale);
    }

    private static void drawCenteredText(GuiGraphicsExtractor graphics, Font font, String text,
                                         int x, int y, float scale, int localX, int localY) {
        int textX = scaledX(x, scale, localX) - font.width(text) / 2;
        graphics.text(font, text, textX, scaledY(y, scale, localY), HUD_GREEN, false);
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
        float target = getBrakeCommandRatio(train);
        UUID id = train.getUUID();
        float current = BRAKE_PRESSURE.getOrDefault(id, target);
        float step = target > current ? 0.028F : 0.045F;
        if (current < target) {
            current = Math.min(target, current + step);
        } else if (current > target) {
            current = Math.max(target, current - step);
        }
        BRAKE_PRESSURE.put(id, current);
        return current;
    }

    private static float getBrakeCommandRatio(TrainEntity train) {
        // 実際の最大ブレーキ段数で割る(段数とメーターのズレを防ぐ)。
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

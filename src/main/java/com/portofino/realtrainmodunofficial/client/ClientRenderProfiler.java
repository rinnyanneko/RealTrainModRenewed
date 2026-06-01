package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 軽量なクライアント側描画プロファイラ。
 * 1 秒単位で描画カテゴリごとの合計時間を集計して、必要な時だけ HUD に出す。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class ClientRenderProfiler {
    private static final String[] CATEGORY_NAMES = {"Rail", "Train", "Object"};
    private static final int CATEGORY_RAIL = 0;
    private static final int CATEGORY_TRAIN = 1;
    private static final int CATEGORY_OBJECT = 2;

    private static final long[] totalsNs = new long[CATEGORY_NAMES.length];
    private static final int[] counts = new int[CATEGORY_NAMES.length];
    private static final long[] displayTotalsNs = new long[CATEGORY_NAMES.length];
    private static final int[] displayCounts = new int[CATEGORY_NAMES.length];

    private static long lastSnapshotNs = System.nanoTime();
    private static boolean overlayEnabled;

    private ClientRenderProfiler() {
    }

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
    }

    public static long begin() {
        return System.nanoTime();
    }

    public static void endRail(long startNs) {
        record(CATEGORY_RAIL, startNs);
    }

    public static void endTrain(long startNs) {
        record(CATEGORY_TRAIN, startNs);
    }

    public static void endInstalledObject(long startNs) {
        record(CATEGORY_OBJECT, startNs);
    }

    private static synchronized void record(int category, long startNs) {
        long elapsed = System.nanoTime() - startNs;
        totalsNs[category] += elapsed;
        counts[category]++;
        snapshotIfNeeded();
    }

    private static void snapshotIfNeeded() {
        long now = System.nanoTime();
        if (now - lastSnapshotNs < 1_000_000_000L) {
            return;
        }
        System.arraycopy(totalsNs, 0, displayTotalsNs, 0, totalsNs.length);
        System.arraycopy(counts, 0, displayCounts, 0, counts.length);
        for (int i = 0; i < totalsNs.length; i++) {
            totalsNs[i] = 0L;
            counts[i] = 0;
        }
        lastSnapshotNs = now;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !overlayEnabled) {
            return;
        }

        snapshotIfNeeded();

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int x = 8;
        int y = 8;
        int lineHeight = font.lineHeight + 2;
        int width = 0;
        String[] lines = new String[CATEGORY_NAMES.length + 1];
        lines[0] = "Profiler [F8]";
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            double totalMs = displayTotalsNs[i] / 1_000_000.0D;
            double avgMs = displayCounts[i] > 0 ? totalMs / displayCounts[i] : 0.0D;
            lines[i + 1] = CATEGORY_NAMES[i] + ": "
                + String.format(java.util.Locale.ROOT, "%.2f ms", totalMs)
                + " / "
                + String.format(java.util.Locale.ROOT, "%.2f avg", avgMs)
                + " (" + displayCounts[i] + ")";
        }

        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        graphics.fill(x - 4, y - 4, x + width + 6, y + lineHeight * lines.length + 2, 0x90000000);
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], x, y + i * lineHeight, 0xFFFFFF, false);
        }
    }
}

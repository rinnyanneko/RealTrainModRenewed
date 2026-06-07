package jp.kaiz.atsassistmod.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Plays a sequence of sound "orders" (port of ClientProxy.playSounds). Each order is
 * either a {@code "namespace:path"} sound (played and waited on until it finishes) or
 * a number (a pause in seconds). Runs on a worker thread so the sequence does not
 * block the game.
 */
public final class SoundSequence {
    private SoundSequence() {}

    public static void play(List<int[]> posList, List<String> orders, float volume) {
        new Thread(() -> {
            Minecraft mc = Minecraft.getInstance();
            for (String order : orders) {
                if (order == null) {
                    continue;
                }
                try {
                    if (order.contains(":")) {
                        Identifier loc = Identifier.parse(order);
                        SoundEvent event = SoundEvent.createVariableRangeEvent(loc);
                        List<SoundInstance> tracks = new ArrayList<>();
                        for (int[] pos : posList) {
                            if (pos == null) continue;
                            SoundInstance s = new PosSoundInstance(pos[0], pos[1], pos[2], event, false, volume);
                            tracks.add(s);
                            mc.execute(() -> mc.getSoundManager().play(s));
                        }
                        if (tracks.isEmpty()) continue;
                        Thread.sleep(50L);
                        while (mc.getSoundManager().isActive(tracks.get(0))) {
                            Thread.sleep(50L);
                        }
                    } else if (isNumber(order)) {
                        Thread.sleep((long) (1000L * Double.parseDouble(order)));
                    }
                } catch (Throwable t) {
                    break;
                }
            }
        }, "ATSAssist-SoundSequence").start();
    }

    public static void play(Entity entity, List<String> orders, float volume) {
        new Thread(() -> {
            Minecraft mc = Minecraft.getInstance();
            for (String order : orders) {
                if (order == null) {
                    continue;
                }
                try {
                    if (order.contains(":")) {
                        Identifier loc = Identifier.parse(order);
                        SoundEvent event = SoundEvent.createVariableRangeEvent(loc);
                        SoundInstance track = new EntitySoundInstance(entity, event, false, volume);
                        mc.execute(() -> mc.getSoundManager().play(track));
                        Thread.sleep(50L);
                        while (mc.getSoundManager().isActive(track)) {
                            Thread.sleep(50L);
                        }
                    } else if (isNumber(order)) {
                        Thread.sleep((long) (1000L * Double.parseDouble(order)));
                    }
                } catch (Throwable t) {
                    break;
                }
            }
        }, "ATSAssist-SoundSequence").start();
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

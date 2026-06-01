package com.portofino.realtrainmodunofficial.client.sound;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyScriptSoundManager {
    private static final Map<String, LoopingTrainSound> ACTIVE = new ConcurrentHashMap<>();

    private LegacyScriptSoundManager() {
    }

    public static void play(TrainEntity train, String namespace, String soundName, float volume, float pitch) {
        play(train, namespace, soundName, volume, pitch, true);
    }

    public static void playLegacyId(TrainEntity train, String legacySoundId, float volume, float pitch, boolean looping) {
        if (legacySoundId == null || legacySoundId.isBlank()) {
            return;
        }
        String namespace = "rtm";
        String soundName = legacySoundId;
        int separator = legacySoundId.indexOf(':');
        if (separator >= 0) {
            namespace = legacySoundId.substring(0, separator);
            soundName = legacySoundId.substring(separator + 1);
        }
        play(train, namespace, soundName, volume, pitch, looping);
    }

    public static void play(TrainEntity train, String namespace, String soundName, float volume, float pitch, boolean looping) {
        if (train == null || !train.level().isClientSide()) {
            return;
        }
        ResourceLocation soundId = toSoundId(namespace, soundName);
        if (soundId == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        if (!looping) {
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                soundId,
                SoundSource.NEUTRAL,
                Mth.clamp(volume, 0.0F, 8.0F),
                Mth.clamp(pitch, 0.05F, 4.0F),
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                train.getX(),
                train.getY(),
                train.getZ(),
                false
            ));
            return;
        }
        String key = key(train.getUUID(), soundId);
        LoopingTrainSound sound = ACTIVE.get(key);
        if (sound == null || sound.isStopped()) {
            sound = new LoopingTrainSound(train, soundId);
            sound.update(volume, pitch);
            ACTIVE.put(key, sound);
            minecraft.getSoundManager().play(sound);
        } else {
            sound.update(volume, pitch);
        }
    }

    /**
     * 任意のワールド座標で 1 回サウンドを鳴らす（スピーカー用）。
     * soundIdStr は "namespace:path" 形式のサウンドイベントID。
     * volume を上げると可聴範囲が広がる（MC の LINEAR 減衰は概ね volume×16 ブロック）。
     */
    public static void playAt(double x, double y, double z, String soundIdStr, float volume, float pitch) {
        if (soundIdStr == null || soundIdStr.isBlank()) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(soundIdStr.trim().toLowerCase(java.util.Locale.ROOT));
        if (soundId == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(
            soundId,
            SoundSource.RECORDS,
            Mth.clamp(volume, 0.0F, 16.0F),
            Mth.clamp(pitch, 0.05F, 4.0F),
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.LINEAR,
            x,
            y,
            z,
            false
        ));
    }

    public static void stop(TrainEntity train, String namespace, String soundName) {
        if (train == null) {
            return;
        }
        ResourceLocation soundId = toSoundId(namespace, soundName);
        if (soundId == null) {
            return;
        }
        LoopingTrainSound sound = ACTIVE.remove(key(train.getUUID(), soundId));
        if (sound != null) {
            sound.requestStop();
        }
    }

    public static void playLeverClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath("rtm", "train.lever");
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(soundId), 1.0F, 0.55F));
    }

    private static String key(UUID trainId, ResourceLocation soundId) {
        return trainId + "|" + soundId;
    }

    private static ResourceLocation toSoundId(String namespace, String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return null;
        }
        String resolvedNamespace = namespace == null || namespace.isBlank() ? "minecraft" : namespace.toLowerCase(java.util.Locale.ROOT);
        String resolvedPath = soundName.trim().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (resolvedPath.startsWith("sounds/")) {
            resolvedPath = resolvedPath.substring("sounds/".length());
        }
        if (resolvedPath.endsWith(".ogg")) {
            resolvedPath = resolvedPath.substring(0, resolvedPath.length() - ".ogg".length());
        }
        if (resolvedNamespace.equals("rtm") && resolvedPath.indexOf('/') >= 0) {
            resolvedPath = resolvedPath.replace('/', '.');
        }
        try {
            return ResourceLocation.fromNamespaceAndPath(resolvedNamespace, resolvedPath);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Invalid legacy sound id {}:{}", resolvedNamespace, soundName);
            return null;
        }
    }

    private static final class LoopingTrainSound extends AbstractTickableSoundInstance {
        private final TrainEntity train;

        private LoopingTrainSound(TrainEntity train, ResourceLocation soundId) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.train = train;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.relative = false;
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }

        private void update(float volume, float pitch) {
            this.volume = Mth.clamp(volume, 0.0F, 8.0F);
            this.pitch = Mth.clamp(pitch, 0.05F, 4.0F);
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }

        private void requestStop() {
            stop();
        }

        @Override
        public void tick() {
            if (!train.isAlive()) {
                stop();
                return;
            }
            this.x = train.getX();
            this.y = train.getY();
            this.z = train.getZ();
        }
    }
}

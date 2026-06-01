package com.portofino.realtrainmodunofficial.client.sound;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CrossingGateSoundManager {
    private static final ResourceLocation CROSSING_SOUND_ID = ResourceLocation.fromNamespaceAndPath("rtm", "block.crossing_gate");
    private static final Map<String, LoopingCrossingSound> ACTIVE = new ConcurrentHashMap<>();

    private CrossingGateSoundManager() {
    }

    public static void tick(InstalledObjectBlockEntity blockEntity) {
        if (blockEntity == null) {
            return;
        }
        Level level = blockEntity.getLevel();
        if (level == null || !level.isClientSide()) {
            return;
        }
        if (!blockEntity.isPowered()) {
            stop(level, blockEntity.getBlockPos());
            return;
        }
        if (!shouldPlayCrossingSound(blockEntity)) {
            stop(level, blockEntity.getBlockPos());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }

        String key = key(level, blockEntity.getBlockPos());
        ResourceLocation soundId = resolveSoundId(blockEntity);
        LoopingCrossingSound sound = ACTIVE.get(key);
        if (sound == null || sound.isStopped() || !sound.matches(soundId)) {
            if (sound != null) {
                sound.requestStop();
            }
            sound = new LoopingCrossingSound(blockEntity, soundId);
            ACTIVE.put(key, sound);
            minecraft.getSoundManager().play(sound);
        } else {
            sound.refresh();
        }
    }

    public static void stop(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        LoopingCrossingSound sound = ACTIVE.remove(key(level, pos));
        if (sound != null) {
            sound.requestStop();
        }
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    private static boolean shouldPlayCrossingSound(InstalledObjectBlockEntity blockEntity) {
        InstalledObjectDefinition definition = blockEntity == null ? null : InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        if (definition == null) {
            return false;
        }
        String runningSound = definition.getRunningSound();
        if (runningSound != null && !runningSound.isBlank()) {
            return true;
        }
        String id = definition.getId() == null ? "" : definition.getId().toLowerCase(java.util.Locale.ROOT);
        String name = definition.getDisplayName() == null ? "" : definition.getDisplayName().toLowerCase(java.util.Locale.ROOT);
        String model = definition.getModelFile() == null ? "" : definition.getModelFile().toLowerCase(java.util.Locale.ROOT);
        return id.contains("crossing")
            || id.contains("fumikiri")
            || name.contains("crossing")
            || name.contains("fumikiri")
            || model.contains("crossing")
            || model.contains("fumikiri");
    }

    private static ResourceLocation resolveSoundId(InstalledObjectBlockEntity blockEntity) {
        InstalledObjectDefinition definition = blockEntity == null ? null : InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        String raw = definition == null ? "" : definition.getRunningSound();
        if (raw == null || raw.isBlank()) {
            return CROSSING_SOUND_ID;
        }
        String normalized = raw.trim().replace('\\', '/');
        if (normalized.endsWith(".ogg")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.startsWith("sounds/")) {
            normalized = normalized.substring("sounds/".length());
        }
        String lowered = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lowered.contains("rtm_crossinggate") || lowered.contains("crossinggate0") || lowered.contains("crossinggate1")) {
            return CROSSING_SOUND_ID;
        }
        try {
            if (normalized.contains(":")) {
                String[] split = normalized.split(":", 2);
                String namespace = split[0].isBlank() ? "minecraft" : split[0].toLowerCase(java.util.Locale.ROOT);
                String path = split[1].toLowerCase(java.util.Locale.ROOT);
                if ("rtm".equals(namespace) && path.indexOf('/') >= 0) {
                    path = path.replace('/', '.');
                }
                return ResourceLocation.fromNamespaceAndPath(namespace, path);
            }
            String path = normalized.toLowerCase(java.util.Locale.ROOT);
            if (path.indexOf('/') >= 0) {
                path = path.replace('/', '.');
            }
            return ResourceLocation.fromNamespaceAndPath("rtm", path);
        } catch (Exception ignored) {
            return CROSSING_SOUND_ID;
        }
    }

    private static final class LoopingCrossingSound extends AbstractTickableSoundInstance {
        private final InstalledObjectBlockEntity blockEntity;
        private final ResourceLocation soundId;

        private LoopingCrossingSound(InstalledObjectBlockEntity blockEntity, ResourceLocation soundId) {
            super(SoundEvent.createVariableRangeEvent(soundId == null ? CROSSING_SOUND_ID : soundId), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.blockEntity = blockEntity;
            this.soundId = soundId == null ? CROSSING_SOUND_ID : soundId;
            this.looping = true;
            this.delay = 0;
            this.relative = false;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            refresh();
        }

        private boolean matches(ResourceLocation other) {
            return soundId.equals(other == null ? CROSSING_SOUND_ID : other);
        }

        private void refresh() {
            BlockPos pos = blockEntity.getBlockPos();
            this.x = pos.getX() + 0.5D;
            this.y = pos.getY() + 0.5D;
            this.z = pos.getZ() + 0.5D;
        }

        private void requestStop() {
            stop();
        }

        @Override
        public void tick() {
            Level level = blockEntity.getLevel();
            if (level == null
                || !level.isClientSide()
                || blockEntity.isRemoved()
                || !blockEntity.isPowered()
                || level.getBlockEntity(blockEntity.getBlockPos()) != blockEntity) {
                if (level != null) {
                    ACTIVE.remove(key(level, blockEntity.getBlockPos()), this);
                }
                stop();
                return;
            }
            refresh();
        }
    }
}

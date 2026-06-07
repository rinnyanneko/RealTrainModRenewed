package jp.kaiz.atsassistmod.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/** Moving sound that follows an entity (port of ATSAMovingSoundEntity). */
public class EntitySoundInstance extends AbstractTickableSoundInstance {
    private final Entity entity;

    public EntitySoundInstance(Entity entity, SoundEvent sound, boolean repeat, float volume) {
        super(sound, SoundSource.RECORDS, RandomSource.create());
        this.entity = entity;
        this.looping = repeat;
        this.volume = volume;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (entity.isAlive()) {
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
        } else {
            stop();
        }
    }
}

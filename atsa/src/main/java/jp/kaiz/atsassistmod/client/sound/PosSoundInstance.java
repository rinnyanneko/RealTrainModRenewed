package jp.kaiz.atsassistmod.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Moving sound fixed at a position (port of ATSAMovingSoundTileEntity). */
public class PosSoundInstance extends AbstractTickableSoundInstance {
    public PosSoundInstance(int x, int y, int z, SoundEvent sound, boolean repeat, float volume) {
        super(sound, SoundSource.RECORDS, RandomSource.create());
        this.looping = repeat;
        this.volume = volume;
        this.x = x + 0.5D;
        this.y = y + 0.5D;
        this.z = z + 0.5D;
    }

    @Override
    public void tick() {
        // fixed position; nothing to update
    }
}

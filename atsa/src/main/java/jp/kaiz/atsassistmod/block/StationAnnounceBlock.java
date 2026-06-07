package jp.kaiz.atsassistmod.block;

import net.minecraft.world.level.block.Block;

/**
 * Station-announce base block. In the original this block had no tile entity and
 * its activation handler did nothing functional, so it is ported as a plain block
 * to preserve its presence and recipe/creative slot.
 */
public class StationAnnounceBlock extends Block {
    public StationAnnounceBlock() {
        this(Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops());
    }

    public StationAnnounceBlock(Properties properties) {
        super(properties);
    }
}

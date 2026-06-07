package cc.mirukuneko.realtrainmodrenewed.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

public class MarkerItem extends BlockItem {
    private final boolean diagonal;

    public MarkerItem(Block block, boolean diagonal) {
        this(block, diagonal, new Properties());
    }

    public MarkerItem(Block block, boolean diagonal, Properties properties) {
        super(block, properties);
        this.diagonal = diagonal;
    }

    public boolean isDiagonal() {
        return diagonal;
    }
}

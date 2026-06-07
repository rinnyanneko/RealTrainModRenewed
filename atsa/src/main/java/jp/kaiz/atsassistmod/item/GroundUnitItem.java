package jp.kaiz.atsassistmod.item;

import jp.kaiz.atsassistmod.block.GroundUnitBlock;
import jp.kaiz.atsassistmod.block.GroundUnitType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One creative item per ground-unit variant (matching the original's 14 metadata
 * items). Places the ground-unit block with {@link GroundUnitBlock#TYPE} set.
 */
public class GroundUnitItem extends BlockItem {
    private final GroundUnitType type;

    public GroundUnitItem(Block block, GroundUnitType type, Properties properties) {
        super(block, properties);
        this.type = type;
    }

    public GroundUnitType getType() {
        return type;
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(GroundUnitBlock.TYPE, type.id);
    }

}

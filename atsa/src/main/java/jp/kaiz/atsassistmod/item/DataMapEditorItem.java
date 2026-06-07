package jp.kaiz.atsassistmod.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * DataMap editor tool. The original opened a GUI when sneak-right-clicking a block.
 * The editor screen is wired in the GUI stage (TODO).
 */
public class DataMapEditorItem extends Item {
    public DataMapEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() && context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            // TODO(gui): open GUIDataMapEditor for the clicked position.
        }
        return InteractionResult.PASS;
    }
}

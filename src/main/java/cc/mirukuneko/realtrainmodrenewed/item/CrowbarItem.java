package cc.mirukuneko.realtrainmodrenewed.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CrowbarItem extends Item {
    public CrowbarItem() {
        this(new Properties().stacksTo(1));
    }

    public CrowbarItem(Properties properties) {
        super(properties);
    }

    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        // legacy同様、バールで通常ブロックを掘れないようにする。
        return false;
    }
}

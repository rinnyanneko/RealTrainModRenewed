package cc.mirukuneko.realtrainmodrenewed.item;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems;
import cc.mirukuneko.realtrainmodrenewed.block.InstalledObjectBlock;
import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock;
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock;
import cc.mirukuneko.realtrainmodrenewed.block.SignalRemoteBlock;
import cc.mirukuneko.realtrainmodrenewed.block.SignalStateBlock;
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

public class CrowbarItem extends Item {
    public CrowbarItem() {
        this(new Properties().stacksTo(1));
    }

    public CrowbarItem(Properties properties) {
        super(properties);
    }

    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return state.getBlock() instanceof RailCollisionBlock
            || state.getBlock() instanceof LargeRailCoreBlock
            || state.getBlock() instanceof InstalledObjectBlock
            || state.getBlock() instanceof SignalRemoteBlock
            || state.getBlock() instanceof SignalStateBlock;
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null
            || (!player.getMainHandItem().is(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                && !player.getOffhandItem().is(RealTrainModRenewedItems.CROWBAR_ITEM.get()))) {
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof TrainEntity
            || target instanceof TrainBogieEntity
            || target instanceof TrainSeatEntity
            || target instanceof CarEntity) {
            return;
        }
        event.setCanceled(true);
    }
}

package jp.kaiz.atsassistmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Opens the train-protection selector screen on use. The original opened a GUI via
 * {@code player.openGui}; the screen itself is wired in the GUI stage (TODO).
 */
public class TrainProtectionSelectorItem extends Item {
    public TrainProtectionSelectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            jp.kaiz.atsassistmod.client.ATSAModClientHooks.openTrainProtectionSelector();
        }
        return InteractionResult.SUCCESS;
    }
}

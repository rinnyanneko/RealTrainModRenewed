package com.portofino.realtrainmodunofficial.compat.atsassist.item;

import net.minecraft.network.chat.Component;
import com.portofino.realtrainmodunofficial.ClientHooks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class TrainProtectionSelectorItem extends Item {
    public TrainProtectionSelectorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            ClientHooks.openAtsaTrainToolScreen("protection");
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        if (level.isClientSide()) {
            ClientHooks.openAtsaTrainToolScreen("protection");
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}

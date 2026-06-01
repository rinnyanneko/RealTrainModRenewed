package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class RailItem extends Item {
    public RailItem() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get()) != null) {
            // コピー済み/調整済みレールは、空振り右クリックで選択UIへ戻さず、そのまま保持する。
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            ClientHooks.openRailSelectScreen(player, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get()) == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
            // バニラのブロック設置と同様、クリックした面の隣(地面の上)を基準位置にする。
            // クリックした地面ブロックそのものを渡すとレールが1ブロック低く=地面にめり込んで
            // 削れて見えるため。コピー元レールのコアも地面の1つ上にあったので +1 で高さが合う。
            BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
            boolean created = MarkerBlock.placeCopiedRailAt(level, placePos, player, stack, selectedId);
            if (created && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return created ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            RailDefinition def = RailRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name)
                .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.none")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class InstalledObjectItem extends Item implements ModelSelectableItem {
    private final InstalledObjectCategory category;

    public InstalledObjectItem(InstalledObjectCategory category) {
        super(new Properties());
        this.category = category;
    }

    public InstalledObjectCategory getCategory() {
        return category;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientHooks.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), category);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(selectedId);
        if (definition == null || definition.getCategory() != category) {
            if (level.isClientSide) {
                ClientHooks.openInstalledObjectSelectScreen(player, stack, category);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
        BlockState state = level.getBlockState(placePos);
        if (!state.canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.setBlock(placePos, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), category, player.getYRot());
                if (category == InstalledObjectCategory.SIGNAL) {
                    // 当たり判定はそのままで、見た目だけ「クリックした柱」の中へ押し込む。
                    // プレイヤー向きではなく設置面基準にすると、どの向きから置いても必ず埋まる。
                    // ただし斜め向きのモデルは投影幅が少し広く見えるので、押し込み量を少し弱める。
                    double yawRad = Math.toRadians(player.getYRot());
                    double faceX = context.getClickedFace().getStepX();
                    double faceZ = context.getClickedFace().getStepZ();
                    double facingDot = Math.abs((-Math.sin(yawRad) * faceX) + (Math.cos(yawRad) * faceZ));
                    double embedDepth = facingDot < 0.85D ? 0.905D : 0.92D;
                    double inwardX = -faceX * embedDepth;
                    double inwardY = -context.getClickedFace().getStepY() * embedDepth;
                    double inwardZ = -faceZ * embedDepth;
                    blockEntity.setRenderOffset(inwardX, inwardY, inwardZ);
                } else {
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                }
                level.sendBlockUpdated(placePos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            InstalledObjectDefinition def = InstalledObjectRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.none").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public List<SelectableModelInfo> getSelectableModels() {
        return InstalledObjectRegistry.getByCategory(category).stream()
            .map(def -> new SelectableModelInfo(def.getId(), def.getDisplayName(), def.getPackName(), def.getButtonTexture()))
            .toList();
    }
}

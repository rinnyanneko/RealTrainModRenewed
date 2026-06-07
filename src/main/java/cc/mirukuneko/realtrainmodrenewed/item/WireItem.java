package cc.mirukuneko.realtrainmodrenewed.item;

import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlocks;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents;
import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat;
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class WireItem extends Item implements ModelSelectableItem {
    public WireItem() {
        this(new Properties());
    }

    public WireItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientHooks.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), InstalledObjectCategory.WIRE);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        String selectedId = LegacyItemStackBridge.getSelectedModelId(stack);
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(selectedId);
        if (definition == null || definition.getCategory() != InstalledObjectCategory.WIRE) {
            if (level.isClientSide()) {
                ClientHooks.openInstalledObjectSelectScreen(player, stack, InstalledObjectCategory.WIRE);
            }
            return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
        }

        BlockPos clickedPos = context.getClickedPos();
        if (!(level.getBlockEntity(clickedPos) instanceof InstalledObjectBlockEntity clicked)
            || (clicked.getCategory() != InstalledObjectCategory.INSULATOR
                && clicked.getCategory() != InstalledObjectCategory.OVERHEAD_LINE_POLE)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.literal("ワイヤーは碍子同士でのみ設置できます"));
            }
            return InteractionResult.FAIL;
        }

        CompoundTag startTag = stack.get(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get());
        if (startTag == null || !startTag.contains("X")) {
            if (!level.isClientSide()) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("X", clickedPos.getX());
                tag.putInt("Y", clickedPos.getY());
                tag.putInt("Z", clickedPos.getZ());
                stack.set(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get(), tag);
                player.sendOverlayMessage(Component.literal("始点の碍子を記録しました"));
            }
            return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
        }

        BlockPos startPos = new BlockPos(NbtCompat.getInt(startTag, "X"), NbtCompat.getInt(startTag, "Y"), NbtCompat.getInt(startTag, "Z"));
        if (startPos.equals(clickedPos)) {
            if (!level.isClientSide()) {
                stack.remove(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get());
                player.sendOverlayMessage(Component.literal("ワイヤー設置を解除しました"));
            }
            return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
        }

        BlockPos mid = new BlockPos((startPos.getX() + clickedPos.getX()) >> 1, (startPos.getY() + clickedPos.getY()) >> 1, (startPos.getZ() + clickedPos.getZ()) >> 1);
        BlockState state = level.getBlockState(mid);
        if (!state.canBeReplaced()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.literal("ワイヤー中央にブロックがあるため設置できません"));
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            level.setBlock(mid, RealTrainModRenewedBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(mid) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), InstalledObjectCategory.WIRE, player.getYRot());
                blockEntity.setWireEndpoints(startPos, clickedPos);
                level.sendBlockUpdated(mid, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
            stack.remove(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.sendOverlayMessage(Component.literal("ワイヤーを設置しました"));
        }
        return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        String selectedId = LegacyItemStackBridge.getSelectedModelId(stack);
        if (selectedId != null && !selectedId.isBlank()) {
            InstalledObjectDefinition def = InstalledObjectRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.accept(Component.translatable("tooltip.realtrainmodrenewed.model.selected", name).withStyle(ChatFormatting.GRAY));
        } else {
            lines.accept(Component.translatable("tooltip.realtrainmodrenewed.model.none").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public List<SelectableModelInfo> getSelectableModels() {
        return InstalledObjectRegistry.getByCategory(InstalledObjectCategory.WIRE).stream()
            .map(def -> new SelectableModelInfo(def.getId(), def.getDisplayName(), def.getPackName(), def.getButtonTexture()))
            .toList();
    }
}


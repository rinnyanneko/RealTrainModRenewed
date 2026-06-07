package cc.mirukuneko.realtrainmodrenewed.item;

import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge;
import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity;
import cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class CarItem extends Item {
    public CarItem() {
        this(new Properties().stacksTo(1));
    }

    public CarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        String selectedId = LegacyItemStackBridge.getSelectedModelId(stack);
        // モデル未選択時は spawn せずに直接選択画面を開く。
        // useOn で PASS しても use() は自動では呼ばれないため、ここで client 側に
        // フックして選択画面を開かないと UI が出ないまま。
        if (selectedId == null || selectedId.isBlank()) {
            if (level.isClientSide() && context.getPlayer() != null) {
                ClientHooks.openCarSelectScreen(context.getPlayer(), stack);
            }
            return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
        }
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        if (def == null || !def.isCarType()) {
            if (level.isClientSide() && context.getPlayer() != null) {
                ClientHooks.openCarSelectScreen(context.getPlayer(), stack);
            }
            return ((level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER));
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        Vec3 spawnPos = Vec3.atBottomCenterOf(pos.above());
        EntityType<CarEntity> type = RealTrainModRenewedEntities.CAR.get();
        CarEntity car = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (car == null) {
            return InteractionResult.FAIL;
        }
        car.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0f;
        car.setYRot(yaw);
        car.setXRot(0f);
        car.setVehicleId(selectedId);
        level.addFreshEntity(car);
        return ((false) ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientHooks.openCarSelectScreen(player, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        String selectedId = LegacyItemStackBridge.getSelectedModelId(stack);
        if (selectedId != null && !selectedId.isBlank()) {
            VehicleDefinition def = VehicleRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.selected", name));
        } else {
            tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.none"));
        }
    }
}


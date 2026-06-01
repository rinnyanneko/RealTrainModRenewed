package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.formation.TrainFormation;
import com.portofino.realtrainmodunofficial.formation.TrainFormationData;
import com.portofino.realtrainmodunofficial.rail.util.RailMap;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public class TrainVehicleItem extends Item {
    private static final int SPAWN_COOLDOWN_TICKS = 4;
    private static final double RAYCAST_DISTANCE = 5.0;

    public TrainVehicleItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // スニーク時は常に選択画面を開く。通常時はレールを見ていなければ開く。
            if (player.isShiftKeyDown() || !isLookingAtRail(level, player)) {
                ClientHooks.openTrainSelectScreen(player, stack);
            }
            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.success(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (trySpawnTrain(level, player, stack)) {
            player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        if (trySpawnTrainAtTarget(level, player, context.getItemInHand(), context.getClickedPos())) {
            player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    private boolean isLookingAtRail(Level level, Player player) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(RAYCAST_DISTANCE));
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        return isRailBlock(level, pos);
    }

    private boolean trySpawnTrain(Level level, Player player, ItemStack stack) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(RAYCAST_DISTANCE));
        BlockPos targetPos = null;

        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 targetPoint = null;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            if (isRailBlock(level, pos)) {
                targetPos = pos;
                targetPoint = hit.getLocation();
            }
        }

        if (targetPos == null) {
            targetPos = findNearestBlockAlongRay(level, start, end);
            if (targetPos != null) {
                targetPoint = Vec3.atCenterOf(targetPos);
            }
        }

        if (targetPos == null) {
            return false;
        }

        return trySpawnTrainAtTarget(level, player, stack, targetPos, targetPoint);
    }

    private boolean trySpawnTrainAtTarget(Level level, Player player, ItemStack stack, BlockPos targetPos) {
        return trySpawnTrainAtTarget(level, player, stack, targetPos, Vec3.atCenterOf(targetPos));
    }

    private boolean trySpawnTrainAtTarget(Level level, Player player, ItemStack stack, BlockPos targetPos, Vec3 targetPoint) {
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }
        if (def == null) {
            player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.train.must_be_on_rail"), true);
            return false;
        }

        RailSpawnData spawnData = findNearestRailSpawn(level, targetPos, targetPoint, player.getYRot());
        if (spawnData == null) {
            player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.train.must_be_on_rail"), true);
            return false;
        }
        double spawnY = spawnData.y();
        if (isOccupiedSpawnArea(level, spawnData.x(), spawnY + 0.25D, spawnData.z(), spawnData.yaw(), def)) {
            player.displayClientMessage(Component.literal("この場所には既に電車があります。別の場所を選んでください。"), true);
            return false;
        }

        TrainEntity train = TrainEntity.create(level, def.getId(), spawnData.x(), spawnY, spawnData.z(), spawnData.yaw(), def.getTrainDistance());
        if (train == null) {
            return false;
        }
        train.initializeOnRail(spawnData.map(), spawnData.split(), spawnData.index());

        level.addFreshEntity(train);
        return true;
    }

    private boolean isOccupiedSpawnArea(Level level, double x, double y, double z, float yaw, VehicleDefinition def) {
        double halfLength = Math.max(2.5, getSpawnHalfLength(def));
        double halfWidth = getSpawnHalfWidth(def);
        double radius = Math.max(halfLength, halfWidth) + 1.0D;
        var bounds = new net.minecraft.world.phys.AABB(
            x - radius,
            y - 0.75,
            z - radius,
            x + radius,
            y + 4.0,
            z + radius
        );
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            TrainEntity.purgeDanglingTrainResidue(serverLevel, bounds);
        }
        List<TrainEntity> overlaps = level.getEntitiesOfClass(TrainEntity.class, bounds, entity -> entity != null && entity.isAlive() && !entity.isRemoved()).stream()
            .filter(entity -> Math.abs(entity.getY() - y) <= 3.5D)
            .filter(entity -> rectanglesOverlap(
                x, z, yaw, halfWidth, halfLength,
                entity.getX(), entity.getZ(), entity.getYRot(),
                entity.getBodyHalfWidthForPlacement() * 0.8D,
                entity.getBodyHalfLengthForPlacement() * 0.9D
            ))
            .toList();
        if (!overlaps.isEmpty()) {
            logSpawnOccupancy(level, "train_vehicle_item", x, y, z, yaw, halfWidth, halfLength, overlaps);
            return true;
        }
        return false;
    }

    private static void logSpawnOccupancy(Level level, String source, double x, double y, double z, float yaw, double halfWidth, double halfLength, List<TrainEntity> overlaps) {
        StringBuilder entities = new StringBuilder();
        for (TrainEntity entity : overlaps) {
            if (entities.length() > 0) {
                entities.append(" | ");
            }
            entities.append(String.format(
                Locale.ROOT,
                "%s uuid=%s pos=(%.2f,%.2f,%.2f) yaw=%.1f half=(%.2f,%.2f) removed=%s alive=%s",
                entity.getVehicleId(),
                entity.getUUID(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYRot(),
                entity.getBodyHalfWidthForPlacement(),
                entity.getBodyHalfLengthForPlacement(),
                entity.isRemoved(),
                entity.isAlive()
            ));
        }
        RealTrainModUnofficial.LOGGER.warn(
            String.format(
                Locale.ROOT,
                "Spawn blocked [%s] level=%s spawn=(%.2f,%.2f,%.2f) yaw=%.1f half=(%.2f,%.2f) overlaps=%s",
                source,
                level.dimension().location(),
                x, y, z, yaw, halfWidth, halfLength,
                entities
            )
        );
    }

    private static boolean rectanglesOverlap(
        double ax, double az, float ayaw, double ahx, double ahz,
        double bx, double bz, float byaw, double bhx, double bhz
    ) {
        double[][] axes = new double[][]{
            axisFromYaw(ayaw),
            perpendicularAxisFromYaw(ayaw),
            axisFromYaw(byaw),
            perpendicularAxisFromYaw(byaw)
        };
        double dx = bx - ax;
        double dz = bz - az;
        for (double[] axis : axes) {
            double centerProjection = Math.abs(dx * axis[0] + dz * axis[1]);
            double aExtent = projectedExtent(ahx, ahz, ayaw, axis);
            double bExtent = projectedExtent(bhx, bhz, byaw, axis);
            if (centerProjection > aExtent + bExtent) {
                return false;
            }
        }
        return true;
    }

    private static double[] axisFromYaw(float yaw) {
        double yawRad = Math.toRadians(-yaw);
        return new double[]{Math.cos(yawRad), Math.sin(yawRad)};
    }

    private static double[] perpendicularAxisFromYaw(float yaw) {
        double[] axis = axisFromYaw(yaw);
        return new double[]{-axis[1], axis[0]};
    }

    private static double projectedExtent(double halfWidth, double halfLength, float yaw, double[] axis) {
        double[] forward = axisFromYaw(yaw);
        double[] side = perpendicularAxisFromYaw(yaw);
        return Math.abs(axis[0] * side[0] + axis[1] * side[1]) * halfWidth
            + Math.abs(axis[0] * forward[0] + axis[1] * forward[1]) * halfLength;
    }

    private static double getSpawnHalfLength(VehicleDefinition def) {
        if (def == null) {
            return 2.25D;
        }
        double halfLength = Math.max(1.75D, def.getTrainDistance() * 0.5D);
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfLength = Math.max(halfLength, Math.abs(bogie.position().z) + 0.95D);
        }
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfLength = Math.max(halfLength, Math.abs(seat.z) + 0.95D);
        }
        return halfLength;
    }

    private static double getSpawnHalfWidth(VehicleDefinition def) {
        if (def == null) {
            return 1.1D;
        }
        double halfWidth = 1.1D;
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfWidth = Math.max(halfWidth, Math.abs(seat.x) + 0.3D);
        }
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfWidth = Math.max(halfWidth, Math.abs(bogie.position().x) + 0.45D);
        }
        return halfWidth;
    }

    private BlockPos findNearestBlockAlongRay(Level level, Vec3 start, Vec3 end) {
        int samples = 8;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 pos = start.add(end.subtract(start).scale(t));
            BlockPos blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
            if (isRailBlock(level, blockPos)) {
                return blockPos;
            }
        }
        return null;
    }

    private boolean isRailBlock(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof LargeRailCoreBlock) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity) {
            return true;
        }
        return false;
    }

    private static RailSpawnData findNearestRailSpawn(Level level, BlockPos clickedPos, Vec3 clickedPoint, float preferredYaw) {
        RailMap clickedRailMap = getRailMapAt(level, clickedPos, clickedPoint);
        if (clickedRailMap != null) {
            return findNearestPointOnMap(clickedRailMap, clickedPoint, preferredYaw);
        }

        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int radius = 16;
        double cx = clickedPoint.x;
        double cy = clickedPoint.y;
        double cz = clickedPoint.z;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = clickedPos.offset(dx, dy, dz);
                    RailMap map = getRailMapAt(level, pos, clickedPoint);
                    if (map == null) continue;
                    int max = getSpawnSplit(map);
                    for (int i = 0; i <= max; i++) {
                        double[] posData = map.getRailPos(max, i);
                        double x = posData[1];
                        double y = map.getRailHeight(max, i);
                        double z = posData[0];
                        double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                        if (d2 < bestDistSq) {
                            bestDistSq = d2;
                            best = createSpawnData(map, max, i, preferredYaw);
                        }
                    }
                }
            }
        }
        return bestDistSq <= 64.0 ? best : null;
    }

    private static RailMap getRailMapAt(Level level, BlockPos pos) {
        return getRailMapAt(level, pos, Vec3.atCenterOf(pos));
    }

    private static RailMap getRailMapAt(Level level, BlockPos pos, Vec3 targetPoint) {
        if (level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return getNearestRailMap(core, targetPoint);
        }
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level.getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return getNearestRailMap(core, targetPoint);
            }
        }
        return null;
    }

    private static RailMap getNearestRailMap(LargeRailCoreBlockEntity core, Vec3 targetPoint) {
        RailMap[] maps = core.getAllRailMaps();
        if (maps.length == 0) return null;
        if (maps.length == 1) return maps[0];
        double cx = targetPoint.x;
        double cy = targetPoint.y;
        double cz = targetPoint.z;
        RailMap best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (RailMap map : maps) {
            int max = getSpawnSplit(map);
            for (int i = 0; i <= max; i++) {
                double[] posData = map.getRailPos(max, i);
                double x = posData[1];
                double y = map.getRailHeight(max, i);
                double z = posData[0];
                double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    best = map;
                }
            }
        }
        return best;
    }

    private static RailSpawnData findNearestPointOnMap(RailMap map, Vec3 clickedPoint, float preferredYaw) {
        if (map == null) return null;
        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int max = getSpawnSplit(map);
        double cx = clickedPoint.x;
        double cy = clickedPoint.y;
        double cz = clickedPoint.z;
        for (int i = 0; i <= max; i++) {
            double[] posData = map.getRailPos(max, i);
            double x = posData[1];
            double y = map.getRailHeight(max, i);
            double z = posData[0];
            double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = createSpawnData(map, max, i, preferredYaw);
            }
        }
        return best;
    }

    private static RailSpawnData createSpawnData(RailMap map, int max, int index, float preferredYaw) {
        double[] p = map.getRailPos(max, index);
        double x = p[1];
        double y = map.getRailHeight(max, index);
        double z = p[0];
        float yaw = choosePreferredRailYaw(map.getRailYaw(max, index), preferredYaw);
        return new RailSpawnData(map, max, index, x, y, z, yaw);
    }

    private static float choosePreferredRailYaw(float railYaw, float preferredYaw) {
        float forwardDiff = Math.abs(Mth.wrapDegrees(railYaw - preferredYaw));
        float reverseYaw = Mth.wrapDegrees(railYaw + 180.0F);
        float reverseDiff = Math.abs(Mth.wrapDegrees(reverseYaw - preferredYaw));
        return reverseDiff < forwardDiff ? reverseYaw : railYaw;
    }

    private static int getSpawnSplit(RailMap map) {
        if (map == null) {
            return 64;
        }
        return Math.max(96, RailMap.curveSplitForLength(map.getHorizontalPathLength()) * 6);
    }

    private record RailSpawnData(RailMap map, int split, int index, double x, double y, double z, float yaw) {
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        TrainFormation formation = TrainFormationData.getFormation(stack);
        if (formation != null && !formation.isEmpty()) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.realtrainmodunofficial.train_formation.cars", formation.getCarCount()));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.realtrainmodunofficial.train_formation.formation", formation.getDisplayName()));
        } else {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.realtrainmodunofficial.train_formation.empty"));
        }

        String selectedModel = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (selectedModel != null && !selectedModel.isBlank()) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.realtrainmodunofficial.selected_model", selectedModel)
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }
}

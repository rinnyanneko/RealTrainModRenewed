package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.rail.util.RailMap;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public class TrainItem extends Item {
    public enum Category {
        ELECTRIC,
        DIESEL,
        TEST
    }

    private final Category category;

    public TrainItem() {
        this(Category.ELECTRIC);
    }

    public TrainItem(Category category) {
        super(new Properties());
        this.category = category == null ? Category.ELECTRIC : category;
    }

    /** スポーンクールダウン: 0.2秒 = 4 ticks */
    private static final int SPAWN_COOLDOWN_TICKS = 4;

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // クールダウン中はスポーン不可
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        if (def == null || !accepts(def)) {
            def = VehicleRegistry.getAll().stream()
                .filter(this::accepts)
                .findFirst()
                .orElse(null);
        }
        if (def == null) {
            return InteractionResult.PASS;
        }
        Vec3 clickedPoint = context.getClickLocation();
        RailSpawnData spawnData = findNearestRailSpawn(level, context.getClickedPos(), clickedPoint, player.getYRot());
        if (spawnData == null) {
            player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.train.must_be_on_rail"), true);
            return InteractionResult.FAIL;
        }
        if (isOccupiedSpawnArea(level, spawnData.x(), spawnData.y() + 0.25D, spawnData.z(), spawnData.yaw(), def)) {
            player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.train.already_exists"), true);
            return InteractionResult.FAIL;
        }
        TrainEntity train = TrainEntity.create(level, def.getId(), spawnData.x(), spawnData.y(), spawnData.z(), spawnData.yaw(), def.getTrainDistance());
        if (train == null) {
            return InteractionResult.PASS;
        }
        train.initializeOnRail(spawnData.map(), spawnData.split(), spawnData.index());
        level.addFreshEntity(train);
        // クールダウン付与（サーバー側。クライアントにも自動同期される）
        player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientHooks.openTrainSelectScreen(player, player.getItemInHand(hand), category);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            VehicleDefinition def = VehicleRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name));
        } else {
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.model.none"));
        }
    }

    public Category getCategory() {
        return category;
    }

    public boolean accepts(VehicleDefinition definition) {
        return accepts(category, definition);
    }

    public static boolean accepts(Category category, VehicleDefinition definition) {
        if (definition == null || definition.isCarType()) {
            return false;
        }
        String key = (safe(definition.getId()) + " " + safe(definition.getDisplayName()) + " "
            + safe(definition.getModelFile()) + " " + safe(definition.getVehicleType())).toLowerCase(java.util.Locale.ROOT);
        String type = safe(definition.getVehicleType()).toUpperCase(java.util.Locale.ROOT);
        boolean test = "TC".equals(type) || key.contains("test") || key.contains("shader") || key.contains("試験");
        return switch (category == null ? Category.ELECTRIC : category) {
            case TEST -> test;
            case DIESEL -> false;
            case ELECTRIC -> !test;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isOccupiedSpawnArea(Level level, double x, double y, double z, float yaw, VehicleDefinition def) {
        double halfLength = Math.max(1.75D, def.getTrainDistance() * 0.5D);
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfLength = Math.max(halfLength, Math.abs(bogie.position().z) + 0.95D);
        }
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfLength = Math.max(halfLength, Math.abs(seat.z) + 0.95D);
        }
        double halfWidth = getSpawnHalfWidth(def);
        double radius = Math.max(halfLength, halfWidth) + 1.0D;
        var bounds = new net.minecraft.world.phys.AABB(
            x - radius,
            y - 0.75D,
            z - radius,
            x + radius,
            y + 4.0D,
            z + radius
        );
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            TrainEntity.purgeDanglingTrainResidue(serverLevel, bounds);
        }
        final double finalHalfLength = halfLength;
        final double finalHalfWidth = halfWidth;
        List<TrainEntity> overlaps = level.getEntitiesOfClass(TrainEntity.class, bounds, entity -> entity != null && entity.isAlive() && !entity.isRemoved()).stream()
            .filter(entity -> Math.abs(entity.getY() - y) <= 3.5D)
            .filter(entity -> rectanglesOverlap(
                x, z, yaw, finalHalfWidth, finalHalfLength,
                entity.getX(), entity.getZ(), entity.getYRot(),
                entity.getBodyHalfWidthForPlacement() * 0.8D,
                entity.getBodyHalfLengthForPlacement() * 0.9D
            ))
            .toList();
        if (!overlaps.isEmpty()) {
            logSpawnOccupancy(level, "train_item", x, y, z, yaw, finalHalfWidth, finalHalfLength, overlaps);
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

    private static double getSpawnHalfWidth(VehicleDefinition def) {
        double halfWidth = 1.1D;
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfWidth = Math.max(halfWidth, Math.abs(seat.x) + 0.3D);
        }
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfWidth = Math.max(halfWidth, Math.abs(bogie.position().x) + 0.45D);
        }
        return halfWidth;
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
                    BlockPos p = clickedPos.offset(dx, dy, dz);
                    RailMap map = getRailMapAt(level, p, clickedPoint);
                    if (map == null) continue;
                    int max = getSpawnSplit(map);
                    for (int i = 0; i <= max; i++) {
                        double[] pos = map.getRailPos(max, i);
                        double x = pos[1];
                        double y = map.getRailHeight(max, i);
                        double z = pos[0];
                        double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                        if (d2 < bestDistSq) {
                            bestDistSq = d2;
                            best = createSpawnData(map, max, i, preferredYaw);
                        }
                    }
                }
            }
        }
        // 吸着距離を約2ブロックに制限する。以前は 8 ブロック(64)圏内の最寄りレールを掴むため、
        // 隣の平行レールに列車が吸い付いていた (ユーザー報告)。クリックしたレール付近にのみ置く。
        return bestDistSq <= 4.0 ? best : null;
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
        // 道床ブロック(BallastBlock)もレールコアを保持する。道床はカーブ全長に敷かれるため、
        // レール中央の道床をクリックしても列車設置のレール判定が効く (ユーザー報告対応)。
        if (level.getBlockEntity(pos) instanceof com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity ballast) {
            BlockPos corePos = ballast.getCorePos();
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
            double[] pos = map.getRailPos(max, i);
            double x = pos[1];
            double y = map.getRailHeight(max, i);
            double z = pos[0];
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
}

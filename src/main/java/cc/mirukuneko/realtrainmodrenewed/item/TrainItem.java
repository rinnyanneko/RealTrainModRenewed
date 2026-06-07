package cc.mirukuneko.realtrainmodrenewed.item;

import cc.mirukuneko.realtrainmodrenewed.blockentity.BallastBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents;
import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class TrainItem extends Item {
    private static final double PLACEMENT_OCCUPANCY_HALF_WIDTH = 0.45D;

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
        this(category, new Properties());
    }

    public TrainItem(Category category, Properties properties) {
        super(properties);
        this.category = category == null ? Category.ELECTRIC : category;
    }

    /** スポーンクールダウン: 0.2秒 = 4 ticks */
    private static final int SPAWN_COOLDOWN_TICKS = 4;

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return findNearestRailSpawn(level, context.getClickedPos(), context.getClickLocation(), player.getYRot()) != null
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        // クールダウン中はスポーン不可
        if (player.getCooldowns().isOnCooldown(context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        String selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get());
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
            player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.train.must_be_on_rail"));
            return InteractionResult.FAIL;
        }
        if (isOccupiedSpawnArea(level, spawnData.x(), spawnData.y() + 0.25D, spawnData.z(), spawnData.yaw(), def, spawnData.map())) {
            player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.train.already_exists"));
            return InteractionResult.FAIL;
        }
        TrainEntity train = TrainEntity.create(level, def.getId(), spawnData.x(), spawnData.y(), spawnData.z(), spawnData.yaw(), def.getTrainDistance());
        if (train == null) {
            return InteractionResult.PASS;
        }
        train.initializeOnRail(spawnData.map(), spawnData.split(), spawnData.index());
        level.addFreshEntity(train);
        // クールダウン付与（サーバー側。クライアントにも自動同期される）
        player.getCooldowns().addCooldown(context.getItemInHand(), SPAWN_COOLDOWN_TICKS);
        return ((false) ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            if (!isLookingAtBlock(level, player)) {
                ClientHooks.openTrainSelectScreen(player, player.getItemInHand(hand), category);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isLookingAtBlock(Level level, Player player) {
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        String selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            VehicleDefinition def = VehicleRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.selected", name));
        } else {
            tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.none"));
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
        String type = safe(definition.getVehicleType()).toUpperCase(java.util.Locale.ROOT);
        boolean test = "TEST".equals(type);
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
        return isOccupiedSpawnArea(level, x, y, z, yaw, def, null);
    }

    private boolean isOccupiedSpawnArea(Level level, double x, double y, double z, float yaw, VehicleDefinition def,
                                        RailMap spawnMap) {
        double halfLength = Math.max(1.75D, def.getTrainDistance());
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfLength = Math.max(halfLength, Math.abs(bogie.position().z) + 0.95D);
        }
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfLength = Math.max(halfLength, Math.abs(seat.z) + 0.95D);
        }
        double halfWidth = PLACEMENT_OCCUPANCY_HALF_WIDTH;
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
            // 別レール上の列車は占有とみなさない（隣接レールへの真横配置を許可）。
            .filter(entity -> spawnMap == null || entity.getActiveRailMap() == null || entity.getActiveRailMap() == spawnMap)
            .filter(entity -> rectanglesOverlap(
                x, z, yaw, finalHalfWidth, finalHalfLength,
                entity.getX(), entity.getZ(), entity.getYRot(),
                PLACEMENT_OCCUPANCY_HALF_WIDTH,
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
        RealTrainModRenewed.LOGGER.warn(
            String.format(
                Locale.ROOT,
                "Spawn blocked [%s] level=%s spawn=(%.2f,%.2f,%.2f) yaw=%.1f half=(%.2f,%.2f) overlaps=%s",
                source,
                level.dimension().identifier().toString(),
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
        if (bestDistSq <= 4.0 && best != null) {
            return best;
        }
        // フォールバック: 検出ブロック(当たり判定/道床)が無いレール(ballastWidth=0 や本修正前に
        // 敷設した既存レール)は、上のブロック走査ではコア(=端)から16ブロック以内しか見つからず
        // 「レール端しか列車が置けない」状態になる。周辺チャンクの BlockEntity からレールコアを
        // 直接拾い、クリック近傍を通るレールを探すことで、コア位置・レール長・敷設時期に依らず
        // レール全長のどこでも設置できるようにする(本家RTM準拠の挙動)。
        return scanNearbyCoresForSpawn(level, clickedPos, clickedPoint, preferredYaw);
    }

    /**
     * クリック位置周辺のチャンクに含まれるレールコア(LargeRailCoreBlockEntity)を直接走査し、
     * クリック点に最も近いレール上の点(約2ブロック以内)を返す。検出用の当たり判定ブロックが
     * 無いレールでも、コアの位置に依らずレール全長のどこでも列車を設置できるようにするための保険。
     */
    private static RailSpawnData scanNearbyCoresForSpawn(Level level, BlockPos clickedPos, Vec3 clickedPoint, float preferredYaw) {
        final int chunkRadius = 3; // ±3 チャンク(約 112 ブロック)を走査。コアは疎なので軽量。
        int baseChunkX = clickedPos.getX() >> 4;
        int baseChunkZ = clickedPos.getZ() >> 4;
        RailSpawnData best = null;
        double bestDistSq = 4.0; // 吸着しきい値(約2ブロック)
        java.util.Set<BlockPos> visitedCores = new java.util.HashSet<>();
        for (int cx = baseChunkX - chunkRadius; cx <= baseChunkX + chunkRadius; cx++) {
            for (int cz = baseChunkZ - chunkRadius; cz <= baseChunkZ + chunkRadius; cz++) {
                net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk)) {
                    continue;
                }
                for (net.minecraft.world.level.block.entity.BlockEntity be : levelChunk.getBlockEntities().values()) {
                    if (!(be instanceof LargeRailCoreBlockEntity core) || !core.isLoaded()) {
                        continue;
                    }
                    if (!visitedCores.add(core.getBlockPos())) {
                        continue;
                    }
                    for (RailMap map : core.getAllRailMaps()) {
                        if (map == null) continue;
                        int max = getSpawnSplit(map);
                        for (int i = 0; i <= max; i++) {
                            double[] pos = map.getRailPos(max, i);
                            double dx = pos[1] - clickedPoint.x;
                            double dy = map.getRailHeight(max, i) - clickedPoint.y;
                            double dz = pos[0] - clickedPoint.z;
                            double d2 = dx * dx + dy * dy + dz * dz;
                            if (d2 < bestDistSq) {
                                bestDistSq = d2;
                                best = createSpawnData(map, max, i, preferredYaw);
                            }
                        }
                    }
                }
            }
        }
        return best;
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
        if (level.getBlockEntity(pos) instanceof BallastBlockEntity ballast) {
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

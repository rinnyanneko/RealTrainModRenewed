package cc.mirukuneko.realtrainmodrenewed.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cc.mirukuneko.realtrainmodrenewed.ClientHooks;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.rail.math.CurveMath;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlocks;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents;
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat;
import cc.mirukuneko.realtrainmodrenewed.item.MarkerItem;
import cc.mirukuneko.realtrainmodrenewed.item.RailItem;
import cc.mirukuneko.realtrainmodrenewed.item.WrenchItem;
import cc.mirukuneko.realtrainmodrenewed.rail.RailDefinition;
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMapBasic;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMaker;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailProperties;
import cc.mirukuneko.realtrainmodrenewed.rail.util.SwitchType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MarkerBlock extends BaseEntityBlock {
    public static final MapCodec<MarkerBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.BOOL.fieldOf("is_switch").forGetter(block -> block.isSwitch),
            propertiesCodec()
        ).apply(instance, MarkerBlock::new)
    );
    public static final IntegerProperty FACING = IntegerProperty.create("facing", 0, 7);
    public final boolean isSwitch;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    public static final int SEARCH_DISTANCE = 50;
    public static final int SEARCH_HEIGHT = 10;

    public MarkerBlock(boolean isSwitch, BlockBehaviour.Properties properties) {
        super(properties);
        this.isSwitch = isSwitch;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, 0));
    }

    public MarkerBlock(boolean isSwitch) {
        this(isSwitch, BlockBehaviour.Properties.of()
            .sound(SoundType.STONE)
            .strength(1.0F, 1.0F)
            .noOcclusion()
            .noCollision());
    }

    @Override
    public MapCodec<? extends MarkerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null) return defaultBlockState().setValue(FACING, 0);
        boolean diagonal = shouldPlaceDiagonal(context.getItemInHand().getItem() instanceof MarkerItem markerItem && markerItem.isDiagonal(), player);
        return defaultBlockState().setValue(FACING, computeFacing(player, diagonal));
    }

    private static boolean shouldPlaceDiagonal(boolean forcedDiagonal, Player player) {
        if (forcedDiagonal || player == null) {
            return forcedDiagonal;
        }
        float yaw = Mth.positiveModulo(player.getYRot() + 180.0F, 360.0F);
        float remainder = Mth.positiveModulo(yaw, 90.0F);
        return remainder >= 22.5F && remainder < 67.5F;
    }

    public static int computeFacing(Player player) {
        return computeFacing(player, false);
    }

    public static int computeFacing(Player player, boolean diagonal) {
        float yaw = Mth.positiveModulo(player.getYRot() + 180.0F, 360.0F);
        int baseFacing = diagonal
            ? (Mth.floor(yaw / 90.0F) & 3)
            : (Mth.floor(yaw / 90.0F + 0.5F) & 3);
        return baseFacing + (diagonal ? 4 : 0);
    }

    /** legacy BlockMarker.getMarkerDir */
    public static int getMarkerDir(int facing) {
        int i0 = facing & 3;
        int i1 = ((6 - i0) & 3) * 2;
        if ((facing & 4) != 0) {
            i1 = (i1 + 7) & 7;
        }
        return i1;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // RTM本家互換の使い方: マーカーをレールアイテムで右クリックすると、範囲内の全マーカーを
        // 探索して個数に応じたレール(2個=通常/3個以上=分岐)を生成する。プレビューや WrenchMode は
        // 使わない (RTM の BlockMarker.onBlockActivated → onMarkerActivated と同じ挙動)。
        // RTM互換: マーカーをマーカーアイテムで右クリック → 設定GUI(カント/曲線アンカー)を開く。
        if (stack.getItem() instanceof MarkerItem) {
            if (level.isClientSide()) {
                ClientHooks.openMarkerConfigScreen(pos);
            }
            return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
        }
        if (stack.getItem() instanceof RailItem) {
            if (!level.isClientSide() && state.getBlock() instanceof MarkerBlock block) {
                String selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get());
                int markerCount = block.searchAllMarkers(level, pos).size();
                if (markerCount < 2) {
                    player.sendOverlayMessage(Component.literal("接続できるマーカーが不足しています(2個以上必要)"));
                    return InteractionResult.SUCCESS_SERVER;
                }
                boolean created = block.onMarkerActivated(level, pos, player, true, selectedId);
                if (created) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.sendOverlayMessage(Component.literal("レールを接続しました"));
                } else {
                    player.sendOverlayMessage(Component.literal("ここにはレールを敷けません(障害物や形状を確認)"));
                }
            }
            return (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    public static boolean placeRailFromItem(Level level, BlockPos pos, Player player, ItemStack stack, @Nullable String selectedModelId) {
        ItemStack previewStack = stack;
        CompoundTag startTag = stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get());
        if (startTag == null) {
            ItemStack alternatePreviewStack = WrenchItem.findPlayerPreviewStack(player);
            CompoundTag alternateTag = alternatePreviewStack.isEmpty()
                ? null
                : alternatePreviewStack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get());
            if (alternateTag != null && NbtCompat.getBoolean(alternateTag, "WrenchMode")) {
                previewStack = alternatePreviewStack;
                startTag = alternateTag;
            }
        }
        if (startTag == null || !startTag.contains("X") || !startTag.contains("Y") || !startTag.contains("Z")) {
            // プレビュー無しの右クリックは、近くのマーカー群から従来どおり即敷設へ流す。
            MarkerBlock block = level.getBlockState(pos).getBlock() instanceof MarkerBlock markerBlock ? markerBlock : null;
            if (block != null && block.searchAllMarkers(level, pos).size() >= 2) {
                boolean created = block.onMarkerActivated(level, pos, player, true, selectedModelId);
                if (created) {
                    player.sendOverlayMessage(Component.literal("レールを接続しました"));
                }
                return created;
            }
            player.sendOverlayMessage(Component.literal("接続できるマーカーが不足しています"));
            return false;
        }

        BlockPos startPos = new BlockPos(NbtCompat.getInt(startTag, "X"), NbtCompat.getInt(startTag, "Y"), NbtCompat.getInt(startTag, "Z"));
        boolean branchMode = NbtCompat.getBoolean(startTag, "BranchMode");
        boolean wrenchMode = NbtCompat.getBoolean(startTag, "WrenchMode")
            && (startTag.contains("EndRP") || startTag.contains("RailSegments"));
        if (startPos.equals(pos) && !wrenchMode) {
            previewStack.remove(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get());
            player.sendOverlayMessage(Component.literal("レールプレビューを解除しました"));
            return false;
        }
        BlockEntity endBe = level.getBlockEntity(pos);
        BlockEntity startBe = level.getBlockEntity(startPos);
        if (!wrenchMode && !(endBe instanceof MarkerBlockEntity)) {
            player.sendOverlayMessage(Component.literal("接続元または接続先のマーカーが見つかりません"));
            return false;
        }
        if (wrenchMode && !(startBe instanceof MarkerBlockEntity) && !(startBe instanceof LargeRailCoreBlockEntity) && !startTag.contains("StartRP")) {
            player.sendOverlayMessage(Component.literal("コピー元のレール情報が見つかりません"));
            return false;
        }

        RailPosition start = resolvePreviewStart(startBe, startTag);
        if (start == null) {
            return false;
        }
        RailProperties prop = createRailProperties(player, selectedModelId);
        boolean created;
        if (wrenchMode) {
            created = createRailsFromWrenchPreview(level, startPos, start, startTag, prop, player.getAbilities().instabuild, selectedModelId);
            branchMode = branchMode || WrenchItem.getSegmentList(startTag).size() > 1;
        } else {
            RailPosition end = ((MarkerBlockEntity) endBe).getMarkerRP();
            if (end == null) {
                return false;
            }
            end = applyPreviewOffset(end, startTag);
            start.addHeight((double) (prop.blockHeight - 0.0625F));
            end.addHeight((double) (prop.blockHeight - 0.0625F));
            created = branchMode
                ? createOrAppendBranchRail(level, startPos, copyRailPosition(start), copyRailPosition(end), prop, player.getAbilities().instabuild, selectedModelId)
                : createRail(level, startPos, List.of(copyRailPosition(start), copyRailPosition(end)), prop, true, player.getAbilities().instabuild, selectedModelId);
        }
        if (created) {
            if (branchMode && !wrenchMode) {
                CompoundTag nextTag = startTag.copy();
                nextTag.remove("OffsetX");
                nextTag.remove("OffsetY");
                nextTag.remove("OffsetZ");
                previewStack.set(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get(), nextTag);
                player.sendOverlayMessage(Component.literal("分岐レールを追加しました"));
            } else {
                previewStack.remove(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get());
                player.sendOverlayMessage(Component.literal("レールを接続しました"));
            }
        } else if (!branchMode || wrenchMode) {
            previewStack.remove(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get());
        }
        return created;
    }

    private static boolean createRailsFromWrenchPreview(Level level, BlockPos startPos, RailPosition rawStart,
                                                        CompoundTag startTag, RailProperties prop,
                                                        boolean isCreative, @Nullable String selectedModelId) {
        ListTag segments = WrenchItem.getSegmentList(startTag);
        if (segments.isEmpty()) {
            return false;
        }
        boolean multiple = segments.size() > 1 || NbtCompat.getBoolean(startTag, "BranchMode");
        boolean createdAny = false;
        for (int i = 0; i < segments.size(); i++) {
            CompoundTag segment = NbtCompat.getCompound(segments, i);
            // レンチで保持したアンカーをそのまま各セグメントへ戻すと、確定後の形がぶれにくい。
            RailPosition start = WrenchItem.applyControlHandle(rawStart, segment, true);
            RailPosition endRaw = RailPosition.readFromNBT(NbtCompat.getCompound(segment, "EndRP"));
            if (endRaw == null) {
                continue;
            }
            RailPosition end = WrenchItem.applyControlHandle(endRaw, segment, false);
            start.addHeight((double) (prop.blockHeight - 0.0625F));
            end.addHeight((double) (prop.blockHeight - 0.0625F));
            boolean created = multiple
                ? createOrAppendBranchRail(level, startPos, copyRailPosition(start), copyRailPosition(end), prop, isCreative, selectedModelId)
                : createNormalRail(level, copyRailPosition(start), copyRailPosition(end), prop, true, isCreative, selectedModelId);
            createdAny |= created;
        }
        return createdAny;
    }

    /**
     * 地面に右クリックしたコピー済みレールを、その位置へ平行移動して復元する。
     */
    public static boolean placeCopiedRailAt(Level level, BlockPos targetPos, Player player, ItemStack stack,
                                            @Nullable String selectedModelId) {
        CompoundTag startTag = stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get());
        if (startTag == null || !NbtCompat.getBoolean(startTag, "WrenchMode") || !startTag.contains("StartRP")) {
            return false;
        }

        BlockPos sourcePos = new BlockPos(NbtCompat.getInt(startTag, "X"), NbtCompat.getInt(startTag, "Y"), NbtCompat.getInt(startTag, "Z"));
        int dx = targetPos.getX() - sourcePos.getX();
        int dy = targetPos.getY() - sourcePos.getY();
        int dz = targetPos.getZ() - sourcePos.getZ();

        RailPosition rawStart = RailPosition.readFromNBT(NbtCompat.getCompound(startTag, "StartRP"));
        if (rawStart == null) {
            return false;
        }

        int desiredDir = getMarkerDir(computeFacing(player));
        BlockPos adjustedTargetPos = targetPos;
        int rotationSteps = (desiredDir - (rawStart.direction & 7) + 8) & 7;

        RailPosition rotatedStart = rotateRailPosition(rawStart, sourcePos, rotationSteps);
        BlockPos rotatedStartPos = new BlockPos(rotatedStart.blockX, rotatedStart.blockY, rotatedStart.blockZ);
        int rotatedDx = adjustedTargetPos.getX() - rotatedStartPos.getX();
        int rotatedDy = adjustedTargetPos.getY() - rotatedStartPos.getY();
        int rotatedDz = adjustedTargetPos.getZ() - rotatedStartPos.getZ();
        RailPosition translatedStart = translateRailPosition(rotatedStart, rotatedDx, rotatedDy, rotatedDz);
        CompoundTag translatedTag = startTag.copy();
        translatedTag.putInt("X", adjustedTargetPos.getX());
        translatedTag.putInt("Y", adjustedTargetPos.getY());
        translatedTag.putInt("Z", adjustedTargetPos.getZ());
        translatedTag.put("StartRP", translatedStart.writeToNBT());

        ListTag segments = WrenchItem.getSegmentList(translatedTag);
        ListTag translatedSegments = new ListTag();
        for (int i = 0; i < segments.size(); i++) {
            CompoundTag segment = NbtCompat.getCompound(segments, i).copy();
            RailPosition end = RailPosition.readFromNBT(NbtCompat.getCompound(segment, "EndRP"));
            if (end == null) {
                continue;
            }
            RailPosition rotatedEnd = rotateRailPosition(end, sourcePos, rotationSteps);
            segment.put("EndRP", translateRailPosition(rotatedEnd, rotatedDx, rotatedDy, rotatedDz).writeToNBT());
            translatedSegments.add(segment);
        }
        if (translatedSegments.isEmpty()) {
            return false;
        }
        translatedTag.put("RailSegments", translatedSegments);
        translatedTag.put("EndRP", NbtCompat.getCompound(NbtCompat.getCompound(translatedSegments, 0), "EndRP"));

        RailProperties prop = createRailProperties(player, selectedModelId);
        return createRailsFromWrenchPreview(level, adjustedTargetPos, translatedStart, translatedTag, prop,
            player.getAbilities().instabuild, selectedModelId);
    }

    @Nullable
    private static RailPosition resolvePreviewStart(@Nullable BlockEntity startBe, CompoundTag tag) {
        if (startBe instanceof MarkerBlockEntity startMarker) {
            return startMarker.getMarkerRP();
        }
        if (startBe instanceof LargeRailCoreBlockEntity core) {
            RailPosition first = core.getFirstRailPosition();
            if (first != null) {
                return first;
            }
        }
        if (tag.contains("StartRP")) {
            return RailPosition.readFromNBT(NbtCompat.getCompound(tag, "StartRP"));
        }
        return null;
    }

    private static RailPosition applyPreviewOffset(RailPosition raw, CompoundTag tag) {
        RailPosition copy = copyRailPosition(raw);
        if (tag == null) {
            return copy;
        }
        copy.posX += NbtCompat.getInt(tag, "OffsetX") / 16.0D;
        copy.posY += NbtCompat.getInt(tag, "OffsetY") / 16.0D;
        copy.posZ += NbtCompat.getInt(tag, "OffsetZ") / 16.0D;
        return copy;
    }

    public boolean onMarkerActivated(Level level, BlockPos pos, @Nullable Player player, boolean makeRail) {
        return onMarkerActivated(level, pos, player, makeRail, null);
    }

    /**
     * legacy BlockMarker.onMarkerActivated に相当。
     * 範囲内の全マーカーを収集し、個数に応じてレール種別を決定する:
     *  2個 → 通常レール
     *  3個以上 → 分岐レール（各マーカーペアで通常レールを複数生成）
     */
    public boolean onMarkerActivated(Level level, BlockPos pos, @Nullable Player player, boolean makeRail, @Nullable String selectedModelId) {
        List<RailPosition> rps = searchAllMarkers(level, pos);
        if (rps.size() < 2) return false;

        RailProperties prop = createRailProperties(player, selectedModelId);
        for (RailPosition rp : rps) {
            rp.addHeight((double) (prop.blockHeight - 0.0625F));
        }
        return createRail(level, pos, rps, prop, makeRail, player == null || player.getAbilities().instabuild, selectedModelId);
    }

    /**
     * legacy BlockMarker.searchAllMarker に相当。
     * 範囲内にある全マーカー（自分自身を含む）をスキャンし、legacy と同じ優先度でソートして返す:
     *  1. switchType 降順（分岐マーカー優先）
     *  2. Y 昇順
     *  3. hashCode 昇順（同一 Y のタイブレーカー）
     */
    private List<RailPosition> searchAllMarkers(Level level, BlockPos origin) {
        List<RailPosition> list = new ArrayList<>();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        for (int i = -SEARCH_DISTANCE; i <= SEARCH_DISTANCE; i++) {
            for (int j = -SEARCH_HEIGHT; j <= SEARCH_HEIGHT; j++) {
                for (int k = -SEARCH_DISTANCE; k <= SEARCH_DISTANCE; k++) {
                    BlockPos check = new BlockPos(ox + i, oy + j, oz + k);
                    BlockEntity be = level.getBlockEntity(check);
                    if (be instanceof MarkerBlockEntity me) {
                        RailPosition rp = me.getMarkerRP();
                        if (rp != null) {
                            list.add(copyRailPosition(rp));
                        }
                    }
                }
            }
        }

        // legacy と同じソート順: switchType 降順 → Y 昇順 → hashCode 昇順
        list.sort((a, b) -> {
            if (a.switchType != b.switchType) return b.switchType - a.switchType;
            if (a.blockY != b.blockY) return a.blockY - b.blockY;
            return a.hashCode() - b.hashCode();
        });

        return list;
    }

    private static RailProperties createRailProperties(@Nullable Player player, @Nullable String selectedModelId) {
        RailProperties prop = RailProperties.createDefault();
        RailDefinition selected = (selectedModelId != null && !selectedModelId.isBlank())
            ? RailRegistry.getById(selectedModelId)
            : RailRegistry.getSelected();
        if (selected != null) {
            prop.ballastWidth = Math.max(0, selected.getBallastWidth());
        }
        if (selected != null && selected.getScriptPath() != null) {
            // PackScriptRuntime removed - no script system
        }
        return prop;
    }

    public static boolean createRail(Level level, BlockPos originPos, List<RailPosition> rps, RailProperties prop, boolean makeRail, boolean isCreative) {
        return createRail(level, originPos, rps, prop, makeRail, isCreative, null);
    }

    /**
     * SuperRailBuilder3 等のスクリプトブリッジ用。modelId のレール定義で RailProperties を作り、
     * rps(2個=通常 / 3個以上=分岐)から RTMU ネイティブのレールを敷設する。
     */
    public static boolean buildRailForScript(Level level, List<RailPosition> rps, String modelId) {
        if (level == null || rps == null || rps.size() < 2) {
            return false;
        }
        // SRB の手持ちレールIDが RTMU の RailRegistry に存在しないことがある(別パックの命名)。
        // その場合は選択中レール→先頭レールへフォールバックし、必ず有効な定義で敷設する。
        RailDefinition def =
            RailRegistry.getById(modelId);
        if (def == null) {
            def = RailRegistry.getSelected();
        }
        if (def == null) {
            java.util.List<RailDefinition> all =
                RailRegistry.getAll();
            if (!all.isEmpty()) {
                def = all.get(0);
            }
        }
        String resolvedId = def != null ? def.getId() : modelId;
        RailProperties prop = createRailProperties(null, resolvedId);
        // 当たり判定(RailCollisionBlock)は ballastWidth>0 のときだけ置かれる。SRB敷設では
        // 必ず判定が出るよう、0以下なら既定幅にする(本家でも床面に判定が出る)。
        if (prop.ballastWidth <= 0) {
            prop.ballastWidth = 3;
        }
        RailPosition origin = rps.get(0);
        BlockPos originPos = new BlockPos(origin.blockX, origin.blockY, origin.blockZ);
        return createRail(level, originPos, rps, prop, true, true, resolvedId);
    }

    /**
     * legacy BlockMarker.createRail に相当。
     *  2個 → createNormalRail
     *  3個以上 → createSwitchRail（分岐: 最初のマーカーを起点に各ペアへ通常レールを生成）
     */
    public static boolean createRail(Level level, BlockPos originPos, List<RailPosition> rps, RailProperties prop, boolean makeRail, boolean isCreative, @Nullable String selectedModelId) {
        if (rps.size() == 2) {
            RailPosition rp1 = rps.get(0);
            RailPosition rp2 = rps.get(1);
            RailPosition start = rp2.blockY >= rp1.blockY ? rp1 : rp2;
            RailPosition end   = rp2.blockY >= rp1.blockY ? rp2 : rp1;
            return createNormalRail(level, start, end, prop, makeRail, isCreative, selectedModelId);
        } else if (rps.size() > 2) {
            RailPosition root = rps.getFirst();
            BlockPos switchCorePos = new BlockPos(root.blockX, root.blockY, root.blockZ);
            return createSwitchRail(level, switchCorePos, rps, prop, makeRail, isCreative, selectedModelId);
        }
        return false;
    }

    /**
     * 2マーカー間に1本の通常レールを敷設する。legacy createNormalRail 相当。
     */
    private static boolean createNormalRail(Level level, RailPosition start, RailPosition end,
                                            RailProperties prop, boolean makeRail, boolean isCreative,
                                            @Nullable String selectedModelId) {
        RailMap railMap = new RailMapBasic(start, end);
        RailDefinition selected = resolveRailDef(selectedModelId);

        if (!makeRail || !railMap.canPlaceRail(level, isCreative, prop)) {
            return false;
        }

        BlockPos corePos = new BlockPos(start.blockX, start.blockY, start.blockZ);
        Block coreBlock = RealTrainModRenewedBlocks.LARGE_RAIL_CORE.get();
        level.setBlock(corePos, coreBlock.defaultBlockState(), Block.UPDATE_ALL);

        RealTrainModRenewed.LOGGER.debug(
            "[RTM-DBG] BUILD normalRail start(blk={},{},{} h={} yaw={} posY={}) end(blk={},{},{} h={} yaw={} posY={})",
            start.blockX, start.blockY, start.blockZ, start.height, start.anchorYaw, (float) start.posY,
            end.blockX, end.blockY, end.blockZ, end.height, end.anchorYaw, (float) end.posY);
        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (coreBe instanceof LargeRailCoreBlockEntity core) {
            core.setRailPositions(new RailPosition[]{start, end});
            if (selected != null) core.setRailDefinitionId(selected.getId());
            core.createRailMap();
            core.setChanged();
            level.sendBlockUpdated(corePos, core.getBlockState(), core.getBlockState(), Block.UPDATE_ALL);
        }

        railMap.setRail(level, resolveBallastBlock(selected), corePos.getX(), corePos.getY(), corePos.getZ(), prop);
        placeCollisionBlocks(level, railMap, corePos);
        removeMarkerAt(level, start.blockX, start.blockY, start.blockZ);
        removeMarkerAt(level, end.blockX, end.blockY, end.blockZ);

        if (selected != null && selected.getScriptPath() != null) {
            // PackScriptRuntime removed - no script system
        }
        return true;
    }

    private static boolean createOrAppendBranchRail(Level level, BlockPos corePos, RailPosition start, RailPosition end,
                                                    RailProperties prop, boolean isCreative,
                                                    @Nullable String selectedModelId) {
        RailMap railMap = new RailMapBasic(start, end);
        if (!railMap.canPlaceRail(level, isCreative, prop)) {
            return false;
        }

        RailDefinition selected = resolveRailDef(selectedModelId);
        BlockEntity coreBe = level.getBlockEntity(corePos);
        LargeRailCoreBlockEntity core;
        if (coreBe instanceof LargeRailCoreBlockEntity existingCore) {
            core = existingCore;
            core.appendRailSegment(copyRailPosition(start), copyRailPosition(end));
        } else {
            level.setBlock(corePos, RealTrainModRenewedBlocks.LARGE_RAIL_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            coreBe = level.getBlockEntity(corePos);
            if (!(coreBe instanceof LargeRailCoreBlockEntity createdCore)) {
                return false;
            }
            core = createdCore;
            core.setRailPositions(new RailPosition[]{copyRailPosition(start), copyRailPosition(end)});
            core.createRailMap();
        }

        if (selected != null) {
            core.setRailDefinitionId(selected.getId());
        }
        core.updateSignalStrength(level.getBestNeighborSignal(corePos));
        core.setChanged();
        level.sendBlockUpdated(corePos, core.getBlockState(), core.getBlockState(), Block.UPDATE_ALL);
        railMap.setRail(level, resolveBallastBlock(selected), corePos.getX(), corePos.getY(), corePos.getZ(), prop);
        placeCollisionBlocks(level, railMap, corePos);
        removeMarkerAt(level, end.blockX, end.blockY, end.blockZ);
        return true;
    }

    private static boolean createSwitchRail(Level level, BlockPos originPos, List<RailPosition> rps,
                                            RailProperties prop, boolean makeRail, boolean isCreative,
                                            @Nullable String selectedModelId) {
        List<RailPosition> ordered = new ArrayList<>(rps.size());
        for (RailPosition rp : rps) {
            ordered.add(copyRailPosition(rp));
        }
        RailMaker railMaker = new RailMaker(ordered);
        SwitchType switchType = railMaker.getSwitch();
        if (switchType == null) {
            return false;
        }
        RailMap[] switchMaps = switchType.getAllRailMap();
        if (switchMaps.length == 0) {
            return false;
        }

        List<RailMap> maps = new ArrayList<>(switchMaps.length);
        for (RailMap switchMap : switchMaps) {
            maps.add(switchMap);
        }

        if (!makeRail) {
            return false;
        }

        for (RailMap map : maps) {
            if (!map.canPlaceRail(level, isCreative, prop)) {
                return false;
            }
        }

        BlockPos corePos = originPos;
        Block coreBlock = RealTrainModRenewedBlocks.LARGE_RAIL_CORE.get();
        level.setBlock(corePos, coreBlock.defaultBlockState(), Block.UPDATE_ALL);

        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (!(coreBe instanceof LargeRailCoreBlockEntity coreEntity)) {
            return false;
        }

        coreEntity.setRailPositions(ordered.toArray(new RailPosition[0]));
        RailDefinition selected = resolveRailDef(selectedModelId);
        if (selected != null) {
            coreEntity.setRailDefinitionId(selected.getId());
        }
        coreEntity.createRailMap();
        coreEntity.setChanged();
        level.sendBlockUpdated(corePos, coreEntity.getBlockState(), coreEntity.getBlockState(), Block.UPDATE_ALL);

        for (RailMap map : maps) {
            map.setRail(level, resolveBallastBlock(selected), corePos.getX(), corePos.getY(), corePos.getZ(), prop);
            placeCollisionBlocks(level, map, corePos);
        }

        for (RailPosition rp : rps) {
            removeMarkerAt(level, rp.blockX, rp.blockY, rp.blockZ);
        }

        if (selected != null && selected.getScriptPath() != null) {
            for (int i = 1; i < rps.size(); i++) {
                // PackScriptRuntime removed - no script system
            }
        }
        return true;
    }


    /** レール中心線に沿って薄いコリジョンブロックを配置する（legacy の道床コリジョン相当）。 */
    private static void placeCollisionBlocks(Level level, RailMap railMap, BlockPos corePos) {
        // 当たり判定・レール検出・破壊連動はすべて道床(BallastBlock)が担うようになったため、
        // レール高さに浮く別の判定ブロック(RailCollisionBlock)は設置しない。
        // (ユーザー要望「当たり判定を道床につけて、今浮いてる」)
        if (true) return;
        int split = RailMap.curveSplitForLength(railMap.getHorizontalPathLength());
        int samples = Math.max(16, split + 1);
        Set<BlockPos> placed = new HashSet<>();

        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = railMap.getRailPos(split, j);
            int x = CurveMath.floor(point[1]);
            int z = CurveMath.floor(point[0]);
            int y = (int) railMap.getRailHeight(split, j);
            int railDir = Math.floorMod(Math.round(railMap.getRailYaw(split, j) / 45.0F), 8);
            int sideDir = (railDir + 2) & 7;

            // コリジョン(レール判定)はレール高さ(y)に置く。道床は y-1 に下げたため、
            // y-1 に置くと道床に塞がれて判定ブロックが置けず、レール中央で列車設置判定が
            // 効かなくなる (ユーザー報告「端だけ判定される」)。y に置けば道床と競合せず
            // カーブ全長に不可視の判定ブロックが並び、どこでもレール判定される。
            placeCollisionBlock(level, corePos, placed, new BlockPos(x, y, z));
            placeCollisionBlock(level, corePos, placed,
                new BlockPos(x + getDirStepX(sideDir), y, z + getDirStepZ(sideDir)));
            placeCollisionBlock(level, corePos, placed,
                new BlockPos(x - getDirStepX(sideDir), y, z - getDirStepZ(sideDir)));
        }

        placeCollisionBlocksAtEndpoint(level, railMap, corePos, placed, split, 0);
        placeCollisionBlocksAtEndpoint(level, railMap, corePos, placed, split, split);
    }

    private static void placeCollisionBlocksAtEndpoint(Level level, RailMap railMap, BlockPos corePos, Set<BlockPos> placed, int split, int index) {
        double[] point = railMap.getRailPos(split, index);
        int x = CurveMath.floor(point[1]);
        int z = CurveMath.floor(point[0]);
        int y = (int) railMap.getRailHeight(split, index);
        int railDir = Math.floorMod(Math.round(railMap.getRailYaw(split, index) / 45.0F), 8);
        int sideDir = (railDir + 2) & 7;

        placeCollisionBlock(level, corePos, placed, new BlockPos(x, y, z));
        placeCollisionBlock(level, corePos, placed, new BlockPos(x + getDirStepX(sideDir), y, z + getDirStepZ(sideDir)));
        placeCollisionBlock(level, corePos, placed, new BlockPos(x - getDirStepX(sideDir), y, z - getDirStepZ(sideDir)));
    }

    private static void placeCollisionBlock(Level level, BlockPos corePos, Set<BlockPos> placed, BlockPos pos) {
        if (pos.equals(corePos) || !placed.add(pos)) {
            return;
        }

        Block existing = level.getBlockState(pos).getBlock();
        // 道床(BallastBlock/砂利)は上書きしない。道床を1ブロック下げた結果コリジョンと同じ
        // Y に来るため、道床がある位置はコリジョンを置かず道床自体を床として使う。
        boolean replaceable = existing == net.minecraft.world.level.block.Blocks.AIR
            || existing == net.minecraft.world.level.block.Blocks.CAVE_AIR
            || existing == net.minecraft.world.level.block.Blocks.VOID_AIR
            || existing instanceof MarkerBlock
            || existing instanceof RailCollisionBlock;
        if (!replaceable) {
            return;
        }

        level.setBlock(pos, RealTrainModRenewedBlocks.RAIL_COLLISION.get().defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity segBe = level.getBlockEntity(pos);
        if (segBe instanceof RailCollisionBlockEntity rbe) {
            rbe.setCorePos(corePos);
            level.sendBlockUpdated(pos, rbe.getBlockState(), rbe.getBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void removeMarkerAt(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).getBlock() instanceof MarkerBlock) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * 道床ブロックを解決する。道床はカーペット厚(1px)の薄板にしたいので、フルブロックである
     * バニラ gravel 等ではなく、砂利テクスチャの薄板 BallastBlock を常に使う。
     * (レール定義の ballastBlockId は道床を敷くか否か = ballastWidth>0 の判定にのみ利用)
     */
    private static Block resolveBallastBlock(@Nullable RailDefinition def) {
        return RealTrainModRenewedBlocks.BALLAST.get();
    }

    @Nullable
    private static RailDefinition resolveRailDef(@Nullable String selectedModelId) {
        return (selectedModelId != null && !selectedModelId.isBlank())
            ? RailRegistry.getById(selectedModelId)
            : RailRegistry.getSelected();
    }

    private static RailPosition copyRailPosition(RailPosition source) {
        return RailPosition.readFromNBT(source.writeToNBT());
    }

    private static RailPosition translateRailPosition(RailPosition source, int dx, int dy, int dz) {
        RailPosition translated = copyRailPosition(source);
        translated.blockX += dx;
        translated.blockY += dy;
        translated.blockZ += dz;
        translated.init();
        return translated;
    }

    private static RailPosition rotateRailPosition(RailPosition source, BlockPos pivot, int rotationSteps) {
        RailPosition rotated = copyRailPosition(source);
        if ((rotationSteps & 7) == 0) {
            return rotated;
        }

        double angle = Math.toRadians(rotationSteps * 45.0D);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int relX = source.blockX - pivot.getX();
        int relZ = source.blockZ - pivot.getZ();
        rotated.blockX = pivot.getX() + (int) Math.round((relX * cos) - (relZ * sin));
        rotated.blockZ = pivot.getZ() + (int) Math.round((relX * sin) + (relZ * cos));
        rotated.direction = (byte) (((source.direction & 7) + rotationSteps) & 7);
        rotated.anchorYaw += rotationSteps * 45.0F;
        rotated.init();
        return rotated;
    }

    public static int getDirStepX(int dir) {
        return switch (dir & 7) {
            case 1, 2, 3 -> -1;
            case 5, 6, 7 -> 1;
            default -> 0;
        };
    }

    public static int getDirStepZ(int dir) {
        return switch (dir & 7) {
            case 0, 1, 7 -> -1;
            case 3, 4, 5 -> 1;
            default -> 0;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RealTrainModRenewedBlockEntities.MARKER.get(), MarkerBlockEntity::tick);
    }
}

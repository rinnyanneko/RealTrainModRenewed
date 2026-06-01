package com.portofino.realtrainmodunofficial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.MarkerBlockEntity;
import com.portofino.realtrainmodunofficial.item.RailItem;
import com.portofino.realtrainmodunofficial.item.WrenchItem;
import com.portofino.realtrainmodunofficial.rail.util.RailMap;
import com.portofino.realtrainmodunofficial.rail.util.RailMapBasic;
import com.portofino.realtrainmodunofficial.rail.util.RailPosition;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class RailPreviewRenderer {
    private static final int SEARCH_DISTANCE = 50;
    private static final int SEARCH_HEIGHT = 10;
    private static final int LABEL_SCAN_INTERVAL_TICKS = 30;
    private static List<RailPosition> cachedMarkerLabelPositions = List.of();
    private static long nextMarkerLabelScanTick;
    private static BlockPos cachedMarkerLabelCenter = BlockPos.ZERO;

    private RailPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        WrenchItem.clearInvalidPreviewTags(mc.player, mc.level);

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        try {
            renderMarkerDistanceLabels(mc, poseStack, bufferSource, camera);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

            // レンチのアンカー編集 (緑線プレビュー)。視点追従でアンカーを更新しつつ曲線を描く。
            renderAnchorEditing(mc, poseStack, consumer);

            ItemStack stack = findPreviewStack(mc);
            if (!stack.isEmpty()) {
                CompoundTag startTag = stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get());
                if (startTag != null && startTag.contains("X") && startTag.contains("Y") && startTag.contains("Z")) {
                    BlockPos startPos = new BlockPos(startTag.getInt("X"), startTag.getInt("Y"), startTag.getInt("Z"));
                    BlockEntity startBe = mc.level.getBlockEntity(startPos);
                    RailPosition start = resolveStartPosition(startBe, startTag);
                    if (start != null) {
                        if (startTag.getBoolean("WrenchMode")) {
                            ListTag segments = WrenchItem.getSegmentList(startTag);
                            if (!segments.isEmpty()) {
                                renderStartMarkerHint(poseStack, consumer, startPos);
                                for (int i = 0; i < segments.size(); i++) {
                                    CompoundTag segment = segments.getCompound(i);
                                    RailPosition rawEnd = RailPosition.readFromNBT(segment.getCompound("EndRP"));
                                    if (rawEnd == null) {
                                        continue;
                                    }
                                    RailPosition controlledStart = WrenchItem.applyControlHandle(start, segment, true);
                                    RailPosition controlledEnd = WrenchItem.applyControlHandle(rawEnd, segment, false);
                                    renderPreviewRail(poseStack, consumer, controlledStart, controlledEnd, false);
                                    renderControlLine(poseStack, consumer, start, WrenchItem.getStartHandle(segment, start, rawEnd));
                                    renderControlLine(poseStack, consumer, rawEnd, WrenchItem.getEndHandle(segment, start, rawEnd));
                                }
                            }
                        } else {
                            renderStartMarkerHint(poseStack, consumer, startPos);
                            if (mc.hitResult instanceof BlockHitResult blockHit
                                && blockHit.getType() == HitResult.Type.BLOCK
                                && !blockHit.getBlockPos().equals(startPos)) {
                                BlockEntity targetBe = mc.level.getBlockEntity(blockHit.getBlockPos());
                                if (targetBe instanceof MarkerBlockEntity marker) {
                                    RailPosition end = marker.getMarkerRP();
                                    if (end != null) {
                                        end = applyPreviewOffset(end, startTag);
                                        renderPreviewRail(poseStack, consumer, start, end, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            poseStack.popPose();
            bufferSource.endBatch();
        }
    }

    private static void renderMarkerDistanceLabels(Minecraft mc, PoseStack poseStack,
                                                   net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                                   Vec3 camera) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos center = mc.player.blockPosition();
        long gameTime = mc.level.getGameTime();
        if (gameTime >= nextMarkerLabelScanTick || fartherThanScanWindow(center, cachedMarkerLabelCenter)) {
            cachedMarkerLabelPositions = collectNearbyMarkerPositions(mc, center);
            cachedMarkerLabelCenter = center;
            nextMarkerLabelScanTick = gameTime + LABEL_SCAN_INTERVAL_TICKS;
        }
        for (RailPosition rp : cachedMarkerLabelPositions) {
            renderMarkerDistanceLabelsForMarker(mc, poseStack, bufferSource, camera, rp);
        }
    }

    private static boolean fartherThanScanWindow(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) > 8
            || Math.abs(a.getY() - b.getY()) > 4
            || Math.abs(a.getZ() - b.getZ()) > 8;
    }

    private static List<RailPosition> collectNearbyMarkerPositions(Minecraft mc, BlockPos center) {
        List<RailPosition> result = new ArrayList<>();
        if (mc.level == null) {
            return result;
        }
        for (int dx = -SEARCH_DISTANCE; dx <= SEARCH_DISTANCE; dx++) {
            for (int dy = -SEARCH_HEIGHT; dy <= SEARCH_HEIGHT; dy++) {
                for (int dz = -SEARCH_DISTANCE; dz <= SEARCH_DISTANCE; dz++) {
                    BlockEntity be = mc.level.getBlockEntity(center.offset(dx, dy, dz));
                    if (!(be instanceof MarkerBlockEntity marker)) {
                        continue;
                    }
                    RailPosition rp = marker.getMarkerRP();
                    if (rp != null) {
                        result.add(rp);
                    }
                }
            }
        }
        return result;
    }

    private static void renderMarkerDistanceLabelsForMarker(Minecraft mc, PoseStack poseStack,
                                                            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                                            Vec3 camera, RailPosition rp) {
        // マーカーの矢印が指す向きにメートルラベルを伸ばす。
        // anchorYaw を sin/cos に変換する方式は、マーカーの正準方向定義 (getDirStepX/Z) と
        // 一致せず、向きによって逆になっていた (ユーザーが両符号とも「逆」と報告)。
        // anchorYaw から dir(0-7) を復元し、矢印と同じ getDirStep 定義で方向ベクトルを作る。
        int dir = ((int) Math.round(rp.anchorYaw / 45.0F)) & 7;
        // 矢印は getDirStep(dir) と逆向き(dir+4側)を指していたため符号を反転する。
        double dirX = -com.portofino.realtrainmodunofficial.block.MarkerBlock.getDirStepX(dir);
        double dirZ = -com.portofino.realtrainmodunofficial.block.MarkerBlock.getDirStepZ(dir);
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 1.0e-6) { dirX /= len; dirZ /= len; }
        for (int meters = 10; meters <= 50; meters += 10) {
            double x = rp.posX + dirX * meters;
            double y = rp.posY + 1.35D;
            double z = rp.posZ + dirZ * meters;
            if (camera.distanceToSqr(x, y, z) > 90.0D * 90.0D) {
                continue;
            }
            renderWorldLabel(mc, poseStack, bufferSource, x, y, z, meters + "m", 0xFFE96B);
        }
    }

    private static void renderWorldLabel(Minecraft mc, PoseStack poseStack,
                                         net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                         double x, double y, double z, String text, int color) {
        Font font = mc.font;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = 0.035F;
        poseStack.scale(-scale, -scale, scale);
        float offset = -font.width(text) / 2.0F;
        font.drawInBatch(text, offset, 0.0F, color, false, poseStack.last().pose(),
            bufferSource, Font.DisplayMode.SEE_THROUGH, 0x66000000, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static ItemStack findPreviewStack(Minecraft mc) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.getItem() instanceof RailItem || stack.getItem() instanceof WrenchItem) {
                if (stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get()) != null) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void renderStartMarkerHint(PoseStack poseStack, VertexConsumer consumer, BlockPos startPos) {
        double x = startPos.getX() + 0.5D;
        double y = startPos.getY() + 0.12D;
        double z = startPos.getZ() + 0.5D;
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            x - 0.45D, y - 0.04D, z - 0.45D,
            x + 0.45D, y + 0.04D, z + 0.45D,
            0.1F, 0.85F, 1.0F, 0.8F
        );
    }

    /**
     * レンチのアンカー編集中: 視点の先(クロスヘア)に向けて編集中マーカーのアンカーを更新し、
     * ペアマーカーとの間に緑のベジェ曲線と、両マーカーからの緑の制御線を描く。
     * 編集中マーカーが壊れた/別ワールド等で消えていればセッションを解除する (永続性: 設置/破壊/リログ)。
     */
    private static void renderAnchorEditing(Minecraft mc, PoseStack poseStack, VertexConsumer consumer) {
        BlockPos editPos = WrenchItem.editingMarker;
        if (editPos == null || mc.level == null || mc.player == null) {
            return;
        }
        // 編集中マーカーが消えた(破壊/リログ)らセッション解除。持ち物に依らず線は保持する。
        if (!(mc.level.getBlockEntity(editPos) instanceof MarkerBlockEntity editMarker)) {
            WrenchItem.editingMarker = null;
            WrenchItem.editingPair = null;
            return;
        }
        RailPosition start = editMarker.getMarkerRP();
        if (start == null) return;

        // 視点追従の更新はレンチ所持中のみ。別アイテムに持ち替えても緑線は保持され(形は固定)、
        // レールアイテムに持ち替えて設置できる (ユーザー要望)。
        boolean holdingWrench = mc.player.getMainHandItem().getItem() instanceof WrenchItem
            || mc.player.getOffhandItem().getItem() instanceof WrenchItem;
        if (holdingWrench && WrenchItem.followMode) {
            Vec3 hit;
            if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
                hit = mc.hitResult.getLocation();
            } else {
                Vec3 eye = mc.player.getEyePosition(1.0F);
                hit = eye.add(mc.player.getViewVector(1.0F).scale(8.0D));
            }
            double dx = hit.x - start.posX;
            double dy = hit.y - start.posY;
            double dz = hit.z - start.posZ;
            double lenH = Math.sqrt(dx * dx + dz * dz);
            if (lenH < 0.5D) lenH = 0.5D;
            WrenchItem.liveYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            WrenchItem.liveLenH = (float) lenH;
            WrenchItem.livePitch = (float) Math.toDegrees(Math.atan2(dy, lenH));
            WrenchItem.liveLenV = (float) lenH;
        }
        // 保持しているライブ値を start に反映。
        if (WrenchItem.liveLenH > 0.0F) {
            start.anchorYaw = WrenchItem.liveYaw;
            start.anchorPitch = WrenchItem.livePitch;
            start.anchorLengthHorizontal = WrenchItem.liveLenH;
            start.anchorLengthVertical = WrenchItem.liveLenV;
        }
        start.cantCenter = WrenchItem.liveCantCenter;
        start.cantEdge = WrenchItem.liveCantEdge;
        start.init();

        renderStartMarkerHint(poseStack, consumer, editPos);
        // 編集中マーカーのハンドル(緑線)。
        renderControlLine(poseStack, consumer, start, anchorHandle(start));

        // ペアマーカーを毎フレーム動的に探索する (範囲 SEARCH_DISTANCE=50)。
        // 範囲内の全マーカーから緑線を出し、編集中マーカーとの間に緑のベジェ曲線(=レール形状)を描く。
        for (RailPosition end : collectNearbyMarkerPositions(mc, editPos)) {
            if (end.blockX == editPos.getX() && end.blockY == editPos.getY() && end.blockZ == editPos.getZ()) {
                continue; // 編集中マーカー自身は除外
            }
            renderStartMarkerHint(poseStack, consumer, new BlockPos(end.blockX, end.blockY, end.blockZ));
            renderControlLine(poseStack, consumer, end, anchorHandle(end));
            // 緑のベジェ曲線 (レール形状プレビュー)。アンカーに応じて曲がる。
            renderPreviewRail(poseStack, consumer, start, end, true);
        }
    }

    /** RailPosition のアンカー(方位・長さ)からワールド空間のハンドル点を返す (緑線の先端)。 */
    private static Vec3 anchorHandle(RailPosition rp) {
        float len = rp.anchorLengthHorizontal > 0.0F ? rp.anchorLengthHorizontal : 2.0F;
        double hx = rp.posX + Math.sin(Math.toRadians(rp.anchorYaw)) * len;
        double hz = rp.posZ + Math.cos(Math.toRadians(rp.anchorYaw)) * len;
        double hy = rp.posY + Math.sin(Math.toRadians(rp.anchorPitch)) * len;
        return new Vec3(hx, hy, hz);
    }

    private static void renderPreviewRail(PoseStack poseStack, VertexConsumer consumer, RailPosition rawStart, RailPosition rawEnd, boolean editLine) {
        RailPosition start = RailPosition.readFromNBT(rawStart.writeToNBT());
        RailPosition end = RailPosition.readFromNBT(rawEnd.writeToNBT());
        RailMap map = new RailMapBasic(start, end);
        int split = Math.max(8, RailMap.curveSplitForLength(map.getHorizontalPathLength()));
        int samples = Math.min(96, Math.max(16, (int) Math.ceil(map.getLength() * 2.0D)));
        for (int i = 0; i <= samples; i++) {
            int index = (int) Math.round(split * (i / (double) samples));
            double[] p = map.getRailPos(split, index);
            double x = p[1];
            double y = map.getRailHeight(split, index) + 0.08D;
            double z = p[0];
            LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                x - 0.11D, y - 0.03D, z - 0.11D,
                x + 0.11D, y + 0.03D, z + 0.11D,
                editLine ? 0.2F : 0.1F,
                editLine ? 1.0F : 0.85F,
                editLine ? 0.2F : 1.0F,
                0.45F
            );
        }
    }

    private static void renderControlLine(PoseStack poseStack, VertexConsumer consumer, RailPosition source, Vec3 handle) {
        int samples = 24;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double x = source.posX + (handle.x - source.posX) * t;
            double y = source.posY + (handle.y - source.posY) * t + 0.08D;
            double z = source.posZ + (handle.z - source.posZ) * t;
            LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                x - 0.045D, y - 0.045D, z - 0.045D,
                x + 0.045D, y + 0.045D, z + 0.045D,
                0.15F, 1.0F, 0.15F, 0.75F
            );
        }
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            handle.x - 0.22D, handle.y - 0.02D, handle.z - 0.22D,
            handle.x + 0.22D, handle.y + 0.08D, handle.z + 0.22D,
            1.0F, 0.1F, 0.1F, 0.85F
        );
    }

    private static RailPosition applyPreviewOffset(RailPosition raw, CompoundTag tag) {
        RailPosition copy = RailPosition.readFromNBT(raw.writeToNBT());
        if (copy == null || tag == null) {
            return raw;
        }
        copy.posX += tag.getInt("OffsetX") / 16.0D;
        copy.posY += tag.getInt("OffsetY") / 16.0D;
        copy.posZ += tag.getInt("OffsetZ") / 16.0D;
        return copy;
    }

    private static RailPosition resolveStartPosition(BlockEntity startBe, CompoundTag tag) {
        if (startBe instanceof MarkerBlockEntity marker) {
            return marker.getMarkerRP();
        }
        if (startBe instanceof LargeRailCoreBlockEntity core) {
            RailPosition first = core.getFirstRailPosition();
            if (first != null) {
                return first;
            }
        }
        if (tag.contains("StartRP")) {
            return RailPosition.readFromNBT(tag.getCompound("StartRP"));
        }
        return null;
    }

}

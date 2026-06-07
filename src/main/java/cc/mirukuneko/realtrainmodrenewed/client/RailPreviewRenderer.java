package cc.mirukuneko.realtrainmodrenewed.client;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents;
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat;
import cc.mirukuneko.realtrainmodrenewed.item.RailItem;
import cc.mirukuneko.realtrainmodrenewed.item.WrenchItem;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMapBasic;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = Dist.CLIENT)
public final class RailPreviewRenderer {
    private static final int PREVIEW_SAMPLES = 96;

    private RailPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderTypes.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        try {
            renderStackPreview(mc, poseStack, consumer);
            renderWrenchEditPreview(mc, poseStack, consumer);
        } finally {
            poseStack.popPose();
            buffer.endBatch();
        }
    }

    private static void renderStackPreview(Minecraft mc, PoseStack poseStack, VertexConsumer consumer) {
        ItemStack stack = findPreviewStack(mc);
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get());
        if (tag == null) {
            return;
        }
        RailPosition start = resolveStartPosition(mc.level, tag);
        if (start == null) {
            return;
        }
        start = applyPreviewOffset(start, tag);
        ListTag segments = WrenchItem.getSegmentList(tag);
        for (int i = 0; i < segments.size(); i++) {
            CompoundTag segment = NbtCompat.getCompound(segments, i);
            RailPosition end = RailPosition.readFromNBT(NbtCompat.getCompound(segment, "EndRP"));
            if (end == null) {
                continue;
            }
            end = applyPreviewOffset(end, tag);
            RailPosition renderedStart = WrenchItem.applyControlHandle(start, segment, true);
            RailPosition renderedEnd = WrenchItem.applyControlHandle(end, segment, false);
            renderRailCurve(poseStack, consumer, renderedStart, renderedEnd, 0.15F, 0.9F, 1.0F, 0.75F);
            renderHandle(poseStack, consumer, renderedStart, WrenchItem.getStartHandle(segment, start, end));
            renderHandle(poseStack, consumer, renderedEnd, WrenchItem.getEndHandle(segment, start, end));
        }
    }

    private static void renderWrenchEditPreview(Minecraft mc, PoseStack poseStack, VertexConsumer consumer) {
        BlockPos editPos = WrenchItem.editingMarker;
        if (editPos == null || mc.level == null) {
            return;
        }
        if (!(mc.level.getBlockEntity(editPos) instanceof MarkerBlockEntity marker)) {
            WrenchItem.editingMarker = null;
            WrenchItem.editingPair = null;
            WrenchItem.followMode = false;
            return;
        }
        RailPosition start = marker.getMarkerRP();
        if (start == null) {
            return;
        }
        if (WrenchItem.followMode) {
            updateLiveHandleFromCrosshair(mc, start);
        }
        RailPosition liveStart = RailPosition.readFromNBT(start.writeToNBT());
        if (liveStart == null) {
            return;
        }
        if (WrenchItem.liveLenH > 0.0F) {
            liveStart.anchorYaw = WrenchItem.liveYaw;
            liveStart.anchorPitch = WrenchItem.livePitch;
            liveStart.anchorLengthHorizontal = WrenchItem.liveLenH;
            liveStart.anchorLengthVertical = WrenchItem.liveLenV;
        }
        liveStart.cantCenter = WrenchItem.liveCantCenter;
        liveStart.cantEdge = WrenchItem.liveCantEdge;
        liveStart.init();

        renderMarkerBox(poseStack, consumer, editPos, 0.2F, 1.0F, 0.2F, 0.9F);
        renderHandle(poseStack, consumer, liveStart, anchorHandle(liveStart));
        BlockPos pairPos = WrenchItem.editingPair;
        if (pairPos != null && mc.level.getBlockEntity(pairPos) instanceof MarkerBlockEntity pairMarker) {
            RailPosition end = pairMarker.getMarkerRP();
            if (end != null) {
                renderMarkerBox(poseStack, consumer, pairPos, 0.2F, 1.0F, 0.2F, 0.65F);
                renderRailCurve(poseStack, consumer, liveStart, end, 0.2F, 1.0F, 0.2F, 0.75F);
                renderHandle(poseStack, consumer, end, anchorHandle(end));
            }
        }
    }

    private static void updateLiveHandleFromCrosshair(Minecraft mc, RailPosition start) {
        Vec3 hit;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            hit = mc.hitResult.getLocation();
        } else if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition(1.0F);
            hit = eye.add(mc.player.getViewVector(1.0F).scale(8.0D));
        } else {
            return;
        }
        double dx = hit.x - start.posX;
        double dz = hit.z - start.posZ;
        double horizontal = Math.max(0.5D, Math.sqrt(dx * dx + dz * dz));
        WrenchItem.liveYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        WrenchItem.livePitch = 0.0F;
        WrenchItem.liveLenH = (float) horizontal;
        WrenchItem.liveLenV = (float) horizontal;
    }

    private static ItemStack findPreviewStack(Minecraft mc) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if ((stack.getItem() instanceof RailItem || stack.getItem() instanceof WrenchItem)
                && stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get()) != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static RailPosition resolveStartPosition(Level level, CompoundTag tag) {
        if (level == null || !tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) {
            return RailPosition.readFromNBT(NbtCompat.getCompound(tag, "StartRP"));
        }
        BlockPos startPos = new BlockPos(NbtCompat.getInt(tag, "X"), NbtCompat.getInt(tag, "Y"), NbtCompat.getInt(tag, "Z"));
        BlockEntity startBe = level.getBlockEntity(startPos);
        if (startBe instanceof MarkerBlockEntity marker) {
            return marker.getMarkerRP();
        }
        if (startBe instanceof LargeRailCoreBlockEntity core) {
            RailPosition first = core.getFirstRailPosition();
            if (first != null) {
                return first;
            }
        }
        return RailPosition.readFromNBT(NbtCompat.getCompound(tag, "StartRP"));
    }

    private static RailPosition applyPreviewOffset(RailPosition raw, CompoundTag tag) {
        RailPosition copy = RailPosition.readFromNBT(raw.writeToNBT());
        if (copy == null || tag == null) {
            return raw;
        }
        copy.posX += NbtCompat.getInt(tag, "OffsetX") / 16.0D;
        copy.posY += NbtCompat.getInt(tag, "OffsetY") / 16.0D;
        copy.posZ += NbtCompat.getInt(tag, "OffsetZ") / 16.0D;
        return copy;
    }

    private static Vec3 anchorHandle(RailPosition rp) {
        float length = rp.anchorLengthHorizontal > 0.0F ? rp.anchorLengthHorizontal : 2.0F;
        double yaw = Math.toRadians(rp.anchorYaw);
        double pitch = Math.toRadians(rp.anchorPitch);
        return new Vec3(
            rp.posX + Math.sin(yaw) * length,
            rp.posY + Math.sin(pitch) * length,
            rp.posZ + Math.cos(yaw) * length
        );
    }

    private static void renderRailCurve(PoseStack poseStack, VertexConsumer consumer, RailPosition start, RailPosition end,
                                        float r, float g, float b, float a) {
        RailMap map = new RailMapBasic(RailPosition.readFromNBT(start.writeToNBT()), RailPosition.readFromNBT(end.writeToNBT()));
        int split = Math.max(8, RailMap.curveSplitForLength(map.getHorizontalPathLength()));
        int samples = Math.min(PREVIEW_SAMPLES, Math.max(16, (int) Math.ceil(map.getLength() * 2.0D)));
        Vec3 previous = null;
        for (int i = 0; i <= samples; i++) {
            int index = (int) Math.round(split * (i / (double) samples));
            double[] railPos = map.getRailPos(split, index);
            Vec3 current = new Vec3(railPos[1], map.getRailHeight(split, index) + 0.08D, railPos[0]);
            if (previous != null) {
                line(poseStack, consumer, previous, current, r, g, b, a);
            }
            previous = current;
        }
    }

    private static void renderHandle(PoseStack poseStack, VertexConsumer consumer, RailPosition source, Vec3 handle) {
        Vec3 start = new Vec3(source.posX, source.posY + 0.12D, source.posZ);
        line(poseStack, consumer, start, handle, 0.2F, 1.0F, 0.2F, 0.85F);
        renderSmallBox(poseStack, consumer, handle, 0.18D, 1.0F, 0.1F, 0.1F, 0.9F);
    }

    private static void renderMarkerBox(PoseStack poseStack, VertexConsumer consumer, BlockPos pos,
                                        float r, float g, float b, float a) {
        Vec3 center = Vec3.atBottomCenterOf(pos).add(0.0D, 0.12D, 0.0D);
        renderSmallBox(poseStack, consumer, center, 0.45D, r, g, b, a);
    }

    private static void renderSmallBox(PoseStack poseStack, VertexConsumer consumer, Vec3 center, double radius,
                                       float r, float g, float b, float a) {
        double minX = center.x - radius;
        double maxX = center.x + radius;
        double minY = center.y - radius * 0.25D;
        double maxY = center.y + radius * 0.25D;
        double minZ = center.z - radius;
        double maxZ = center.z + radius;
        Vec3 a0 = new Vec3(minX, minY, minZ);
        Vec3 a1 = new Vec3(maxX, minY, minZ);
        Vec3 a2 = new Vec3(maxX, minY, maxZ);
        Vec3 a3 = new Vec3(minX, minY, maxZ);
        Vec3 b0 = new Vec3(minX, maxY, minZ);
        Vec3 b1 = new Vec3(maxX, maxY, minZ);
        Vec3 b2 = new Vec3(maxX, maxY, maxZ);
        Vec3 b3 = new Vec3(minX, maxY, maxZ);
        line(poseStack, consumer, a0, a1, r, g, b, a);
        line(poseStack, consumer, a1, a2, r, g, b, a);
        line(poseStack, consumer, a2, a3, r, g, b, a);
        line(poseStack, consumer, a3, a0, r, g, b, a);
        line(poseStack, consumer, b0, b1, r, g, b, a);
        line(poseStack, consumer, b1, b2, r, g, b, a);
        line(poseStack, consumer, b2, b3, r, g, b, a);
        line(poseStack, consumer, b3, b0, r, g, b, a);
        line(poseStack, consumer, a0, b0, r, g, b, a);
        line(poseStack, consumer, a1, b1, r, g, b, a);
        line(poseStack, consumer, a2, b2, r, g, b, a);
        line(poseStack, consumer, a3, b3, r, g, b, a);
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer, Vec3 start, Vec3 end,
                             float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose.pose(), (float) start.x, (float) start.y, (float) start.z)
            .setColor(r, g, b, a)
            .setNormal(0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
            .setColor(r, g, b, a)
            .setNormal(0.0F, 1.0F, 0.0F);
    }
}

package cc.mirukuneko.realtrainmodrenewed.script;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.blockentity.BallastBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock;
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock;
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock;
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge;
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity;
import cc.mirukuneko.realtrainmodrenewed.item.RailItem;
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * SuperRailBuilder3 の 1.12.2 RTM レール API を RTMU のネイティブ敷設へ橋渡しする。
 *
 * <p>スクリプト本体は改変できないため、{@code createRailPosition}/{@code getPlayerRail}/
 * {@code buildNormalRail}/{@code buildBranchRail}/{@code deleteRail} の各関数を後注入の JS で
 * このブリッジ呼び出しへ差し替える(TrainScriptSystem)。GUI・制御フロー・render はそのまま。</p>
 */
public final class SrbRailBridge {

    /** SRB の createRailPosition 相当。data の各値から RTMU の RailPosition を生成する。 */
    public RailPosition createRailPosition(int blockX, int blockY, int blockZ, int markerDir,
                                           double switchType, double anchorLength, double anchorPitch,
                                           double anchorYaw, double cantCenter, double cantEdge, double height) {
        RailPosition rp = new RailPosition(blockX, blockY, blockZ, markerDir, (int) switchType);
        if (anchorLength >= 0.0D) {
            rp.anchorLengthHorizontal = (float) anchorLength;
            rp.anchorLengthVertical = (float) anchorLength;
        }
        rp.anchorPitch = (float) anchorPitch;
        rp.anchorYaw = (float) anchorYaw;
        rp.cantCenter = (float) cantCenter;
        rp.cantEdge = (float) cantEdge;
        rp.setHeight((byte) (int) height);
        rp.init();
        return rp;
    }

    /** SRB の buildNormalRail 相当(2点間に通常レール)。 */
    public boolean buildNormalRail(Object world, RailPosition start, RailPosition end, Object modelId) {
        Level level = toLevel(world);
        RealTrainModRenewed.LOGGER.debug(
            "[RTM-DBG] SRB buildNormalRail level={} start={} end={} model={}",
            level != null, start != null ? (start.blockX + "," + start.blockY + "," + start.blockZ) : "null",
            end != null ? (end.blockX + "," + end.blockY + "," + end.blockZ) : "null", toModelId(modelId));
        if (level == null || start == null || end == null) {
            return false;
        }
        List<RailPosition> rps = new ArrayList<>(2);
        rps.add(start);
        rps.add(end);
        boolean ok = MarkerBlock.buildRailForScript(level, rps, toModelId(modelId));
        RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB buildNormalRail result={}", ok);
        return ok;
    }

    /** SRB の buildBranchRail 相当(3点以上で分岐レール)。 */
    public boolean buildBranchRail(Object world, List<?> rpsRaw, Object modelId) {
        Level level = toLevel(world);
        if (level == null || rpsRaw == null || rpsRaw.size() < 2) {
            return false;
        }
        List<RailPosition> rps = new ArrayList<>(rpsRaw.size());
        for (Object o : rpsRaw) {
            if (o instanceof RailPosition rp) {
                rps.add(rp);
            }
        }
        return MarkerBlock.buildRailForScript(level, rps, toModelId(modelId));
    }

    /** SRB の deleteRail 相当。(x,y,z) にレール(コア or 当たり判定)があれば撤去し true。 */
    public boolean deleteRail(Object world, int x, int y, int z) {
        Level level = toLevel(world);
        if (level == null) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof LargeRailCoreBlock) {
            level.removeBlock(pos, false); // onRemove が当たり判定ブロックも掃除する
            return true;
        }
        if (block instanceof RailCollisionBlock) {
            BlockPos corePos = null;
            if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity be) {
                corePos = be.getCorePos();
            }
            if (corePos != null && level.getBlockState(corePos).getBlock() instanceof LargeRailCoreBlock) {
                level.removeBlock(corePos, false);
                return true;
            }
            level.removeBlock(pos, false);
            return true;
        }
        return false;
    }

    /** SRB の getPlayerRail 相当。プレイヤーが持つレールアイテムの選択モデルIDを返す(無ければ "")。 */
    public String heldRailModelId(Object playerObj) {
        if (!(playerObj instanceof Player player)) {
            return "";
        }
        ItemStack main = player.getMainHandItem();
        if (main != null && main.getItem() instanceof RailItem) {
            String id = LegacyItemStackBridge.getSelectedModelId(main);
            return id == null ? "" : id;
        }
        ItemStack off = player.getOffhandItem();
        if (off != null && off.getItem() instanceof RailItem) {
            String id = LegacyItemStackBridge.getSelectedModelId(off);
            return id == null ? "" : id;
        }
        return "";
    }

    /** NGTLog.sendChatMessage(player, msg) 相当。プレイヤーにシステムメッセージを送る。 */
    public void chat(Object playerObj, String msg) {
        if (playerObj instanceof Player p && msg != null) {
            try {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * (x,y,z) のレール関連ブロックをレールコア(LargeRailCoreBlockEntity)に解決して返す。
     *
     * <p>RTMU はレールコアを始点1ブロックにしか置かず、レール沿いは当たり判定(RailCollisionBlock)
     * /道床(BallastBlock)が並ぶ。SRB の接続検出 getAroundTileEntity は {@code instanceof
     * TileEntityLargeRailBase} でコアしか拾えないため、コアから離れた位置(レール終端側など)では
     * 接続が検出されず、接続マーカーが接線ロックされない(本家では端のどちらでも接続できる)。
     * そこで当たり判定/道床ブロックは getCorePos からコアを辿って返し、レール全長で接続検出を効かせる。
     * レール以外の BlockEntity(看板等)はそのまま返す。</p>
     */
    public Object railCoreAt(Object world, int x, int y, int z) {
        Level level = toLevel(world);
        if (level == null) {
            return null;
        }
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LargeRailCoreBlockEntity) {
            return be;
        }
        BlockPos corePos = null;
        if (be instanceof RailCollisionBlockEntity rbe) {
            corePos = rbe.getCorePos();
        } else if (be instanceof BallastBlockEntity bbe) {
            corePos = bbe.getCorePos();
        }
        if (corePos != null
            && level.getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core) {
            debugCore("collision->core", core);
            return core;
        }
        if (be instanceof LargeRailCoreBlockEntity coreDirect) {
            debugCore("direct-core", coreDirect);
        }
        return be;
    }

    private static long lastCoreLog = 0L;

    /** [RTM-DBG] レール接続検証用: 解決したコアの RailPositions(anchorYaw/posX/Z) をスロットル出力。 */
    private static void debugCore(String tag, LargeRailCoreBlockEntity core) {
        long now = System.currentTimeMillis();
        if (now - lastCoreLog < 1000L) {
            return;
        }
        lastCoreLog = now;
        try {
            RailPosition[] rps = core.getRailPositions();
            if (rps == null) {
                RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB railCoreAt {} rps=null", tag);
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rps.length; i++) {
                RailPosition rp = rps[i];
                if (rp == null) { sb.append("[").append(i).append("]=null "); continue; }
                sb.append(String.format("[%d]pos(%.2f,%.2f,%.2f) yaw=%.1f pitch=%.1f dir=%d ",
                    i, rp.posX, rp.posY, rp.posZ, rp.anchorYaw, rp.anchorPitch, rp.direction));
            }
            RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB railCoreAt {} {}", tag, sb);
        } catch (Throwable t) {
            RealTrainModRenewed.LOGGER.debug("[RTM-DBG] SRB railCoreAt {} err {}", tag, t.toString());
        }
    }

    /** BlockEntity の座標を返す(SRB の getTileEntityPos が MCP 名 func_174877_v を使うため代替)。 */
    public int[] tilePos(Object tile) {
        if (tile instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            BlockPos p = be.getBlockPos();
            return new int[]{p.getX(), p.getY(), p.getZ()};
        }
        return new int[]{0, 0, 0};
    }

    private static Level toLevel(Object world) {
        if (world instanceof Level level) {
            return level;
        }
        if (world instanceof CarEntity.CarWorldCompat compat) {
            return compat.getLevel();
        }
        return null;
    }

    private static String toModelId(Object modelId) {
        if (modelId == null) {
            return null;
        }
        String s = modelId.toString();
        return s.isBlank() ? null : s;
    }
}

package cc.mirukuneko.realtrainmodrenewed.rail.util;

import cc.mirukuneko.realtrainmodrenewed.block.BallastBlock;
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock;
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlocks;
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.rail.math.BezierCurve;
import cc.mirukuneko.realtrainmodrenewed.rail.math.CurveMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;

/**
 * legacy RailMap 移植。道床用の座標列生成（レガシー）と、レール配置可否の判定を担当。
 * レールビジュアル(MQOモデル)は LargeRailCoreBlockEntity が別途担当する。
 * 道床ブロックのワールド配置は行わない（軽量化のため中心線のみ検査・撤去）。
 */
public abstract class RailMap {
    public static boolean suppressRailRemoval = false;
    protected final List<int[]> rails = new ArrayList<>();

    public abstract RailPosition getStartRP();
    public abstract RailPosition getEndRP();
    public abstract double getLength();
    public abstract int getNearlestPoint(int split, double x, double z);
    public abstract double[] getRailPos(int split, int index);
    public abstract double getRailHeight(int split, int index);
    public abstract float getRailYaw(int split, int index);
    public abstract float getRailPitch(int split, int index);
    public abstract float getRailRoll(int split, int index);

    public float getCant(int split, int index) {
        return this.getRailRoll(split, index);
    }

    /** legacy の {@code getRailRotation} と同じ（ヨー角）。 */
    public float getRailRotation(int split, int index) {
        return this.getRailYaw(split, index);
    }

    /** 水平が直線のレール区間か（見た目のゲージ調整用）。 */
    public boolean isStraightTrack() {
        return false;
    }

    /**
     * 水平ベジェの弧長に基づく分割数。{@link BezierCurve#splitForLength(double)} と同一（レンダラの {@code max} 用）。
     * {@link #getLength()} は勾配で 3D 長になるため、曲線のサンプル数には {@link #getHorizontalPathLength()} を使うこと。
     */
    public double getHorizontalPathLength() {
        return this.getLength();
    }

    /**
     * 曲線計算に渡す分割数。{@link BezierCurve} の内部 {@code split} と一致させる。
     */
    public static int curveSplitForLength(double length) {
        return BezierCurve.splitForLength(length);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RailMap rm)) {
            return false;
        }
        return this.getStartRP().equals(rm.getStartRP())
            && this.getEndRP().equals(rm.getEndRP());
    }

    @Override
    public int hashCode() {
        int result = getStartRP().hashCode();
        result = 31 * result + getEndRP().hashCode();
        return result;
    }

    /**
     * 道床ブロック位置リストを生成する。
     * ballastWidth に応じて線路幅方向にブロックを展開する。
     */
    protected void createRailList(RailProperties prop) {
        this.rails.clear();
        // 距離フィールド方式: レール曲線を密サンプルし、各候補セル中心から曲線までの距離が
        // 帯の半幅以内なら道床を置く。垂直掃引+floor だと縁がギザつき隙間も出る (ユーザー報告
        // 「カーブで道床がかくかく/隙間」) ため、距離判定で均一幅・最小ギザつきの綺麗な帯にする。
        // ballastWidth=0 のレールでも、列車をレール全長のどこにでも設置できるよう中心線に
        // 不可視の検出ブロックを並べる(本家RTMはレール全長に当たり判定あり)。最小半幅 0.6 で
        // 中心線が通るブロックだけを薄く埋める(地形は壊さない=setRail 側で置換可否を判定)。
        double halfW = Math.max(prop.ballastWidth / 2.0D, 0.6D);
        int split = (int) (this.getLength() * 4.0D);
        if (split < 2) split = 2;
        int n = split + 1;

        double[] sx = new double[n];
        double[] sz = new double[n];
        int[] sy = new int[n];
        // 各サンプルでのブロック内レール面高さ(0..1)。坂で薄い当たり判定をレール面に合わせるため。
        double[] soff = new double[n];
        double minx = Double.MAX_VALUE, maxx = -Double.MAX_VALUE;
        double minz = Double.MAX_VALUE, maxz = -Double.MAX_VALUE;
        for (int j = 0; j < n; ++j) {
            double[] point = this.getRailPos(split, j);
            sx[j] = point[1];
            sz[j] = point[0];
            // 当たり判定はレール高さ(=レール面のブロック)に置く。1 ブロック下(地面)に置くと
            // 地面ブロックを置換して抉れて見えるため。レール高さは通常空中なので置換で問題が出ない。
            double h = this.getRailHeight(split, j);
            sy[j] = (int) Math.floor(h + 1.0e-4);
            soff[j] = h - sy[j]; // ブロック内のレール面の高さ(端数)
            if (sx[j] < minx) minx = sx[j];
            if (sx[j] > maxx) maxx = sx[j];
            if (sz[j] < minz) minz = sz[j];
            if (sz[j] > maxz) maxz = sz[j];
        }

        int bx0 = CurveMath.floor(minx - halfW - 1.0D);
        int bx1 = CurveMath.floor(maxx + halfW + 1.0D);
        int bz0 = CurveMath.floor(minz - halfW - 1.0D);
        int bz1 = CurveMath.floor(maxz + halfW + 1.0D);
        double thrSq = halfW * halfW;

        for (int X = bx0; X <= bx1; ++X) {
            for (int Z = bz0; Z <= bz1; ++Z) {
                double cx = X + 0.5D;
                double cz = Z + 0.5D;
                double best = Double.MAX_VALUE;
                int bestJ = 0;
                for (int j = 0; j < n; ++j) {
                    double dx = cx - sx[j];
                    double dz = cz - sz[j];
                    double d = dx * dx + dz * dz;
                    if (d < best) {
                        best = d;
                        bestJ = j;
                    }
                }
                if (best > thrSq) continue;
                // 端点では、レール方向の外側へはみ出すセルを除外する。
                // (距離フィールドだと端から半幅分はみ出し、当たり判定がレールより1ブロック長く見える)
                if (bestJ == 0 || bestJ == n - 1) {
                    int inner = bestJ == 0 ? Math.min(1, n - 1) : Math.max(0, n - 2);
                    // 内側サンプルへ向かう接線。外向き = その逆。
                    double tinx = sx[inner] - sx[bestJ];
                    double tinz = sz[inner] - sz[bestJ];
                    double vx = cx - sx[bestJ];
                    double vz = cz - sz[bestJ];
                    // セルが端点より外側(接線の逆方向)に出ていれば除外。
                    if (vx * tinx + vz * tinz < -1.0e-3) {
                        continue;
                    }
                }
                // 坂対応: このセル近傍を通る全サンプルの高さに当たり判定を置く。
                // 平坦レールは全サンプルが同じ高さなので1つだけ。急勾配では1つのXZ列に
                // レールが複数の高さで通るため、各高さに置いて縦方向の隙間(=端しか壊せない)を無くす。
                for (int j = 0; j < n; ++j) {
                    double dx = cx - sx[j];
                    double dz = cz - sz[j];
                    if (dx * dx + dz * dz > thrSq) continue;
                    // レール面端数を 1/16 単位(0..15)で保持し、当たり判定スラブの高さに使う。
                    int off16 = (int) Math.round(soff[j] * 16.0D);
                    if (off16 < 0) off16 = 0;
                    if (off16 > 15) off16 = 15;
                    this.addRailBlock(X, sy[j], Z, off16);
                }
            }
        }
    }

    protected void addRailBlock(int x, int y, int z) {
        addRailBlock(x, y, z, 0);
    }

    protected void addRailBlock(int x, int y, int z, int surfaceOffset16) {
        // (x,y,z) が完全一致する場合は、当たり判定スラブ高さ(surfaceOffset16)を最大値で更新する。
        // 坂では同じブロック内をレールが下端→上端と通るので、最も高いレール面まで床から詰める
        // (本家RTM: box(0,0,0,1,レール面高さ,1))。異なる Y(=別ブロック)はそれぞれ追加する。
        for (int i = 0; i < this.rails.size(); ++i) {
            int[] ia = this.rails.get(i);
            if (ia[0] == x && ia[1] == y && ia[2] == z) {
                if (ia.length >= 4 && surfaceOffset16 > ia[3]) {
                    ia[3] = surfaceOffset16;
                }
                return;
            }
        }
        this.rails.add(new int[]{x, y, z, surfaceOffset16});
    }

    /**
     * 道床(見える砂利)は置かない (ユーザー要望「道床を消す」)。
     * レールに沿って、レール高さの空中に不可視の薄い当たり判定 (RailCollisionBlock) だけを置く。
     * これで列車設置の検出・破壊連動は機能しつつ、地面を抉らず砂利も見えない。
     * 当たり判定は AIR と既存の判定/道床/マーカーのみ置換する (草・土など地形は壊さない)。
     */
    public void setRail(Level level, Block ballastBlock, int x0, int y0, int z0, RailProperties prop) {
        // ballastWidth<=0 でも検出ブロックは置く(createRailList が中心線を最小幅で生成する)。
        // ここで return すると ballast なしレールがコア(端)でしか列車検出されなくなる。
        if (level == null || prop == null) {
            this.rails.clear();
            return;
        }
        this.createRailList(prop);
        Block collisionBlock = RealTrainModRenewedBlocks.RAIL_COLLISION.get();
        BlockPos corePos = new BlockPos(x0, y0, z0);
        for (int[] rail : this.rails) {
            BlockPos pos = new BlockPos(rail[0], rail[1], rail[2]);
            net.minecraft.world.level.block.state.BlockState existingState = level.getBlockState(pos);
            Block existing = existingState.getBlock();
            // 地形(草/土/石等)は置換しない。空中・既存の判定/道床/マーカー・置換可能ブロックのみ。
            boolean replaceable = existing == Blocks.AIR
                    || existing == Blocks.CAVE_AIR || existing == Blocks.VOID_AIR
                    || existing instanceof RailCollisionBlock
                    || existing instanceof BallastBlock
                    || existing instanceof MarkerBlock
                    || existingState.canBeReplaced();
            if (replaceable) {
                level.setBlock(pos, collisionBlock.defaultBlockState(), Block.UPDATE_ALL);
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof RailCollisionBlockEntity rbe) {
                    rbe.setCorePos(corePos);
                    // レール面の高さ(端数)を保存し、当たり判定スラブをブロック底からその高さまで出す(本家RTM準拠)。
                    float surfaceY = rail.length >= 4 ? rail[3] / 16.0f : 0.0f;
                    rbe.setSurfaceY(surfaceY);
                    level.sendBlockUpdated(pos, rbe.getBlockState(), rbe.getBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        this.rails.clear();
    }

    /**
     * レールを置けるか。道床ボリューム全体は走査せず、中心線をブロック程度の間隔でサンプルする
     * （legacyの道床グリッド検査より軽い）。
     */
    public boolean canPlaceRail(Level level, boolean isCreative, RailProperties prop) {
        double len = this.getLength();
        int samples = Math.max(3, (int) Math.ceil(len) + 1);
        int split = curveSplitForLength(this.getHorizontalPathLength());
        BlockPos startNeighbor = this.getStartRP().getNeighborBlockPos();
        BlockPos endNeighbor = this.getEndRP().getNeighborBlockPos();
        boolean allClear = true;
        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = this.getRailPos(split, j);
            int x = CurveMath.floor(point[1]);
            int z = CurveMath.floor(point[0]);
            int y = (int) this.getRailHeight(split, j);
            BlockPos pos = new BlockPos(x, y, z);
            if (pos.equals(startNeighbor) || pos.equals(endNeighbor)) {
                continue;
            }
            Block block = level.getBlockState(pos).getBlock();
            boolean passable = block == Blocks.AIR
                || block == Blocks.CAVE_AIR
                || block == Blocks.VOID_AIR
                || block instanceof MarkerBlock
                || block instanceof BallastBlock
                || block instanceof RailCollisionBlock;
            if (!passable) {
                allClear = false;
                if (!isCreative) return false;
            }
        }
        return isCreative || allClear;
    }

    public List<int[]> getRailBlockList(RailProperties prop, boolean regenerate) {
        if (this.rails.isEmpty() || regenerate) {
            this.createRailList(prop);
        }
        return new ArrayList<>(this.rails);
    }

    /** 旧道床ブロックを中心線沿いに軽量スキャンして撤去する。レールコアは別途削除する。 */
    public void removeRailBlocks(Level level) {
        double len = this.getLength();
        int split = curveSplitForLength(this.getHorizontalPathLength());
        int samples = Math.max(3, split + 1);
        for (int i = 0; i < samples; i++) {
            int j = samples <= 1 ? 0 : (int) Math.round((double) split * i / (samples - 1));
            if (j > split) j = split;
            double[] point = this.getRailPos(split, j);
            int x = CurveMath.floor(point[1]);
            int z = CurveMath.floor(point[0]);
            int y = (int) this.getRailHeight(split, j);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                        Block block = level.getBlockState(pos).getBlock();
                        if (block instanceof BallastBlock || block instanceof RailCollisionBlock) {
                            level.removeBlock(pos, false);
                        }
                    }
                }
            }
        }
    }
}

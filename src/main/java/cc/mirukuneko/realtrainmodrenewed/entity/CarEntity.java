package cc.mirukuneko.realtrainmodrenewed.entity;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader;
import cc.mirukuneko.realtrainmodrenewed.network.CarScriptDataPayload;
import cc.mirukuneko.realtrainmodrenewed.network.CarScriptDataSyncPayload;
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems;
import cc.mirukuneko.realtrainmodrenewed.item.CrowbarItem;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import javax.script.ScriptEngine;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import static cc.mirukuneko.realtrainmodrenewed.util.RealTrainModRenewedConstants.SECONDS_IN_TICK;
import static cc.mirukuneko.realtrainmodrenewed.util.RealTrainModRenewedConstants.TICK_PER_SECOND;
import static cc.mirukuneko.realtrainmodrenewed.util.UnitConverter.*;

/// 自動車Entityクラス
public final class CarEntity extends Entity {
    private static final EntityDataAccessor<String> DATA_VEHICLE_ID =
        SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.STRING);

    private ScriptEngine serverScriptEngine;
    private boolean attemptedServerScriptLoad;
    private final java.util.Map<String, String> scriptData = new java.util.HashMap<>();
    private boolean scriptDataDirty;

    // === RTM 1.7.10/1.12 互換フィールド (SRB3 等のスクリプトが直接読み書きする) ===
    /** RTM の yaw 名 (entity.field_70177_z) */
    public float field_70177_z;
    /** RTM の pitch 名 (entity.field_70125_A) */
    public float field_70125_A;
    /** RTM の tick counter 名 (entity.field_70173_aa) */
    public int field_70173_aa;
    /** RTM の world 参照 (entity.field_70170_p)。WorldCompat 経由でアクセス。 */
    public final CarWorldCompat field_70170_p = new CarWorldCompat(this);
    /** RTM の motionX/Y/Z 名 (SRB3 の doFollowing が 0 を書いて漂流を止める)。 */
    public double field_70159_w;
    public double field_70181_x;
    public double field_70179_y;

    /// 車輪のX座標オフセット
    public static final float WHEEL_X_COORD = cm2m(72.47766876220703f);
    //    private static final EntityDataAccessor<Float> DATA_SPEED =
//        SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    // 自動車の情報

    /// 乗車定員
    private static final int RIDING_CAPACITY = 5;

    // モデル情報
    /// 前輪のZ座標
    public static final float WHEEL_F_COORD = cm2m(158.62274169921875f);
    /// 後輪のZ座標
    public static final float WHEEL_R_COORD = cm2m(-164.98480224609375f);
    /// 車輪のY座標
    public static final float WHEEL_Y_COORD = cm2m(37.28034973144531f);
    /// 車輪の半径
    public static final float WHEEL_RADIUS = WHEEL_Y_COORD;
    /// ホイールベースの距離
    private static final float WHEELBASE = WHEEL_F_COORD - WHEEL_R_COORD;

    // 性能
    /// 加速度（ブロック毎ティック毎ティック）
    private static final float ACCELERATION = mpss2bpts(4.15f); // ゼロヒャク6.7秒から計算 約0.01f
    /// 減速度 正の値（ブロック毎ティック毎ティック）
    private static final float DECELERATION = ACCELERATION * 1.2f; // 加速度より少し強め
    /// 惰性の減速度 正の値（ブロック毎ティック毎ティック）
    private static final float SLOWDOWN_DECELERATION = 0.001f;
    /// 前進の最高速度 120km/h -> 33.33…m/s -> 1.666…block/tick
    private static final float MAX_SPEED = kph2bpt(120.0f);

    /// 車両が停止しているとみなす速度の閾値
    private static final float SPEED_STOP_THRESHOLD = 0.01f;
    /// ステアリングレシオ
    public static final float STEERING_RATIO = 1 / 12.0f; // ステアリング角度は、ハンドルの回転角度の12分の1

    /// 左右入力中の1tick当たりのハンドル回転角度（度毎ティック）
    private static final float STEERING_WHEEL_ANGULAR_VELOCITY_MANIPULATED = 10.0f;
    /// セルフセンタリングによる1tick当たりのハンドル回転係数（単位無し 1ブロック移動するごとに変化させる割合を決める）
    private static final float STEERING_WHEEL_SELF_CENTERING_PARAMETER = 2.0f;
    /// ハンドルの最大回転角度 左右に1.75回転ずつ（度）
    private static final float STEERING_WHEEL_MAX_ANGLE = 630.0f;
    /// ハンドルの回転角度
    public float currentSteeringWheelAngle = 0.0f; // 単位: 度
    /// 前回tickでのハンドルの回転角度
    public float prevSteeringWheelAngle = 0.0f;

    /// 車輪の回転角度 クライアントのみ
    public float wheelRotation = 0.0f;
    /// 前tickでの車輪の回転角度 クライアントのみ
    public float prevWheelRotation = 0.0f;

    /// 踏んでいる間のアクセル開度の変化量
    private static final float ACCELERATOR_STROKE_CHANGE_RATE = 1.0f / TICK_PER_SECOND / 3.0f; // 3秒でベタ踏み
    /// アクセル開度 0~1
    private float acceleratorStroke = 0.0f;
    /// 踏んでいる間のブレーキストロークの変化量
    private static final float BRAKE_STROKE_CHANGE_RATE = 1.0f / TICK_PER_SECOND; // 1秒でベタ踏み
    /// ブレーキのストローク量 0~1
    private float brakeStroke = 0.0f;
    /// ギアをリバースに入れているか
    private boolean isReversing = false;
    /// 現在ブレーキ中か
    private boolean isBraking = false;
    /// 前tickでのwSの値
    private float prevWs = 0.0f;
    /// ブレーキ中に停止してもキーを押し続けた際に、方向転換をロックする
    private boolean isReversalLocked = false;
    /// 速度 前進方向が正、後進方向が負
    public float speed = 0.0f;
    /// 現在のtickでのヨーの変化量（度）
    private float deltaYaw = 0.0f;


    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput tag) {
        setVehicleId(tag.getStringOr("VehicleId", ""));
        scriptData.clear();
        tag.read("ScriptData", CompoundTag.CODEC).ifPresent(scriptDataTag -> {
            for (String key : scriptDataTag.keySet()) {
                scriptData.put(key, scriptDataTag.getStringOr(key, ""));
            }
        });
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput tag) {
        tag.putString("VehicleId", getVehicleId());
        if (!scriptData.isEmpty()) {
            CompoundTag sd = new CompoundTag();
            scriptData.forEach(sd::putString);
            tag.store("ScriptData", CompoundTag.CODEC, sd);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DATA_VEHICLE_ID, "");
    }

    public String getVehicleId() {
        return this.entityData.get(DATA_VEHICLE_ID);
    }

    public void setVehicleId(String id) {
        this.entityData.set(DATA_VEHICLE_ID, id == null ? "" : id);
    }

    public String getScriptDataValue(String key) {
        return scriptData.getOrDefault(key, "");
    }

    public void setScriptDataValue(String key, String value) {
        if (key == null || key.isBlank()) return;
        String v = value == null ? "" : value;
        String prev = scriptData.put(key, v);
        if (!v.equals(prev)) {
            scriptDataDirty = true;
        }
    }

    /** サーバ→クライアント同期で受け取った scriptData を適用する(クライアント側)。 */
    public void applyScriptDataSync(java.util.Map<String, String> data) {
        if (data == null) return;
        scriptData.putAll(data);
    }

    public java.util.Map<String, String> scriptDataMap() {
        return scriptData;
    }

    private void ensureServerScriptLoaded() {
        if (attemptedServerScriptLoad) return;
        String id = getVehicleId();
        if (id == null || id.isBlank()) return;
        VehicleDefinition def = VehicleRegistry.getById(id);
        if (def == null || !def.hasServerScript()) {
            attemptedServerScriptLoad = true;
            return;
        }
        attemptedServerScriptLoad = true;
        try {
            serverScriptEngine = MqoModelLoader
                .loadServerScriptForVehicle(def);
        } catch (Throwable t) {
            RealTrainModRenewed.LOGGER.warn("Failed to load server script for {}: {}", id, t.toString());
        }
    }

    /**
     * RTM の {@code entity.field_70170_p} 互換オブジェクト。SRB3 等のスクリプトが
     * world に setBlock 等を呼ぶための薄いシム。
     */
    public static final class CarWorldCompat {
        private final CarEntity car;
        public boolean field_72995_K;

        public CarWorldCompat(CarEntity car) {
            this.car = car;
        }

        public boolean isClientSide() {
            field_72995_K = car != null && car.level().isClientSide();
            return field_72995_K;
        }

        public net.minecraft.world.level.Level getLevel() {
            return car != null ? car.level() : null;
        }

        /** func_175625_s = getBlockEntity(BlockPos) (SRB3 の getTileEntity が呼ぶ) */
        public net.minecraft.world.level.block.entity.BlockEntity func_175625_s(net.minecraft.core.BlockPos pos) {
            return (car != null && pos != null) ? car.level().getBlockEntity(pos) : null;
        }

        /** 座標直接版。スクリプトが 1.12.2 の net.minecraft.util.math.BlockPos を new せずに済むよう用意。 */
        public net.minecraft.world.level.block.entity.BlockEntity func_175625_s(double x, double y, double z) {
            if (car == null) {
                return null;
            }
            return car.level().getBlockEntity(new net.minecraft.core.BlockPos(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        /** func_73045_a = getEntity(id) (SRB3 が hostPlayerEntityId からプレイヤーを引く) */
        public net.minecraft.world.entity.Entity func_73045_a(Object id) {
            if (car == null || id == null) {
                return null;
            }
            try {
                int i = (id instanceof Number) ? ((Number) id).intValue() : Integer.parseInt(id.toString());
                return car.level().getEntity(i);
            } catch (Throwable t) {
                return null;
            }
        }

        /** func_180495_p = getBlockState(BlockPos) */
        public net.minecraft.world.level.block.state.BlockState func_180495_p(net.minecraft.core.BlockPos pos) {
            return (car != null && pos != null) ? car.level().getBlockState(pos) : null;
        }
    }

    /** RTM 互換: スクリプトから entity.getResourceState() で呼ばれる。 */
    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    public static final class ResourceStateCompat {
        private final CarEntity car;
        public ResourceStateCompat(CarEntity car) { this.car = car; }
        public DataMapCompat getDataMap() { return new DataMapCompat(car); }
    }

    /** RTM 互換: scriptData への読み書きを media する。 */
    public static final class DataMapCompat {
        private final CarEntity car;
        public DataMapCompat(CarEntity car) { this.car = car; }
        public String getString(String key) { return car == null ? "" : car.getScriptDataValue(key); }
        public boolean getBoolean(String key) {
            String v = getString(key);
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }
        public int getInt(String key) {
            try { return Integer.parseInt(getString(key)); } catch (Exception e) { return 0; }
        }
        public double getDouble(String key) {
            try { return Double.parseDouble(getString(key)); } catch (Exception e) { return 0.0; }
        }
        public void setString(String key, String value, int syncType) {
            apply(key, value == null ? "" : value, syncType);
        }
        public void setBoolean(String key, boolean value, int syncType) {
            apply(key, Boolean.toString(value), syncType);
        }
        public void setInt(String key, int value, int syncType) {
            apply(key, Integer.toString(value), syncType);
        }
        public void setDouble(String key, double value, int syncType) {
            apply(key, Double.toString(value), syncType);
        }
        /**
         * ローカルへ書き込みつつ、クライアント側で syncType!=0 の値はサーバへ送る。
         * render(クライアント)スクリプトが書いた設置点/ビルドフラグをサーバ onUpdate へ届け、
         * 実際の敷設をサーバで行えるようにする。
         */
        private void apply(String key, String value, int syncType) {
            if (car == null) {
                return;
            }
            car.setScriptDataValue(key, value);
            if (syncType != 0 && car.level().isClientSide()) {
                try {
                    net.minecraft.client.multiplayer.ClientPacketListener connection =
                        net.minecraft.client.Minecraft.getInstance().getConnection();
                    if (connection != null) {
                        connection.send(new CarScriptDataPayload(car.getId(), key, value));
                    }
                } catch (Throwable ignored) {
                    // サーバ未接続/送信失敗時は無視(ローカルには書けている)。
                }
            }
        }
    }

    // ===== RTM 1.12.2 MCP 名の互換メソッド (SRB3 等のサーバスクリプトが直接呼ぶ) =====
    /** func_184188_bt = getPassengers() */
    public java.util.List<Entity> func_184188_bt() {
        return this.getPassengers();
    }
    /** func_184187_bx = getVehicle() (乗っている対象) */
    public Entity func_184187_bx() {
        return this.getVehicle();
    }
    /** func_184210_p = stopRiding() (降車/乗り物から降りる) */
    public void func_184210_p() {
        this.stopRiding();
    }
    /** func_145782_y = getId() (エンティティID) */
    public int func_145782_y() {
        return this.getId();
    }
    /** func_70106_y = discard() (エンティティ除去) */
    public void func_70106_y() {
        this.discard();
    }
    /** func_70107_b = setPos(x,y,z) */
    public void func_70107_b(double x, double y, double z) {
        this.setPos(x, y, z);
    }

    /// 右クリックされた時の処理
    ///
    /// @param player 右クリックしたプレイヤー
    /// @param hand   メインハンドまたはオフハンド
    /// @return 処理の完了状態
    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand, @NotNull Vec3 location) {
        if (this.canAddPassenger(player)) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().size() < RIDING_CAPACITY;
    }


    /// 操縦しているLivingEntity
    ///
    /// @return あればそのLivingEntity、なければnull
    @Override
    public LivingEntity getControllingPassenger() {
        final var passengers = this.getPassengers();
        final var controllingEntity = passengers.isEmpty() ? null : passengers.getFirst();
        return controllingEntity instanceof LivingEntity controllingLivingEntity ? controllingLivingEntity : null;
    }

    /// 渡された乗客Entityの着席位置
    ///
    /// @param passenger   乗客Entity
    /// @param dimensions  自動車の情報 寸法、目の高さなど
    /// @param partialTick なぜ？
    /// @return 位置のベクトル
    @Override
    @NotNull
    protected Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger, @NotNull EntityDimensions dimensions, float partialTick) {
        // 友達がいないのでデバッグできません(泣)
        final var index = this.getPassengers().indexOf(passenger);

        final var baseOffset = calcBaseOffset(index, dimensions);

        final var yRot = this.getViewYRot(partialTick);
        final var rotatedHorizontalOffset = baseOffset.yRot((float) -Math.toRadians(yRot));

        return new Vec3(rotatedHorizontalOffset.x, baseOffset.y, rotatedHorizontalOffset.z);
    }

    private Vec3 calcBaseOffset(int index, EntityDimensions dimensions) {
        final var heightBase = dimensions.height() * 0.2;
        return switch (index) {
            case 0 -> new Vec3(-0.42, heightBase, 0.1);
            case 1 -> new Vec3(0.42, heightBase, 0.1);
            case 2 -> new Vec3(0.42, heightBase, -1.0);
            case 3 -> new Vec3(-0.42, heightBase, -1.0);
            case 4 -> new Vec3(0.0, heightBase, -1.0);
            default -> new Vec3(0.0, dimensions.height() * 0.9, 0.0); // nullが返せないので、Mr.ビーンの場所にしとく
        };
    }

    /// 乗客の向きを車両と同期するために使用 詳細不明
    @Override
    protected void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (!(passenger instanceof Player player)) return;
        player.setYRot(player.getYRot() + this.deltaYaw);
    }

    /// 謎
    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return true;
    }

    /// 体当たりをして押せるかどうかだと思われる
    ///
    /// @return 常に偽 自動車だし押せなくていいよね
    @Override
    public boolean isPushable() {
        return false;
    }

    /// クリック判定を発生させるかどうかだと思われる
    ///
    /// @return もちろん発生させる じゃないと乗れない
    @Override
    public boolean isPickable() {
        return true;
    }

    /// バール・素手でプレイヤーが攻撃したら車を撤去してアイテムを回収する
    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player player)) return false;
        ItemStack held = player.getMainHandItem();
        // バールまたは素手のみ撤去可能
        if (!held.isEmpty() && !(held.getItem() instanceof CrowbarItem)) return false;
        if (!this.getPassengers().isEmpty()) {
            this.ejectPassengers();
        }
        this.spawnAtLocation(level, new ItemStack(RealTrainModRenewedItems.CAR_ITEM.get()));
        this.discard();
        return true;
    }

    /// 用途不明
    @Override
    @NotNull
    public Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    /// 毎Tick呼び出される
    @Override
    public void tick() {
        super.tick();

        // RTM 互換フィールドを最新値に同期 (SRB3 等のレガシースクリプトが直接読む)
        this.field_70177_z = getYRot();
        this.field_70125_A = getXRot();
        this.field_70173_aa = this.tickCount;
        this.field_70170_p.isClientSide();

        // サーバ側で vehicle 紐付けスクリプト（SRB3 等）を毎tick実行する。
        // クライアントでは何もしない（DataMap 同期は別経路）。
        if (!this.level().isClientSide()) {
            ensureServerScriptLoaded();
            if (serverScriptEngine != null) {
                TrainScriptSystem
                    .invokeServerScriptOnUpdate(serverScriptEngine, this);
                // サーバスクリプトが entity.field_70177_z=0 等で向きを制御する(SRB3はyawを0固定
                // してマーカーをワールド座標で描く)。シムのフィールドを実際の向きへ反映する。
                // 反映しないと車の実yawが残り、render が回転してマーカーが散らばる。
                this.setYRot(this.field_70177_z);
                this.setXRot(this.field_70125_A);
                this.yRotO = this.field_70177_z;
                this.xRotO = this.field_70125_A;
            }
            // サーバ→クライアント scriptData 同期。SRB3 の render(クライアント)は
            // hostPlayerEntityId 等のサーバ設定値を読んで GUI を起動するため必須。
            if (scriptDataDirty && !scriptData.isEmpty()) {
                scriptDataDirty = false;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    this, new CarScriptDataSyncPayload(
                        this.getId(), new java.util.HashMap<>(scriptData)));
            }
        }

        // クライアント: SRB の追従車はホストプレイヤーの位置・旧位置を完全ミラー(+2Y)する。
        // サーバ同期＋補間だとプレイヤーに遅れて、マーカー(車基準)とカーソル(プレイヤー基準)が
        // ズレて荒ぶる。旧位置までコピーすることで car の描画補間位置 = player の描画補間位置 となり、
        // マーカーとカーソルがフレーム単位で完全一致する。
        if (this.level().isClientSide()) {
            String hostId = getScriptDataValue("hostPlayerEntityId");
            if (hostId != null && !hostId.isEmpty()) {
                try {
                    Entity host = this.level().getEntity(Integer.parseInt(hostId));
                    if (host != null) {
                        this.setPos(host.getX(), host.getY() + 2.0D, host.getZ());
                        this.xOld = host.xOld;
                        this.yOld = host.yOld + 2.0D;
                        this.zOld = host.zOld;
                        this.xo = host.xo;
                        this.yo = host.yo + 2.0D;
                        this.zo = host.zo;
                        // SRB はマーカーをワールド座標で描くため車の yaw は 0 固定。クライアントでも
                        // 0 に固定し、補間で車が回転してマーカーが回って見える(荒ぶる)のを防ぐ。
                        this.setYRot(0.0F);
                        this.setXRot(0.0F);
                        this.yRotO = 0.0F;
                        this.xRotO = 0.0F;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        @SuppressWarnings("resource") final var level = this.level();

        this.prevSteeringWheelAngle = this.currentSteeringWheelAngle; // アニメーションのために前回tickの回転角度を保存

        // Entity#isControlledByLocalInstance は、自身が乗っている場合はクライアント、そうでなければサーバーでtrue
        // Entityの移動操作に使うとよいっぽい
        // マルチプレイでどうなるかはわからないが、テストする友達がいません（泣）
        // 降りた後に惰性で動かないので、とりあえずコメントアウトして無効化 要研究
//        if (!this.isControlledByLocalInstance()) return;

        final var driver = this.getControllingPassenger();
        if (driver instanceof Player drivingPlayer) {
            this.handlePlayerInput(drivingPlayer);
        } else {
            // 操縦手がいない
            this.updatePedals(); // ペダルのストロークを更新
            this.updateSteeringAngle(); // ステアリング角度を更新
        }

        this.updateSpeed(); // 速度を更新
        this.applyMovement(); // 移動量を計算

        if (level.isClientSide()) {
            updateWheelRotationInClient(); // 車輪の回転を反映
        }

        // 移動を実行
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    /// 運転しているプレイヤーの操作を反映する
    ///
    /// ### 動作
    /// - 進行方向のキーでアクセル（`acceleratorStroke`を増加）
    /// - 進行方向反対のキーでブレーキ（`brakeStroke`を増加）
    /// - ブレーキで停止したのち、一度離してもう一度押し始めると反対側に進行方向を転換（`isReversing`を逆転）し、
    ///   そのままその方向に加速を始める
    private void handlePlayerInput(Player player) {

        // プレイヤーの操作
        final float wS = player.zza; // W: 0.98, S: -0.98
        final float aD = player.xxa; // A: 0.98, D: -0.98

        if (wS > 0.0f) { // 前キー（W）
            final var justStartedW = this.prevWs <= 0.0f;
            if (!this.isReversing) { // 前進（アクセル）
                this.brakeStroke = 0.0f;
                this.isBraking = false;

                final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE; // 踏む量を増やす
                this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
            } else { // 後進（ブレーキ）
                this.acceleratorStroke = 0.0f;

                if (this.isStopping() && justStartedW && !this.isReversalLocked) { // 新たに押され、ロックされていない
                    this.isReversing = false; // 前進を開始
                    this.brakeStroke = 0.0f;

                    final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE;
                    this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
                } else {
                    // 通常のブレーキ処理
                    if (this.speed >= -SPEED_STOP_THRESHOLD) { // 転換しない場合ロックをセット
                        this.isReversalLocked = true;
                    }
                    final var stroke = this.brakeStroke + BRAKE_STROKE_CHANGE_RATE;
                    this.brakeStroke = Math.clamp(stroke, 0.0f, 1.0f);
                }
            }
        } else if (wS < 0.0f) { // 後ろキー（S）
            final var justStartedS = this.prevWs >= 0.0f;
            if (this.isReversing) { // 後進（アクセル）
                this.brakeStroke = 0.0f;
                this.isBraking = false;

                final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE;
                this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
            } else { // 前進（ブレーキ）
                this.acceleratorStroke = 0.0f;

                if (this.isStopping() && justStartedS && !this.isReversalLocked) { // 新たに押され、ロックされていない
                    this.isReversing = true;
                    this.brakeStroke = 0.0f;

                    final var stroke = this.acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE;
                    this.acceleratorStroke = Math.clamp(stroke, 0.0f, 1.0f);
                } else {
                    // 通常のブレーキ処理
                    if (this.speed <= SPEED_STOP_THRESHOLD) {
                        this.isReversalLocked = true;
                    }
                    final var stroke = this.brakeStroke + BRAKE_STROKE_CHANGE_RATE;
                    this.brakeStroke = Math.clamp(stroke, 0.0f, 1.0f);
                }
            }
        } else { // 操作されていない
            this.isReversalLocked = false; // 方向転換停止を解除

            // 無人の時と同様に処理
            this.updatePedals();
        }

        this.prevWs = wS; // wSを保存

        if (aD != 0) {
            final var angle = this.currentSteeringWheelAngle + Math.signum(aD) * -STEERING_WHEEL_ANGULAR_VELOCITY_MANIPULATED; // Aが正、Dが負だが、ヨーは逆
            this.currentSteeringWheelAngle = Math.clamp(angle, -STEERING_WHEEL_MAX_ANGLE, STEERING_WHEEL_MAX_ANGLE);
        } else { // 操作されていない
            // 無人の時と同様に処理
            this.updateSteeringAngle();
        }
    }
//         実際の移動
//        this.setYRot(this.getYRot() + turn);
//        this.setDeltaMovement(this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z + forward);

//        if (forward != 0) {
//            acceleration += forward * 0.02f;
//            acceleration = Math.clamp(acceleration, -maxSpeed, maxSpeed);
//            // 移動方向
//            // Entity#yRotはたぶん度数法
//            float yaw = this.getYRot() * (float) Math.PI / 180.0f;
//            Vec3 forwardVec = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
//            Vec3 motion = forwardVec.scale(acceleration);
//
//            // 問答無用でX+へ移動
//            this.setDeltaMovement(1.0f, this.getDeltaMovement().y, this.getDeltaMovement().z);
//            // 回転処理（移動中のみ）
//            if (Math.abs(acceleration) > 0.01f) {
//                this.setYRot(this.getYRot() + turn * turnSpeed * Math.signum(acceleration));
//            }
//        }
//        this.getEntityData().set(DATA_SPEED, Math.abs(acceleration));

    /// 操作されていないときに自然にペダルを処理する
    private void updatePedals() {
        // 踏んだ時と同じ割合で減らす
        // あるいは即時0？ どちらが実際の運転の感覚と似ているだろうか
        final var accelStroke = this.acceleratorStroke - ACCELERATOR_STROKE_CHANGE_RATE;
        this.acceleratorStroke = Math.clamp(accelStroke, 0.0f, 1.0f);
        final var brakeStroke = this.brakeStroke - BRAKE_STROKE_CHANGE_RATE;
        this.brakeStroke = Math.clamp(brakeStroke, 0.0f, 1.0f);
    }

    /// 操作されていないときに自然にステアリングを処理する
    private void updateSteeringAngle() {
        // 速度に応じてセルフセンタリングさせる処理
        // 前進では切れ角を減らし、後進では増える

        final var angle = this.currentSteeringWheelAngle;
        // 移動距離が大きいほど変化量も大きくなる 0に近づくほど変わりづらくなる
        this.currentSteeringWheelAngle = angle * (1 - STEERING_WHEEL_SELF_CENTERING_PARAMETER * this.speed * SECONDS_IN_TICK);
    }

    /// 速度を更新する
    private void updateSpeed() {
        var newSpeed = this.speed;

        if (this.acceleratorStroke > 0.0f) {
            newSpeed += (isReversing ? -1 : +1) * this.acceleratorStroke * ACCELERATION;
        } else if (this.brakeStroke > 0.0f) {
            newSpeed += (isReversing ? +1 : -1) * this.brakeStroke * DECELERATION;
        } else {
            newSpeed *= 1 - SLOWDOWN_DECELERATION;
        }

        if (isReversing) {
            newSpeed = Math.clamp(newSpeed, -MAX_SPEED * 0.2f, 0.0f);
        } else {
            newSpeed = Math.clamp(newSpeed, 0.0f, MAX_SPEED);
        }

        this.speed = Math.clamp(newSpeed, -MAX_SPEED * 0.2f, MAX_SPEED);
    }


    /// アッカーマンジオメトリを遵守した四輪自動車の移動と回転の結果でdeltaMovementとyawを更新
    private void applyMovement() {
        // 正接で面倒が起きないようにステアリング角度が0度に近い場合は直接前進
        if (Math.abs(this.currentSteeringWheelAngle) < 1.0f) {
            final var movement = Vec3.directionFromRotation(0, this.getYRot()).scale(this.speed);
            this.setDeltaMovement(movement);
            return;
        }

        // 実舵角（ラジアン）
        final double steerAngle = Math.toRadians(this.currentSteeringWheelAngle * STEERING_RATIO);

        // 後輪軸基準の旋回半径
        final double R = WHEELBASE / Math.tan(steerAngle);

        // 1tick あたりのヨー変化量（後輪軸速度 = this.speed と仮定）
        final double dYawRad = this.speed / R;
        final float dYawDeg = (float) Math.toDegrees(dYawRad);
        this.deltaYaw = dYawDeg;

        final float currentYaw = this.getYRot();
        final Vec3 forward = Vec3.directionFromRotation(0.0f, currentYaw);

        // ① エンティティ原点 → 後輪軸のワールド座標
        //    WHEEL_R_COORD < 0 なので forward.scale(WHEEL_R_COORD) は後方向
        final Vec3 rearAxlePos = this.position().add(forward.scale(WHEEL_R_COORD));

        // ② ICR = 後輪軸の右方向に距離 R
        //    Minecraft の YRot は時計回りが正なので +90 で右方向になる
        final Vec3 rightVec = Vec3.directionFromRotation(0.0f, currentYaw + 90.0f);
        final Vec3 icrPos = rearAxlePos.add(rightVec.scale(R));

        // ③ 後輪軸を ICR 周りに dYawRad だけ回転
        //    Minecraft XZ 平面（上から見て時計回りが正）の回転行列:
        //      x' = cx + cos(θ)·dx - sin(θ)·dz
        //      z' = cz + sin(θ)·dx + cos(θ)·dz
        final double dx = rearAxlePos.x - icrPos.x;
        final double dz = rearAxlePos.z - icrPos.z;
        final double cos = Math.cos(dYawRad);
        final double sin = Math.sin(dYawRad);
        final Vec3 newRearAxlePos = new Vec3(
            icrPos.x + cos * dx - sin * dz,
            rearAxlePos.y,
            icrPos.z + sin * dx + cos * dz
        );

        // ④ 新しいヨーと前方向を確定
        final float newYaw = currentYaw + dYawDeg;
        final Vec3 newForward = Vec3.directionFromRotation(0, newYaw);

        // ⑤ 後輪軸からエンティティ原点を逆算
        //    entityPos = rearAxlePos - forward * WHEEL_R_COORD
        //              = rearAxlePos + forward * |WHEEL_R_COORD|  （WHEEL_R_COORD < 0）
        final Vec3 newEntityPos = newRearAxlePos.subtract(newForward.scale(WHEEL_R_COORD));

        // ⑥ deltaMovement と yaw を設定
        this.setDeltaMovement(newEntityPos.subtract(this.position()));
        this.setYRot(newYaw);

    }

    /// 車輪の回転角度を更新する クライアントのみ
    private void updateWheelRotationInClient() {
        this.prevWheelRotation = this.wheelRotation;

        final var deltaRotation = this.speed / WHEEL_RADIUS;
        this.wheelRotation += deltaRotation; // 直接加算 340潤ラジアン回ることは多分ないと信じる
    }

    private boolean isStopping() {
        return Math.abs(this.speed) < SPEED_STOP_THRESHOLD;
    }
}

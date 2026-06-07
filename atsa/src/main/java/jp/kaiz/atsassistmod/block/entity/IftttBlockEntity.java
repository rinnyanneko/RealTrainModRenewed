package jp.kaiz.atsassistmod.block.entity;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import com.mojang.serialization.Codec;
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer;
import jp.kaiz.atsassistmod.ifttt.IFTTTUtil;
import jp.kaiz.atsassistmod.registry.ATSAModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * IFTTT block entity (port of TileEntityIFTTT). Holds the THIS (condition) and THAT
 * (action) rule lists, evaluates them each server tick against the train on top of
 * the block, and runs the actions.
 */
public class IftttBlockEntity extends BlockEntity {
    private int redStoneOutput;
    private boolean notFirst;
    private boolean anyMatch;
    private List<IFTTTContainer> thisList = new ArrayList<>();
    private List<IFTTTContainer> thatList = new ArrayList<>();

    public IftttBlockEntity(BlockPos pos, BlockState state) {
        super(ATSAModBlockEntities.IFTTT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IftttBlockEntity be) {
        be.tick();
    }

    private void tick() {
        if (level == null || level.isClientSide() || thisList.isEmpty() || thatList.isEmpty()) {
            return;
        }
        AABB detect = new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
                worldPosition.getX() + 2, worldPosition.getY() + 4, worldPosition.getZ() + 2);
        List<TrainEntity> trains = level.getEntitiesOfClass(TrainEntity.class, detect);
        TrainEntity train = trains.isEmpty() ? null : trains.get(0);

        boolean match = anyMatch
                ? thisList.stream().anyMatch(c -> ((IFTTTContainer.This) c).isCondition(this, train))
                : thisList.stream().allMatch(c -> ((IFTTTContainer.This) c).isCondition(this, train));

        if (match) {
            boolean first = !notFirst;
            for (IFTTTContainer c : thatList) {
                ((IFTTTContainer.That) c).doThat(this, train, first);
            }
            notFirst = true;
        } else if (notFirst) {
            for (IFTTTContainer c : thatList) {
                ((IFTTTContainer.That) c).finish(this, train);
            }
            setRedStoneOutput(0);
            notFirst = false;
        }
    }

    public int getRedStoneOutput() {
        return redStoneOutput;
    }

    public void setRedStoneOutput(int power) {
        if (this.redStoneOutput != power) {
            this.redStoneOutput = power;
            setChanged();
            if (level != null) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    public boolean isAnyMatch() { return anyMatch; }
    public void setAnyMatch(boolean anyMatch) { this.anyMatch = anyMatch; }
    public List<IFTTTContainer> getThisList() { return thisList; }
    public List<IFTTTContainer> getThatList() { return thatList; }

    public void addIFTTT(IFTTTContainer c) {
        if (c instanceof IFTTTContainer.This) {
            if (thisList.size() < 6) thisList.add(c);
        } else if (c instanceof IFTTTContainer.That) {
            if (thatList.size() < 6) thatList.add(c);
        }
    }

    public void setIFTTT(IFTTTContainer c, int index) {
        if (c instanceof IFTTTContainer.This) {
            if (thisList.size() > index) thisList.set(index, c); else addIFTTT(c);
        } else if (c instanceof IFTTTContainer.That) {
            if (thatList.size() > index) thatList.set(index, c); else addIFTTT(c);
        }
    }

    public void removeIFTTT(IFTTTContainer c, int index) {
        if (c instanceof IFTTTContainer.This) {
            if (index >= 0 && index < thisList.size()) thisList.remove(index);
        } else if (c instanceof IFTTTContainer.That) {
            if (index >= 0 && index < thatList.size()) thatList.remove(index);
        }
    }

    public void replaceLists(List<IFTTTContainer> newThis, List<IFTTTContainer> newThat, boolean anyMatch) {
        this.thisList = new ArrayList<>(newThis);
        this.thatList = new ArrayList<>(newThat);
        this.anyMatch = anyMatch;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ----------------------------------------------------------------- NBT
    private static void saveList(ValueOutput output, String key, List<IFTTTContainer> list) {
        ValueOutput.TypedOutputList<ByteBuffer> tag = output.list(key, Codec.BYTE_BUFFER);
        for (IFTTTContainer c : list) {
            byte[] bytes = IFTTTUtil.toBytes(c);
            if (bytes != null) {
                tag.add(ByteBuffer.wrap(bytes));
            }
        }
    }

    private static List<IFTTTContainer> loadList(ValueInput input, String key) {
        List<IFTTTContainer> list = new ArrayList<>();
        for (ByteBuffer buffer : input.listOrEmpty(key, Codec.BYTE_BUFFER)) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            IFTTTContainer c = IFTTTUtil.fromBytes(bytes);
            if (c != null) {
                list.add(c);
            }
        }
        return list;
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putInt("redStoneOutput", redStoneOutput);
        tag.putBoolean("notFirst", notFirst);
        tag.putBoolean("anyMatch", anyMatch);
        saveList(tag, "iftttThisList", thisList);
        saveList(tag, "iftttThatList", thatList);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        redStoneOutput = tag.getIntOr("redStoneOutput", 0);
        notFirst = tag.getBooleanOr("notFirst", false);
        anyMatch = tag.getBooleanOr("anyMatch", false);
        thisList = loadList(tag, "iftttThisList");
        thatList = loadList(tag, "iftttThatList");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

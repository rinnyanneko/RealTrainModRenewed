package com.portofino.realtrainmodunofficial.rail.util;

import net.minecraft.world.level.Level;

public final class RailMapSwitch extends RailMapBasic {
    public final RailDir startDir;
    public final RailDir endDir;
    private boolean open;

    public RailMapSwitch(RailPosition start, RailPosition end, RailDir startDir, RailDir endDir) {
        super(start, end);
        this.startDir = startDir;
        this.endDir = endDir;
    }

    public RailMapSwitch setState(boolean open) {
        this.open = open;
        return this;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isGettingPowered(Level level) {
        return super.isGettingPowered(level);
    }
}

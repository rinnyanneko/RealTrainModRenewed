package jp.kaiz.atsassistmod.util;

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;

/**
 * Compass direction test for the IFTTT "Train direction" condition. The original
 * compared front/back bogie positions; RTM's new bogie compat does not expose bogie
 * coordinates, so this derives the heading from the head car's yaw + train direction.
 */
public enum CardinalDirection {
    NORTH("NORTH", false, Axis.Z),
    EAST("EAST", true, Axis.X),
    SOUTH("SOUTH", true, Axis.Z),
    WEST("WEST", false, Axis.X);

    private final String name;
    private final boolean positive;
    private final Axis axis;

    CardinalDirection(String name, boolean positive, Axis axis) {
        this.name = name;
        this.positive = positive;
        this.axis = axis;
    }

    public String getName() {
        return name;
    }

    public boolean isInDirection(TrainEntity train) {
        double yaw = Math.toRadians(train.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        if (train.getTrainDirection() != 0) {
            fx = -fx;
            fz = -fz;
        }
        return switch (axis) {
            case X -> positive == (fx > 0);
            case Z -> positive == (fz > 0);
        };
    }

    public static CardinalDirection getDirection(String name) {
        try {
            return CardinalDirection.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORTH;
        }
    }

    private enum Axis { X, Z }
}

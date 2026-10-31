package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;

/** Geometry plans expose occupied blocks and the model instances to place in them. */
public interface TrackPlacementPlan {
    List<BlockPos> positions();

    List<? extends Piece> pieces();

    interface Piece {
        BlockPos pos();

        Direction facing();

        double angle();

        double offsetX();

        double offsetZ();

        default int curveRadius() { return 0; }

        default double curveSweep() { return 0.0D; }

        default double curveArc() { return 0.0D; }

        default double curveEntryAngle() { return angle(); }

        default double fitAdvance() { return 0.0D; }

        default double fitInward() { return 0.0D; }

        default boolean curveLeft() { return false; }
    }
}

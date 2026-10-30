package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Samples a grid-aligned diagonal centerline into model placements no more than one block apart. */
public final class DiagonalPlan implements TrackPlacementPlan {
    public record Piece(BlockPos pos, Direction facing, double arc, double angle,
                        double offsetX, double offsetZ) implements TrackPlacementPlan.Piece {
    }

    private final DiagonalSolver.Solution solution;
    private final int multiplier;
    private final double totalLength;
    private final List<Piece> pieces;
    private final List<BlockPos> positions;

    private DiagonalPlan(DiagonalSolver.Solution solution, int multiplier, double totalLength,
                         List<Piece> pieces) {
        this.solution = solution;
        this.multiplier = multiplier;
        this.totalLength = totalLength;
        this.pieces = List.copyOf(pieces);
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (Piece piece : pieces) {
            unique.add(piece.pos());
        }
        this.positions = List.copyOf(unique);
    }

    public static DiagonalPlan of(BlockPos start, Direction entry,
                                  DiagonalSolver.Solution solution, int multiplier) {
        int safeMultiplier = Math.max(1, Math.min(DiagonalSolver.maxMultiplier(solution), multiplier));
        double ex = RailCurve.directionX(entry);
        double ez = RailCurve.directionZ(entry);
        double dx = ex * solution.advance() - ez * solution.lateral();
        double dz = ez * solution.advance() + ex * solution.lateral();
        double startX = start.getX() + 0.5D;
        double startZ = start.getZ() + 0.5D;
        double length = solution.minimumLength() * safeMultiplier;
        int intervals = Math.max(1, (int) Math.ceil(length));
        double modelAngle = Math.atan2(-dz, -dx);
        Direction facing = nearestDirection(dx, dz);
        List<Piece> pieces = new ArrayList<>(intervals + 1);
        for (int index = 0; index <= intervals; index++) {
            double arc = index == intervals ? length : length * index / intervals;
            double progress = index == intervals ? safeMultiplier : arc / solution.minimumLength();
            double x = startX + dx * progress;
            double z = startZ + dz * progress;
            BlockPos pos = BlockPos.containing(x, start.getY(), z);
            pieces.add(new Piece(pos, facing, arc, modelAngle,
                    x - pos.getX(), z - pos.getZ()));
        }
        return new DiagonalPlan(solution, safeMultiplier, length, pieces);
    }

    private static Direction nearestDirection(double x, double z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    public DiagonalSolver.Solution solution() {
        return solution;
    }

    public int multiplier() {
        return multiplier;
    }

    public double totalLength() {
        return totalLength;
    }

    @Override
    public List<Piece> pieces() {
        return pieces;
    }

    @Override
    public List<BlockPos> positions() {
        return positions;
    }
}

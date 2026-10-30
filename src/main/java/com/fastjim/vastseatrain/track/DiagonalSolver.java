package com.fastjim.vastseatrain.track;

import java.util.ArrayList;
import java.util.List;
import com.fastjim.vastseatrain.VTConfig;
import net.minecraft.core.Direction;

/** Resolves a requested angle to a primitive integer grid vector. */
public final class DiagonalSolver {
    /** Maximum coordinate span used when approximating slider angles. */
    public static final int MAX_COMPONENT = 128;

    public record Solution(int advance, int lateral, double requestedDegrees,
                           double actualDegrees, double minimumLength) {
        public int gcd() {
            return DiagonalSolver.gcd(advance, lateral);
        }
    }

    private static final List<Solution> DIRECTIONS = buildDirections();
    private static volatile SolutionTable cachedTable;

    private record SolutionTable(int maxPlacement, Solution[] solutions) {
    }

    private DiagonalSolver() {
    }

    /**
     * Finds the closest primitive grid direction. Both coordinate offsets are integers,
     * so every multiplier ends at a block-aligned point.
     */
    public static Solution solve(int requestedDegrees) {
        int requested = Math.max(0, Math.min(89, requestedDegrees));
        int maxPlacement = VTConfig.maxPlacement();
        SolutionTable table = cachedTable;
        if (table == null || table.maxPlacement() != maxPlacement) {
            table = tableFor(maxPlacement);
        }
        Solution solution = table.solutions()[requested];
        return solution;
    }

    /** Snaps player view to one of four cardinal headings rotated clockwise by the requested angle. */
    public static Direction facingFromLook(double lookX, double lookZ, int clockwiseOffset,
                                           Direction fallback) {
        if (Math.hypot(lookX, lookZ) < 1.0E-6D) {
            return fallback;
        }
        double bearing = Math.toDegrees(Math.atan2(lookX, -lookZ));
        double adjusted = bearing - Math.max(0, Math.min(89, clockwiseOffset));
        int sector = Math.floorMod((int) Math.floor((adjusted + 45.0D) / 90.0D), 4);
        return switch (sector) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    /** Largest whole number of grid-aligned increments under the shared placement limit. */
    public static int maxMultiplier(Solution solution) {
        return Math.max(1, (int) Math.floor(VTConfig.maxPlacement() / solution.minimumLength()));
    }

    private static SolutionTable tableFor(int maxPlacement) {
        SolutionTable table = cachedTable;
        if (table != null && table.maxPlacement() == maxPlacement) {
            return table;
        }
        synchronized (DiagonalSolver.class) {
            table = cachedTable;
            if (table != null && table.maxPlacement() == maxPlacement) {
                return table;
            }
            Solution[] solutions = new Solution[90];
            for (int requested = 0; requested < solutions.length; requested++) {
                Solution best = null;
                double bestError = Double.POSITIVE_INFINITY;
                for (Solution option : DIRECTIONS) {
                    if (option.minimumLength() > maxPlacement) continue;
                    double error = Math.abs(option.actualDegrees() - requested);
                    if (best == null || error < bestError
                            || error == bestError && option.minimumLength() < best.minimumLength()
                            || error == bestError && option.minimumLength() == best.minimumLength()
                            && option.advance() < best.advance()
                            || error == bestError && option.minimumLength() == best.minimumLength()
                            && option.advance() == best.advance() && option.lateral() < best.lateral()) {
                        best = option;
                        bestError = error;
                    }
                }
                if (best == null) {
                    throw new IllegalStateException("No grid-aligned track direction fits the placement limit");
                }
                solutions[requested] = new Solution(best.advance(), best.lateral(), requested,
                        best.actualDegrees(), best.minimumLength());
            }
            table = new SolutionTable(maxPlacement, solutions);
            cachedTable = table;
            return table;
        }
    }

    private static List<Solution> buildDirections() {
        List<Solution> result = new ArrayList<>();
        for (int advance = 0; advance <= MAX_COMPONENT; advance++) {
            for (int lateral = 0; lateral <= MAX_COMPONENT; lateral++) {
                if (advance == 0 || (lateral == 0 && advance != 1) || gcd(advance, lateral) != 1) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(lateral, advance));
                result.add(new Solution(advance, lateral, angle, angle,
                        Math.hypot(advance, lateral)));
            }
        }
        return List.copyOf(result);
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return Math.abs(a);
    }
}

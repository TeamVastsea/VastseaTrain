package com.fastjim.vastseatrain.client.model;

import com.fastjim.vastseatrain.block.RailCurveBlockEntity.Entry;
import com.fastjim.vastseatrain.VTConfig;
import com.fastjim.vastseatrain.track.CurvePlan;
import com.fastjim.vastseatrain.track.RailCurve;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.UUID;

/** Standalone geometry checks that can run without launching Minecraft. */
public final class RailCurveGeometryProbe {
    private static final double EPSILON = 1.0E-4D;

    private RailCurveGeometryProbe() {
    }

    public static void main(String[] args) {
        VTConfig.SPEC.acceptConfig(CommentedConfig.inMemory());
        verifiesTurnDirections();
        verifiesFittedEndpointsAndSampling();
        verifiesToleranceRejection();
        verifiesConfiguredLimits();
        verifiesIndependentRailArcLengths();
        verifiesRailContinuityBetweenPieces();
        verifiesLegacyStraightRailsAreUnchanged();
        System.out.println("Curve geometry checks passed");
    }

    private static void verifiesTurnDirections() {
        Direction[] entries = {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH};
        Direction[] leftExits = {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
        Direction[] rightExits = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
        for (int i = 0; i < entries.length; i++) {
            require(RailCurve.exit(entries[i], true) == leftExits[i], "left turn direction " + entries[i]);
            require(RailCurve.exit(entries[i], false) == rightExits[i], "right turn direction " + entries[i]);
            require(RailCurve.inward(entries[i], true) == leftExits[i], "left curve center " + entries[i]);
        }
    }

    private static void verifiesFittedEndpointsAndSampling() {
        BlockPos start = new BlockPos(10, 64, -7);
        for (Direction entry : new Direction[]{Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH}) {
            for (boolean left : new boolean[]{false, true}) {
                for (int angle : new int[]{30, 45, 67, 90}) {
                    CurvePlan plan = CurvePlan.of(start, entry, left, 10, angle, 1.0D);
                    CurvePlan.Piece first = plan.pieces().get(0);
                    CurvePlan.Piece last = plan.pieces().get(plan.pieces().size() - 1);
                    double endpointX = plan.frame().fittedX(plan.arcLength(), plan.fitAdvance(),
                            plan.fitInward(), plan.arcLength());
                    double endpointZ = plan.frame().fittedZ(plan.arcLength(), plan.fitAdvance(),
                            plan.fitInward(), plan.arcLength());
                    double radians = Math.toRadians(angle);
                    double advance = 10.0D * Math.sin(radians);
                    double inward = 10.0D * (1.0D - Math.cos(radians));
                    Direction turn = RailCurve.inward(entry, left);
                    double snappedX = start.getX() + 0.5D
                            + Math.rint(advance) * RailCurve.directionX(entry)
                            + Math.rint(inward) * RailCurve.directionX(turn);
                    double snappedZ = start.getZ() + 0.5D
                            + Math.rint(advance) * RailCurve.directionZ(entry)
                            + Math.rint(inward) * RailCurve.directionZ(turn);

                    close(endpointX, snappedX, EPSILON, "snapped endpoint X");
                    close(endpointZ, snappedZ, EPSILON, "snapped endpoint Z");
                    close(endpointX - (last.pos().getX() + last.offsetX()), 0.0D, EPSILON,
                            "piece endpoint X");
                    close(endpointZ - (last.pos().getZ() + last.offsetZ()), 0.0D, EPSILON,
                            "piece endpoint Z");
                    close(first.angle(), plan.frame().modelAngle(0.0D), EPSILON, "entry tangent");
                    close(last.angle(), plan.frame().fittedModelAngle(plan.arcLength(), plan.fitAdvance(),
                            plan.fitInward(), plan.arcLength()), EPSILON, "exit tangent");
                    close(plan.pieces().size(), CurvePlan.estimatePieces(10, angle), 0.0D,
                            "model instance estimate");
                    require(plan.step() <= 1.0D + EPSILON, "sample interval exceeds one block");
                    double exitX = RailCurve.directionX(entry) * Math.cos(radians)
                            + RailCurve.directionX(turn) * Math.sin(radians);
                    double exitZ = RailCurve.directionZ(entry) * Math.cos(radians)
                            + RailCurve.directionZ(turn) * Math.sin(radians);
                    require(plan.exit() == RailCurve.directionOf(exitX, exitZ),
                            "partial-turn exit direction at " + angle);
                    for (int i = 1; i < plan.pieces().size(); i++) {
                        require(Math.abs(plan.pieces().get(i).curveEntryAngle() - first.curveEntryAngle()) < EPSILON,
                                "curve entry angle must be constant across samples");
                    }
                }
            }
        }
    }

    private static void verifiesToleranceRejection() {
        boolean rejected = false;
        try {
            CurvePlan.of(BlockPos.ZERO, Direction.EAST, true, 10, 30, 0.0D);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "curve outside endpoint tolerance must be rejected");
    }

    private static void verifiesConfiguredLimits() {
        require(VTConfig.maxCurveRadius(1) == VTConfig.MAX_CURVE_RADIUS,
                "one-degree maximum radius must reach the configured ceiling");
        int maxRadius = VTConfig.maxCurveRadius(90);
        require(maxRadius <= VTConfig.MAX_CURVE_RADIUS, "maximum curve radius exceeds hard limit");
        require(VTConfig.curveArcLength(maxRadius, 90) <= VTConfig.MAX_CURVE_ARC_LENGTH,
                "maximum-radius curve exceeds arc-length limit");
        require(VTConfig.curveArcLength(maxRadius + 1, 90) > VTConfig.MAX_CURVE_ARC_LENGTH,
                "radius bound is not derived from arc-length limit");
        require(VTConfig.performanceWarningLevel(256) == 1, "warning threshold must be inclusive");
        require(VTConfig.performanceWarningLevel(512) == 2, "critical threshold must be inclusive");
    }

    private static void verifiesIndependentRailArcLengths() {
        double centerRadius = 100.0D;
        double sweep = Math.toRadians(45.0D);
        double total = centerRadius * sweep;
        BlockPos start = new BlockPos(0, 64, 0);
        for (boolean left : new boolean[]{false, true}) {
            RailCurve.Frame frame = RailCurve.frame(start, Direction.EAST, left, (int) centerRadius, sweep);
            double arc = total * 0.5D;
            double angle = frame.modelAngle(arc);
            double centerX = frame.fittedX(arc, 0.0D, 0.0D, total);
            double centerZ = frame.fittedZ(arc, 0.0D, 0.0D, total);
            BlockPos pos = BlockPos.containing(centerX, start.getY(), centerZ);
            Entry entry = new Entry((float) angle, (float) (centerX - pos.getX()),
                    (float) (centerZ - pos.getZ()), null, UUID.randomUUID(), (int) centerRadius,
                    (float) sweep, (float) arc, 0.0F, 0.0F, (float) total, left,
                    (float) frame.modelAngle(0.0D));

            for (double lateral : new double[]{-0.765D, 0.765D}) {
                double firstAngle = worldAngle(frame, pos, entry, -0.5F, (float) lateral);
                double lastAngle = worldAngle(frame, pos, entry, 0.5F, (float) lateral);
                double radius = centerRadius + (left ? -lateral : lateral);
                double angularSpan = Math.abs(lastAngle - firstAngle);
                close(radius * angularSpan, radius / centerRadius, 2.0E-3D,
                        "rail arc length must follow its own radius");
            }
        }
    }

    private static void verifiesRailContinuityBetweenPieces() {
        BlockPos start = new BlockPos(-23, 64, 41);
        for (Direction entryDirection : new Direction[]{Direction.EAST, Direction.NORTH,
                Direction.WEST, Direction.SOUTH}) {
            for (boolean left : new boolean[]{false, true}) {
                CurvePlan plan = CurvePlan.of(start, entryDirection, left, 10, 67, 1.0D);
                for (int i = 0; i + 1 < plan.pieces().size(); i++) {
                    CurvePlan.Piece first = plan.pieces().get(i);
                    CurvePlan.Piece second = plan.pieces().get(i + 1);
                    double halfStep = (second.arc() - first.arc()) * 0.5D;
                    for (double lateral : new double[]{-0.765D, 0.765D}) {
                        double[] firstPoint = worldPoint(first, -halfStep, lateral);
                        double[] secondPoint = worldPoint(second, halfStep, lateral);
                        close(firstPoint[0], secondPoint[0], 2.0E-4D, "rail seam X");
                        close(firstPoint[1], secondPoint[1], 2.0E-4D, "rail seam Z");
                    }
                }
            }
        }
    }

    private static double[] worldPoint(CurvePlan.Piece piece, double localX, double localZ) {
        float[] local = new float[2];
        Entry entry = new Entry((float) piece.angle(), (float) piece.offsetX(),
                (float) piece.offsetZ(), null, UUID.randomUUID(), piece.radius(), (float) piece.sweep(),
                (float) piece.arc(), (float) piece.fitAdvance(), (float) piece.fitInward(),
                (float) piece.total(), piece.left(), (float) piece.entryAngle());
        RailCurveBakedModel.fitRail(RailObjGeometry.Part.LEFT_RAIL, entry, (float) localX, (float) localZ,
                local, new double[6]);
        double cos = Math.cos(piece.angle());
        double sin = Math.sin(piece.angle());
        return new double[]{piece.pos().getX() + piece.offsetX() + cos * local[0] - sin * local[1],
                piece.pos().getZ() + piece.offsetZ() + sin * local[0] + cos * local[1]};
    }

    private static double worldAngle(RailCurve.Frame frame, BlockPos pos, Entry entry, float x, float z) {
        float[] local = new float[2];
        RailCurveBakedModel.fitRail(RailObjGeometry.Part.LEFT_RAIL, entry, x, z, local, new double[6]);
        double cos = Math.cos(entry.angle);
        double sin = Math.sin(entry.angle);
        double worldX = entry.originX + cos * local[0] - sin * local[1];
        double worldZ = entry.originZ + sin * local[0] + cos * local[1];
        return frame.angleOf(pos.getX() + worldX, pos.getZ() + worldZ);
    }

    private static void verifiesLegacyStraightRailsAreUnchanged() {
        Entry straight = new Entry((float) Math.PI, 0.5F, 0.5F);
        float[] out = new float[2];
        RailCurveBakedModel.fitRail(RailObjGeometry.Part.LEFT_RAIL, straight,
                0.25F, 0.75F, out, new double[6]);
        close(out[0], 0.25D, EPSILON, "legacy straight rail X");
        close(out[1], 0.75D, EPSILON, "legacy straight rail Z");
    }

    private static void close(double actual, double expected, double tolerance, String label) {
        require(Math.abs(actual - expected) <= tolerance,
                label + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

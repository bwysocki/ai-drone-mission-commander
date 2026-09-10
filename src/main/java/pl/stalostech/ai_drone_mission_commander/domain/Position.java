package pl.stalostech.ai_drone_mission_commander.domain;

/** Coordinates in kilometres on a flat simulated map. */
public record Position(double xKm, double yKm) {
    public static final Position BASE = new Position(0, 0);

    public Position {
        if (!Double.isFinite(xKm) || !Double.isFinite(yKm)) {
            throw new IllegalArgumentException("Coordinates must be finite");
        }
    }

    public double distanceTo(Position other) {
        return Math.hypot(xKm - other.xKm, yKm - other.yKm);
    }
}

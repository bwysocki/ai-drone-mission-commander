package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.List;

/** Battery usage is movement only; mission activity has a separate cost. */
public record Route(List<Position> waypoints, double distanceKm, int estimatedBatteryUsage) {
    public Route {
        waypoints = List.copyOf(waypoints);
        if (waypoints.size() < 2 || !Double.isFinite(distanceKm) || distanceKm < 0 || estimatedBatteryUsage < 0) {
            throw new IllegalArgumentException("Invalid route");
        }
    }
}

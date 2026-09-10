package pl.stalostech.ai_drone_mission_commander.simulation;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.domain.*;

@Service
public class RouteService {
    private final DroneWorld world;
    public RouteService(DroneWorld world) { this.world = world; }

    public Route calculate(String droneId, String sectorId, boolean returnHome) {
        synchronized (world) {
            var origin = world.drone(droneId).position();
            var target = world.sector(sectorId).position();
            return calculate(returnHome ? List.of(origin, target, Position.BASE) : List.of(origin, target), world.weather());
        }
    }

    Route calculate(List<Position> waypoints, Weather weather) {
        double distance = 0;
        int battery = 0;
        for (int i = 1; i < waypoints.size(); i++) {
            double leg = waypoints.get(i - 1).distanceTo(waypoints.get(i));
            distance += leg;
            battery = Math.addExact(battery, flightCost(leg, weather));
        }
        return new Route(waypoints, distance, battery);
    }

    int flightCost(double distance, Weather weather) {
        // Percentage points, rounded per leg; not a real-world battery model.
        double cost = Math.ceil(distance * 2 * (1 + weather.windKmh() / 20.0));
        if (!Double.isFinite(cost) || cost > Integer.MAX_VALUE || cost < 0) {
            throw new IllegalArgumentException("Route exceeds simulator limits");
        }
        return (int) cost;
    }
}

package pl.stalostech.ai_drone_mission_commander.simulation;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.domain.*;

@Service
public class SimulationEventService {
    public static final int MIN_RECENT_LIMIT = 1;
    public static final int MAX_RECENT_LIMIT = 20;
    private final DroneWorld world;
    public SimulationEventService(DroneWorld world) { this.world = world; }

    public List<SimulationEvent> recent(int limit) {
        if (limit < MIN_RECENT_LIMIT || limit > MAX_RECENT_LIMIT) {
            throw new IllegalArgumentException("Alert limit must be between 1 and 20");
        }
        return world.snapshot().events().reversed().stream().limit(limit).toList();
    }

    public SimulationEvent inject(SimulationEventType type, String droneId, Integer amount) {
        if (type == null) throw new IllegalArgumentException("Event type is required");
        synchronized (world) {
            if (type == SimulationEventType.STRONG_WIND) {
                if (droneId != null) throw new IllegalArgumentException("Wind is a world event; omit droneId");
                int wind = amount == null ? 45 : amount;
                if (wind < 30 || wind > 200) throw new IllegalArgumentException("Strong wind must be between 30 and 200 km/h");
                var current = world.weather();
                world.weather(new Weather(wind, current.rain(), current.visibility()));
                return world.event(type, null, wind);
            }
            var drone = world.drone(droneId);
            if (type != SimulationEventType.BATTERY_DROP && amount != null) {
                throw new IllegalArgumentException("Only battery and wind events accept amount");
            }
            int battery = drone.batteryPercent();
            int value = 0;
            var gps = drone.gps();
            boolean motor = drone.motorWarning();
            boolean communication = drone.communicationAvailable();
            switch (type) {
                case BATTERY_DROP -> {
                    int drop = amount == null ? 20 : amount;
                    if (drop < 1 || drop > 100) throw new IllegalArgumentException("Battery drop must be between 1 and 100");
                    value = Math.min(drop, battery);
                    battery -= value;
                }
                case GPS_DEGRADED -> {
                    if (gps != GpsStatus.LOST) gps = GpsStatus.DEGRADED;
                }
                case GPS_LOST -> gps = GpsStatus.LOST;
                case MOTOR_WARNING -> motor = true;
                case COMMUNICATION_LOST -> communication = false;
                default -> throw new IllegalArgumentException("Unsupported event");
            }
            world.update(new DroneStatus(drone.drone(), battery, drone.position(), drone.state(), gps, motor, communication));
            return world.event(type, drone.drone().id(), value);
        }
    }
}

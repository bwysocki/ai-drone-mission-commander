package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.List;
import java.util.Objects;

public record DroneWorldSnapshot(List<DroneStatus> drones, List<Sector> sectors, Weather weather,
                                 List<Mission> missions, List<SimulationEvent> events) {
    public DroneWorldSnapshot {
        drones = List.copyOf(drones);
        sectors = List.copyOf(sectors);
        Objects.requireNonNull(weather);
        missions = List.copyOf(missions);
        events = List.copyOf(events);
    }
}

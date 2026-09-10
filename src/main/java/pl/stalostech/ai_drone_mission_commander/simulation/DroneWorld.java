package pl.stalostech.ai_drone_mission_commander.simulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.SimulationNotFoundException;

/** Owns all mutable state. Services hold this monitor for an entire operation. */
@Component
public class DroneWorld {
    private final Map<String, DroneStatus> drones = new LinkedHashMap<>();
    private final Map<String, Sector> sectors = new LinkedHashMap<>();
    private final Map<String, Mission> missions = new LinkedHashMap<>();
    private final List<SimulationEvent> events = new ArrayList<>();
    private Weather weather;
    // Keep identifiers unique for this world's lifetime, including across resets.
    private long nextMission = 1;

    public DroneWorld() {
        reset();
    }

    public synchronized DroneWorldSnapshot reset() {
        drones.clear();
        sectors.clear();
        missions.clear();
        events.clear();
        sectors.put("SECTOR_A", new Sector("SECTOR_A", new Position(2, 0), "Open field"));
        sectors.put("SECTOR_B", new Sector("SECTOR_B", new Position(0, 3), "Warehouse perimeter"));
        sectors.put("SECTOR_C", new Sector("SECTOR_C", new Position(4, 3), "Remote access road"));
        seedDrone("alpha", "Alpha", 82, Position.BASE, DroneState.READY);
        seedDrone("bravo", "Bravo", 41, sectors.get("SECTOR_A").position(), DroneState.IDLE);
        seedDrone("charlie", "Charlie", 67, Position.BASE, DroneState.READY);
        weather = new Weather(12, false, Visibility.GOOD);
        return snapshot();
    }

    private void seedDrone(String id, String name, int battery, Position position, DroneState state) {
        drones.put(id, new DroneStatus(new Drone(id, name), battery, position, state, GpsStatus.AVAILABLE, false, true));
    }

    public synchronized DroneWorldSnapshot snapshot() {
        return new DroneWorldSnapshot(List.copyOf(drones.values()), List.copyOf(sectors.values()), weather,
                List.copyOf(missions.values()), List.copyOf(events));
    }

    public synchronized DroneStatus drone(String id) {
        var drone = drones.get(key(id).toLowerCase(Locale.ROOT));
        if (drone == null) throw new SimulationNotFoundException("Drone not found");
        return drone;
    }

    public synchronized Sector sector(String id) {
        var sector = sectors.get(key(id).toUpperCase(Locale.ROOT));
        if (sector == null) throw new SimulationNotFoundException("Sector not found");
        return sector;
    }

    public synchronized Mission mission(String id) {
        var mission = missions.get(key(id));
        if (mission == null) throw new SimulationNotFoundException("Mission not found");
        return mission;
    }

    public synchronized Weather weather() { return weather; }

    // Package-private mutations are used only while holding the world monitor.
    void update(DroneStatus drone) { drones.put(drone.drone().id(), drone); }
    Mission save(Mission mission) { missions.put(mission.id(), mission); return mission; }
    String missionId() { return "mission-" + nextMission++; }
    void weather(Weather value) { weather = value; }
    SimulationEvent event(SimulationEventType type, String droneId, int value) {
        var event = new SimulationEvent(events.size() + 1L, type, droneId, value);
        events.add(event);
        return event;
    }

    private static String key(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identifier must not be blank");
        return value.strip();
    }
}

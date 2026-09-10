package pl.stalostech.ai_drone_mission_commander.simulation;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.SimulationConflictException;

@Service
public class DroneSimulationService {
    private final DroneWorld world;
    private final RouteService routes;
    public DroneSimulationService(DroneWorld world, RouteService routes) { this.world = world; this.routes = routes; }

    public List<DroneStatus> list() { return world.snapshot().drones(); }
    public DroneStatus status(String id) { return world.drone(id); }

    public DroneStatus move(String id, String sectorId) {
        synchronized (world) { return fly(id, world.sector(sectorId).position()); }
    }

    public DroneStatus returnHome(String id) {
        synchronized (world) { return fly(id, Position.BASE); }
    }

    private DroneStatus fly(String id, Position destination) {
        var drone = world.drone(id);
        ensureOperable(drone);
        return travel(drone, destination, destination.equals(Position.BASE) ? DroneState.READY : DroneState.IDLE);
    }

    // MissionSimulationService holds the world lock and has checked the whole mission budget.
    DroneStatus flyForMission(String id, Position destination) {
        return travel(world.drone(id), destination, DroneState.IN_MISSION);
    }

    private DroneStatus travel(DroneStatus drone, Position destination, DroneState state) {
        int cost = routes.flightCost(drone.position().distanceTo(destination), world.weather());
        ensureBattery(drone, cost);
        var updated = drone.at(destination, drone.batteryPercent() - cost, state);
        world.update(updated);
        return updated;
    }

    public DroneStatus consumeBattery(String id, int amount) {
        synchronized (world) {
            if (amount < 0) throw new IllegalArgumentException("Battery consumption must not be negative");
            var drone = world.drone(id);
            ensureBattery(drone, amount);
            var updated = drone.at(drone.position(), drone.batteryPercent() - amount, drone.state());
            world.update(updated);
            return updated;
        }
    }

    void ensureOperable(DroneStatus drone) {
        if (drone.gps() == GpsStatus.LOST) throw new SimulationConflictException("GPS is lost");
        if (drone.motorWarning()) throw new SimulationConflictException("Motor warning prevents movement");
        if (!drone.communicationAvailable()) throw new SimulationConflictException("Communication is lost");
        if (drone.state() == DroneState.IN_MISSION) throw new SimulationConflictException("Drone is already in a mission");
    }

    void ensureBattery(DroneStatus drone, int cost) {
        if (drone.batteryPercent() < cost) throw new SimulationConflictException("Insufficient battery for the operation");
    }
}

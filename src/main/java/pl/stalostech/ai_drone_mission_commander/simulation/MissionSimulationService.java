package pl.stalostech.ai_drone_mission_commander.simulation;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.exception.SimulationConflictException;

@Service
public class MissionSimulationService {
    private final DroneWorld world;
    private final RouteService routes;
    private final DroneSimulationService drones;
    private final SectorService sectors;

    public MissionSimulationService(DroneWorld world, RouteService routes, DroneSimulationService drones, SectorService sectors) {
        this.world = world; this.routes = routes; this.drones = drones; this.sectors = sectors;
    }

    public Mission create(MissionIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        synchronized (world) {
            var route = routes.calculate(intent.droneId(), intent.targetSector(), intent.returnHome());
            return world.save(new Mission(world.missionId(), intent, MissionState.CREATED, route, null, null));
        }
    }

    public List<Mission> list() { return world.snapshot().missions(); }
    public Mission get(String id) { return world.mission(id); }

    public Mission execute(String id) {
        synchronized (world) {
            var mission = world.mission(id);
            if (mission.state() != MissionState.CREATED) {
                throw new SimulationConflictException("Only a newly created mission can be executed");
            }
            var intent = mission.intent();
            // Recalculate against current position and weather, not the creation-time quote.
            var route = routes.calculate(intent.droneId(), intent.targetSector(), intent.returnHome());
            var drone = world.drone(intent.droneId());
            int activityCost = intent.type() == MissionType.INSPECTION ? 3 : 2;
            try {
                drones.ensureOperable(drone);
                drones.ensureBattery(drone, Math.addExact(route.estimatedBatteryUsage(), activityCost));
            } catch (SimulationConflictException exception) {
                return world.save(new Mission(id, intent, MissionState.FAILED, route, null, exception.getMessage()));
            }
            world.save(new Mission(id, intent, MissionState.RUNNING, route, null, null));
            world.update(drone.at(drone.position(), drone.batteryPercent(), DroneState.IN_MISSION));
            drones.flyForMission(intent.droneId(), sectors.get(intent.targetSector()).position());
            drones.consumeBattery(intent.droneId(), activityCost);
            var inspection = intent.type() == MissionType.INSPECTION ? sectors.inspect(intent.targetSector()) : null;
            if (intent.returnHome()) drones.flyForMission(intent.droneId(), Position.BASE);
            var finished = world.drone(intent.droneId());
            world.update(finished.at(finished.position(), finished.batteryPercent(),
                    finished.position().equals(Position.BASE) ? DroneState.READY : DroneState.IDLE));
            return world.save(new Mission(id, intent, MissionState.COMPLETED, route, inspection, null));
        }
    }
}

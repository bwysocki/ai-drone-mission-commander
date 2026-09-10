package pl.stalostech.ai_drone_mission_commander.tools;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.stalostech.ai_drone_mission_commander.domain.DroneStatus;
import pl.stalostech.ai_drone_mission_commander.simulation.DroneSimulationService;

@Component
public class DroneTools {
    private final DroneSimulationService drones;
    public DroneTools(DroneSimulationService drones) { this.drones = drones; }

    @Tool(description = "Read current battery, position, activity state, GPS, motor and communication flags for one simulated drone. READY alone does not imply it can fly.")
    public DroneStatus getDroneStatus(@ToolParam(description = "Drone identifier, for example alpha") String droneId) {
        return drones.status(droneId);
    }

    @Tool(description = "Read the current status of every simulated drone. Use this to discover drone identifiers.")
    public List<DroneStatus> getFleetStatus() { return drones.list(); }
}

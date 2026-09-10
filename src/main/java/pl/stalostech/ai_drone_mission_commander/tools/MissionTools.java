package pl.stalostech.ai_drone_mission_commander.tools;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.*;

@Component
public class MissionTools {
    private final SectorService sectors;
    private final RouteService routes;
    private final MissionSimulationService missions;
    private final SimulationEventService events;

    public MissionTools(SectorService sectors, RouteService routes, MissionSimulationService missions, SimulationEventService events) {
        this.sectors = sectors; this.routes = routes; this.missions = missions; this.events = events;
    }

    @Tool(description = "Read a simulated sector's coordinates in kilometres and description. Known sectors are SECTOR_A, SECTOR_B and SECTOR_C; do not invent aliases.")
    public Sector getSector(@ToolParam(description = "Exact sector identifier, for example SECTOR_B") String sectorId) {
        return sectors.get(sectorId);
    }

    @Tool(description = "Quote a route from the drone's current position using current weather. estimatedBatteryUsage is movement only in percentage points; inspection adds 3 and patrol adds 2. This neither reserves battery nor approves or executes a mission.")
    public Route calculateRoute(
            @ToolParam(description = "Drone identifier") String droneId,
            @ToolParam(description = "Target sector identifier") String sectorId,
            @ToolParam(description = "Whether the quote includes a final leg to BASE") boolean returnHome) {
        return routes.calculate(droneId, sectorId, returnHome);
    }

    @Tool(description = "Read an existing simulated mission and its recorded state, route, findings or failure. Requires an ID actually returned by mission creation; never invent an ID.")
    public Mission getMission(@ToolParam(description = "Existing mission identifier") String missionId) {
        return missions.get(missionId);
    }

    @Tool(description = "Read recently injected simulation events, newest first. This is event history, not proof a fault is still active; consult current drone/weather status. Reset clears history.")
    public List<SimulationEvent> getRecentAlerts(@ToolParam(description = "Maximum number of events, from 1 to 20") int limit) {
        return events.recent(limit);
    }
}

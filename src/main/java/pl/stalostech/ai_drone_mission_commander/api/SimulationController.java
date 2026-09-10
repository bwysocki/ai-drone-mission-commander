package pl.stalostech.ai_drone_mission_commander.api;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import pl.stalostech.ai_drone_mission_commander.api.dto.SimulationEventRequest;
import pl.stalostech.ai_drone_mission_commander.domain.*;
import pl.stalostech.ai_drone_mission_commander.simulation.*;

@RestController
@RequestMapping(value = "/api/simulation", produces = "application/json")
@Tag(name = "Simulation", description = "Deterministic in-memory world; mutations affect simulated drones only")
public class SimulationController {
    private final DroneWorld world;
    private final DroneSimulationService drones;
    private final MissionSimulationService missions;
    private final RouteService routes;
    private final WeatherService weather;
    private final SectorService sectors;
    private final SimulationEventService events;

    public SimulationController(DroneWorld world, DroneSimulationService drones, MissionSimulationService missions,
                                RouteService routes, WeatherService weather, SectorService sectors, SimulationEventService events) {
        this.world = world; this.drones = drones; this.missions = missions; this.routes = routes;
        this.weather = weather; this.sectors = sectors; this.events = events;
    }

    @GetMapping("/world")
    public DroneWorldSnapshot world() { return world.snapshot(); }

    @PostMapping("/reset")
    @Operation(summary = "Reset the simulated world", description = "Restores initial drones and weather; clears missions and event history.")
    public DroneWorldSnapshot reset() { return world.reset(); }

    @GetMapping("/drones")
    public List<DroneStatus> drones() { return drones.list(); }

    @GetMapping("/drones/{id}")
    public DroneStatus drone(@PathVariable String id) { return drones.status(id); }

    @PostMapping("/drones/{id}/move")
    public DroneStatus move(@PathVariable String id, @RequestParam String sectorId) { return drones.move(id, sectorId); }

    @PostMapping("/drones/{id}/return-home")
    public DroneStatus returnHome(@PathVariable String id) { return drones.returnHome(id); }

    @GetMapping("/weather")
    public Weather weather() { return weather.current(); }

    @GetMapping("/sectors")
    public List<Sector> sectors() { return sectors.list(); }

    @GetMapping("/sectors/{id}")
    public Sector sector(@PathVariable String id) { return sectors.get(id); }

    @GetMapping("/routes")
    @Operation(summary = "Quote a route", description = "Movement battery cost only; inspection costs 3 extra points, patrol 2. Quote uses current weather and position.")
    public Route route(@RequestParam String droneId, @RequestParam String sectorId,
                       @RequestParam(defaultValue = "false") boolean returnHome) {
        return routes.calculate(droneId, sectorId, returnHome);
    }

    @PostMapping("/missions")
    @Operation(summary = "Create a simulated mission", description = "Queues a typed intent without moving a drone. Use SECTOR_A, SECTOR_B or SECTOR_C.")
    public Mission create(@RequestBody MissionIntent intent) { return missions.create(intent); }

    @GetMapping("/missions")
    public List<Mission> missions() { return missions.list(); }

    @GetMapping("/missions/{id}")
    public Mission mission(@PathVariable String id) { return missions.get(id); }

    @PostMapping("/missions/{id}/execute")
    @Operation(summary = "Execute a simulated mission", description = "Synchronous and atomic. Returns COMPLETED or FAILED; inspect state. Recalculates costs before execution. No AI call or real hardware operation.")
    public Mission execute(@PathVariable String id) { return missions.execute(id); }

    @PostMapping("/events")
    public SimulationEvent inject(@RequestBody SimulationEventRequest event) {
        return events.inject(event.type(), event.droneId(), event.amount());
    }
}

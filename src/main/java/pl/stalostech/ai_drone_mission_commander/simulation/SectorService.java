package pl.stalostech.ai_drone_mission_commander.simulation;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.domain.*;

@Service
public class SectorService {
    private final DroneWorld world;
    public SectorService(DroneWorld world) { this.world = world; }
    public List<Sector> list() { return world.snapshot().sectors(); }
    public Sector get(String id) { return world.sector(id); }

    public InspectionResult inspect(String id) {
        var sector = world.sector(id);
        boolean anomaly = sector.id().equals("SECTOR_C");
        return new InspectionResult(sector.id(), anomaly, List.of(anomaly
                ? "Simulated vehicle detected on the access road." : "No simulated anomalies detected."));
    }
}

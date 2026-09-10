package pl.stalostech.ai_drone_mission_commander.simulation;

import org.springframework.stereotype.Service;
import pl.stalostech.ai_drone_mission_commander.domain.Weather;

@Service
public class WeatherService {
    private final DroneWorld world;
    public WeatherService(DroneWorld world) { this.world = world; }
    public Weather current() { return world.weather(); }
}

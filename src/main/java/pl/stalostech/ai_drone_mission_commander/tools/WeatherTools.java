package pl.stalostech.ai_drone_mission_commander.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import pl.stalostech.ai_drone_mission_commander.domain.Weather;
import pl.stalostech.ai_drone_mission_commander.simulation.WeatherService;

@Component
public class WeatherTools {
    private final WeatherService weather;
    public WeatherTools(WeatherService weather) { this.weather = weather; }

    @Tool(description = "Read current simulated wind in km/h, rain and visibility. This is data, not mission approval.")
    public Weather getWeather() { return weather.current(); }
}

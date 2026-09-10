package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.Objects;

public record Weather(int windKmh, boolean rain, Visibility visibility) {
    public Weather {
        if (windKmh < 0 || windKmh > 200) {
            throw new IllegalArgumentException("Wind must be between 0 and 200 km/h");
        }
        Objects.requireNonNull(visibility);
    }
}

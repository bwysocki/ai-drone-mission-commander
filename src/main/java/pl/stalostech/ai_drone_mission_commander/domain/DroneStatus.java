package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.Objects;

public record DroneStatus(Drone drone, int batteryPercent, Position position, DroneState state,
                          GpsStatus gps, boolean motorWarning, boolean communicationAvailable) {
    public DroneStatus {
        Objects.requireNonNull(drone);
        Objects.requireNonNull(position);
        Objects.requireNonNull(state);
        Objects.requireNonNull(gps);
        if (batteryPercent < 0 || batteryPercent > 100) {
            throw new IllegalArgumentException("Battery must be between 0 and 100");
        }
    }

    public DroneStatus at(Position destination, int battery, DroneState nextState) {
        return new DroneStatus(drone, battery, destination, nextState, gps, motorWarning, communicationAvailable);
    }
}

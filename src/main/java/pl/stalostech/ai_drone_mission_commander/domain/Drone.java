package pl.stalostech.ai_drone_mission_commander.domain;

public record Drone(String id, String name) {
    public Drone {
        id = DomainValues.text(id, "id");
        name = DomainValues.text(name, "name");
    }
}

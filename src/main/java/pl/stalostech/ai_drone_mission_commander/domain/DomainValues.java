package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.List;

final class DomainValues {
    private DomainValues() { }

    static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    static List<String> texts(List<String> values, String field) {
        if (values == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return values.stream().map(value -> text(value, field)).toList();
    }
}

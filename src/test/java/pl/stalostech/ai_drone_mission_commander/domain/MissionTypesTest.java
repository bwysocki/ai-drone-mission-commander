package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MissionTypesTest {
    private final MissionIntent intent = new MissionIntent("alpha", MissionType.INSPECTION, "BRAVO", true);

    @Test
    void normalizesIdentifiersIndependentlyOfDefaultLocale() {
        var original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(new MissionIntent(" INDIA ", MissionType.PATROL, " india ", false))
                    .isEqualTo(new MissionIntent("india", MissionType.PATROL, "INDIA", false));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void snapshotsCollectionsAndRejectsInvalidDomainValues() {
        var values = new ArrayList<>(List.of(" Inspect sector "));
        var plan = new MissionPlan(intent, values);
        var assessment = new MissionAssessment(AssessmentStatus.NOT_EVALUATED, values);
        var report = new MissionReport(intent, MissionOutcome.NOT_EXECUTED, " Pending ", values);
        values.clear();
        assertThat(plan.steps()).containsExactly("Inspect sector");
        assertThat(assessment.reasons()).containsExactly("Inspect sector");
        assertThat(report.observations()).containsExactly("Inspect sector");
        assertThat(report.summary()).isEqualTo("Pending");
        assertThatThrownBy(() -> plan.steps().add("Launch")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new MissionPlan(intent, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissionPlan(intent, List.of(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissionAssessment(AssessmentStatus.REJECTED, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissionReport(intent, MissionOutcome.COMPLETED, " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

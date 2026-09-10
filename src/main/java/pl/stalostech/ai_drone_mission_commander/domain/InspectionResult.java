package pl.stalostech.ai_drone_mission_commander.domain;

import java.util.List;

public record InspectionResult(String sectorId, boolean anomaly, List<String> findings) {
    public InspectionResult {
        sectorId = DomainValues.text(sectorId, "sectorId");
        findings = DomainValues.texts(findings, "findings");
    }
}

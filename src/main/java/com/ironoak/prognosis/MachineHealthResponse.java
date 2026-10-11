package com.ironoak.prognosis;

import java.util.List;

/**
 * The model's assessment. Each output carries its probability, the tuned
 * threshold from the
 * model metadata, and whether it passed it. atRisk is true when any output is
 * flagged;
 * recommendedService is the service code to offer (a technician visit) or null
 * when LOW.
 */
public record MachineHealthResponse(
        boolean atRisk,
        RiskLevel riskLevel,
        Output machineFailure,
        List<Output> failureModes,
        String recommendedService,
        String modelVersion) {

    public enum RiskLevel {
        LOW, ELEVATED, HIGH
    }

    public record Output(String code,
            String name,
            double probability,
            double threshold,
            boolean flagged) {
    }
}

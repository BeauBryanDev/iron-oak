package com.ironoak.agent.tools;

import com.ironoak.prognosis.MachineHealthRequest;
import com.ironoak.prognosis.MachineHealthResponse;
import com.ironoak.prognosis.MachineHealthService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Piper's read-only CNC machine-health diagnosis tool. */
@Component
public class MachineHealthTools {

    private final MachineHealthService health;

    public MachineHealthTools(MachineHealthService health) {
        this.health = health;
    }

    @Tool(name = "diagnose_machine", description = "Estimate CNC machine-health risk from exactly six customer-provided inputs: air temperature in Kelvin, process temperature in Kelvin, rotational speed in RPM, torque in Nm, tool wear in minutes, and product quality variant L, M, or H. Do not call this tool until all six values are supplied; never guess or invent a missing measurement. The backend derives the model's engineered features, validates ranges, scales inputs, and runs the piper_machine_health_v1 MLP.\n\nInterpretation for the customer: every probability is a model estimate from 0.0 to 1.0 (multiply by 100 for a percentage), not a guarantee or physical measurement. Each output includes its tuned threshold and flagged=true when the probability reaches that threshold. Explain the overall riskLevel: LOW means no failure output crossed its threshold, ELEVATED means one failure mode crossed its threshold, and HIGH means the general machine-failure output or at least two failure modes crossed thresholds. Explain flagged failure modes by name: TWF is tool-wear failure, HDF is heat-dissipation failure, PWF is power failure, and OSF is overstrain failure. Mention machineFailure separately when flagged. If recommendedService is present, offer that service, but describe the diagnosis as a model-based estimate and recommend qualified technician inspection; do not claim that failure is certain.")
    public MachineHealthResponse diagnoseMachine(
            @ToolParam(description = "Air temperature in Kelvin. Required range: 250 to 350 K. Do not convert or guess a missing value.") Double air_temperature_k,
            @ToolParam(description = "Process temperature in Kelvin. Required range: 250 to 400 K. Do not convert or guess a missing value.") Double process_temperature_k,
            @ToolParam(description = "Rotational speed in revolutions per minute (RPM). Required range: 1 to 10000 RPM.") Double rotational_speed_rpm,
            @ToolParam(description = "Torque in Newton-metres (Nm). Required range: 0 to 500 Nm.") Double torque_nm,
            @ToolParam(description = "Tool wear in minutes. Required range: 0 to 1000 minutes.") Double tool_wear_min,
            @ToolParam(description = "Product quality variant used by the trained model. Required enum value: L, M, or H.") MachineHealthRequest.QualityVariant quality_variant) {

        if (air_temperature_k == null || process_temperature_k == null ||
                rotational_speed_rpm == null || torque_nm == null ||
                tool_wear_min == null || quality_variant == null) {

            throw new IllegalArgumentException("All six machine-health inputs are required");
        }

        return health.assess(new MachineHealthRequest(
                air_temperature_k,
                process_temperature_k,
                rotational_speed_rpm,
                torque_nm,
                tool_wear_min,
                quality_variant));
    }
}

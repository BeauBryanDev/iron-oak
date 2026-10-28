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

    @Tool(name = "diagnose_machine", description = "Estimate CNC machine-health risk with the piper_machine_health_v1 model from six customer readings: air and process temperature (K), speed (RPM), torque (Nm), tool wear (min) and quality variant L/M/H. Call only when all six are given; never guess one. Probabilities are 0-1 estimates, each with its threshold and flagged. riskLevel: LOW = nothing flagged, ELEVATED = one failure mode, HIGH = machine failure or two or more modes. Modes: TWF tool wear, HDF heat dissipation, PWF power, OSF overstrain. Offer recommendedService when present and describe the result as a model estimate that a technician should confirm.")
    public MachineHealthResponse diagnoseMachine(
            @ToolParam(description = "Air temperature, K, 250-350.") Double air_temperature_k,
            @ToolParam(description = "Process temperature, K, 250-400.") Double process_temperature_k,
            @ToolParam(description = "Rotational speed, RPM, 1-10000.") Double rotational_speed_rpm,
            @ToolParam(description = "Torque, Nm, 0-500.") Double torque_nm,
            @ToolParam(description = "Tool wear, minutes, 0-1000.") Double tool_wear_min,
            @ToolParam(description = "Quality variant: L, M or H.") MachineHealthRequest.QualityVariant quality_variant) {

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

package com.ironoak.prognosis;

import com.ironoak.prognosis.MachineHealthResponse.Output;
import com.ironoak.prognosis.MachineHealthResponse.RiskLevel;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the notebook's 11 features from one process reading, runs the
 * machine-health model
 * and turns the five probabilities into a risk level and the service to offer.
 * HIGH (machine failure flagged, or two or more failure modes) =>
 * EMERGENCY_REPAIR;
 * ELEVATED (one failure mode) => PREVENTIVE_MAINTENANCE; LOW => nothing.
 * 
 * No HTTP endpoint: Piper calls assess() in-process as an agent tool. The
 * request is validated
 * here (@Validated), so impossible readings fail with a
 * ConstraintViolationException.
 */
@Service
@Validated
public class MachineHealthService {

    static final String MACHINE_FAILURE = "Machine failure";

    /** Model label -> {code, display name}. */
    private static final Map<String, String[]> LABELS = Map.of(
            MACHINE_FAILURE, new String[] { "MACHINE_FAILURE", "Machine failure" },
            "TWF", new String[] { "TWF", "Tool wear failure" },
            "HDF", new String[] { "HDF", "Heat dissipation failure" },
            "PWF", new String[] { "PWF", "Power failure" },
            "OSF", new String[] { "OSF", "Overstrain failure" });

    public static final String SERVICE_HIGH = "EMERGENCY_REPAIR";
    public static final String SERVICE_ELEVATED = "PREVENTIVE_MAINTENANCE";

    private final MachineHealthModel model;

    public MachineHealthService(MachineHealthModel model) {
        this.model = model;
    }

    public MachineHealthResponse assess(@Valid MachineHealthRequest request) {

        float[] probabilities = model.predict(features(request));

        Output machineFailure = null;
        List<Output> modes = new ArrayList<>();

        for (int i = 0; i < probabilities.length; i++) {
            String label = model.labelOrder().get(i);
            String[] names = LABELS.getOrDefault(label, new String[] { label, label });
            double threshold = model.threshold(label);
            Output output = new Output(names[0], names[1],
                    probabilities[i],
                    threshold,
                    probabilities[i] >= threshold);

            if (MACHINE_FAILURE.equals(label)) {
                machineFailure = output;
            } else {
                modes.add(output);
            }
        }
        long flaggedModes = modes.stream().filter(Output::flagged).count();
        boolean failureFlagged = machineFailure != null && machineFailure.flagged();

        RiskLevel level = failureFlagged || flaggedModes >= 2 ? RiskLevel.HIGH
                : flaggedModes == 1 ? RiskLevel.ELEVATED
                        : RiskLevel.LOW;

        String service = switch (level) {
            case HIGH -> SERVICE_HIGH;
            case ELEVATED -> SERVICE_ELEVATED;
            case LOW -> null;
        };
        return new MachineHealthResponse(level != RiskLevel.LOW,
                level,
                machineFailure,
                modes,
                service,
                MachineHealthModel.MODEL_VERSION);
    }

    /**
     * The notebook's feature engineering (cells 40 and 42), keyed by its column
     * names.
     */
    static Map<String, Double> features(MachineHealthRequest r) {
        Map<String, Double> f = new HashMap<>();
        f.put("Air temperature", r.airTempK());
        f.put("Process temperature", r.processTempK());
        f.put("Rotational speed", r.rpm());
        f.put("Torque", r.torqueNm());
        f.put("Tool wear", r.toolWearMin());
        f.put("Type_H", r.qualityVariant() == MachineHealthRequest.QualityVariant.H ? 1.0 : 0.0);
        f.put("Type_L", r.qualityVariant() == MachineHealthRequest.QualityVariant.L ? 1.0 : 0.0);
        f.put("Type_M", r.qualityVariant() == MachineHealthRequest.QualityVariant.M ? 1.0 : 0.0);
        f.put("Power", r.torqueNm() * r.rpm() * (2 * Math.PI / 60));
        f.put("Torque_x_ToolWear", r.torqueNm() * r.toolWearMin());
        f.put("Temp_Diff", r.processTempK() - r.airTempK());

        return f;
    }
}

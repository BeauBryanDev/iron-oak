package com.ironoak.prognosis;

import ai.onnxruntime.OrtEnvironment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.prognosis.MachineHealthRequest.QualityVariant;
import com.ironoak.prognosis.MachineHealthResponse.Output;
import com.ironoak.prognosis.MachineHealthResponse.RiskLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Java inference must match the notebook pipeline. Expected probabilities were computed with
 * Python onnxruntime on the same ONNX file and scaler (order: Machine failure, TWF, HDF, PWF, OSF).
 */
class MachineHealthServiceTest {

    private static MachineHealthService service;

    @BeforeAll
    static void load() throws Exception {
        service = new MachineHealthService(new MachineHealthModel(OrtEnvironment.getEnvironment(), new ObjectMapper()));
    }

    private static MachineHealthResponse assess(QualityVariant type, double air, double process, double rpm,
                                                double torque, double wear) {
        return service.assess(new MachineHealthRequest(air, process, rpm, torque, wear, type));
    }

    private static void assertProbabilities(MachineHealthResponse r, double... expected) {
        assertThat(r.machineFailure().probability()).isCloseTo(expected[0], within(1e-4));
        for (int i = 0; i < 4; i++) {
            assertThat(r.failureModes().get(i).probability()).isCloseTo(expected[i + 1], within(1e-4));
        }
    }

    @Test
    void aNormalReadingIsLowRisk() {
        MachineHealthResponse r = assess(QualityVariant.M, 298.1, 308.6, 1551, 42.8, 0);
        assertProbabilities(r, 0.000835, 0.000111, 0.000001, 0.000208, 0.000009);
        assertThat(r.atRisk()).isFalse();
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(r.recommendedService()).isNull();
    }

    @Test
    void failingReadingsAreFlaggedAndSendATechnician() {
        MachineHealthResponse power = assess(QualityVariant.L, 298.9, 309.1, 2861, 4.6, 143);
        assertProbabilities(power, 0.999064, 0.018723, 0.000012, 0.999995, 0.001377);
        assertThat(power.failureModes()).filteredOn(Output::flagged).extracting(Output::code).containsExactly("PWF");

        MachineHealthResponse heat = assess(QualityVariant.L, 302.5, 310.5, 1300, 52.0, 50);
        assertProbabilities(heat, 0.990969, 0.000681, 0.999632, 0.008776, 0.059087);
        assertThat(heat.failureModes()).filteredOn(Output::flagged).extracting(Output::code).containsExactly("HDF");

        MachineHealthResponse overstrain = assess(QualityVariant.L, 300.0, 310.0, 1400, 60.0, 210);
        assertProbabilities(overstrain, 0.995216, 0.788041, 0.012796, 0.926929, 0.999317);
        assertThat(overstrain.failureModes()).filteredOn(Output::flagged).extracting(Output::code)
                .containsExactly("TWF", "OSF");

        for (MachineHealthResponse r : new MachineHealthResponse[] {power, heat, overstrain}) {
            assertThat(r.atRisk()).isTrue();
            assertThat(r.riskLevel()).isEqualTo(RiskLevel.HIGH); // machine failure is flagged too
            assertThat(r.recommendedService()).isEqualTo(MachineHealthService.SERVICE_HIGH);
        }
    }
}

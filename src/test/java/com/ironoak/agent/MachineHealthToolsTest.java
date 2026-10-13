package com.ironoak.agent;

import com.ironoak.agent.tools.MachineHealthTools;
import com.ironoak.prognosis.MachineHealthRequest.QualityVariant;
import com.ironoak.prognosis.MachineHealthResponse;
import com.ironoak.prognosis.MachineHealthResponse.RiskLevel;
import com.ironoak.prognosis.MachineHealthService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MachineHealthToolsTest {

    @Test
    void passesAllSixInputsToTheExistingMlpService() {
        MachineHealthService health = mock(MachineHealthService.class);
        MachineHealthTools tools = new MachineHealthTools(health);
        MachineHealthResponse response = new MachineHealthResponse(
                true, RiskLevel.HIGH, null, java.util.List.of(),
                MachineHealthService.SERVICE_HIGH, "piper_machine_health_v1");
        when(health.assess(any())).thenReturn(response);

        assertThat(tools.diagnoseMachine(298.1, 308.6, 1551.0, 42.8, 20.0, QualityVariant.M))
                .isSameAs(response);
        verify(health).assess(any());
    }

    @Test
    void refusesToRunWithMissingInputsInsteadOfInventingMeasurements() {
        MachineHealthTools tools = new MachineHealthTools(mock(MachineHealthService.class));

        assertThatThrownBy(() -> tools.diagnoseMachine(null, 308.6, 1551.0, 42.8, 20.0, QualityVariant.M))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("All six machine-health inputs are required");
    }
}

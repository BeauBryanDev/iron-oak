package com.ironoak.agent;

import com.ironoak.agent.tools.EndMillTools;
import com.ironoak.dto.response.EndMillResponse;
import com.ironoak.services.EndMillService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EndMillToolsTest {

    @Test
    void returnsActiveCatalogFieldsAndDelegatesFilters() {
        EndMillService service = mock(EndMillService.class);
        EndMillTools tools = new EndMillTools(service);
        EndMillResponse endMill = new EndMillResponse(1L, "BN-002", "Ball Nose 2mm",
                "Cutter", "Ball Nose", "RIGHT", new BigDecimal("2.0"),
                new BigDecimal("1.0"), "CARBIDE", new BigDecimal("50.0"),
                new BigDecimal("20.0"), new BigDecimal("37.00"), 18000, true, null);
        when(service.list(eq("Ball Nose"), eq(new BigDecimal("2.0")), eq("BN"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(endMill)));

        var result = tools.getEndMills("BN", "Ball Nose", new BigDecimal("2.0"), 10);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("BN-002");
            assertThat(item.cuttingDiameterMm()).isEqualByComparingTo("2.0");
            assertThat(item.price()).isEqualByComparingTo("37.00");
            assertThat(item.inStock()).isTrue();
        });
        verify(service).list(eq("Ball Nose"), eq(new BigDecimal("2.0")), eq("BN"), org.mockito.ArgumentMatchers.any());
    }
}

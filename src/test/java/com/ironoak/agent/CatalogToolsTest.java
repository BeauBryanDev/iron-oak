package com.ironoak.agent;

import com.ironoak.agent.tools.CatalogTools;
import com.ironoak.dto.response.MillingMachineResponse;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.dto.response.ServiceOfferingResponse;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.ProductService;
import com.ironoak.services.ServiceCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class CatalogToolsTest {

    private ProductService products;
    private ServiceCatalogService catalog;
    private CatalogTools tools;

    @BeforeEach
    void setUp() {
        products = mock(ProductService.class);
        catalog = mock(ServiceCatalogService.class);
        tools = new CatalogTools(products, catalog);
    }

    @Test
    void searchUsesDefaultLimitAndReturnsOnlyToolContractFields() {
        ProductResponse product = new ProductResponse(1L, "IO-HAM-001", "Hammer", "IronClad",
                "Hand Tools", "hammer", "A hammer", new BigDecimal("12.50"), 7, true, 12,
                null, "Hand Tools", 2L, null);
        when(products.search(null, null, null, 5)).thenReturn(List.of(product));

        var result = tools.searchProducts(null, null, null, null);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("IO-HAM-001");
            assertThat(item.name()).isEqualTo("Hammer");
            assertThat(item.price()).isEqualByComparingTo("12.50");
            assertThat(item.stockQuantity()).isEqualTo(7);
        });
        verify(products).search(null, null, null, 5);
    }

    @Test
    void searchRejectsLimitsOutsideTheContract() {
        assertThatThrownBy(() -> tools.searchProducts(null, null, null, 11))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("limit must be between 1 and 10");
    }

    @Test
    void detailsTrimSkuAndDelegateToActiveProductService() {
        ProductResponse product = new ProductResponse(1L, "IO-HAM-001", "Hammer", "IronClad",
                "Hand Tools", "hammer", "A hammer", new BigDecimal("12.50"), 7, true, 12,
                null, "Hand Tools", 2L, null);
        when(products.getBySku("IO-HAM-001")).thenReturn(product);

        var result = tools.getProductDetails(" IO-HAM-001 ");

        assertThat(result.description()).isEqualTo("A hammer");
        assertThat(result.warrantyMonths()).isEqualTo(12);
        assertThat(result.stockQuantity()).isEqualTo(7);
        verify(products).getBySku("IO-HAM-001");
    }

    @Test
    void machineAndServiceFiltersUseExistingCatalogLookups() {
        MillingMachineResponse machine = new MillingMachineResponse(1L, "BM-200", "Bench Mill",
                "Machine", new BigDecimal("2.0"), 1000, 5000, 600, 300,
                new BigDecimal("4250.00"), 24, null, null, null);
        ServiceOfferingResponse service = new ServiceOfferingResponse(1L, "REPAIR", "Repair",
                "Maintenance", "Repair service", PricingType.HOURLY, null,
                new BigDecimal("180.00"), new BigDecimal("1.0"), new BigDecimal("8.0"), "hour");
        when(catalog.getMachine("BM-200")).thenReturn(machine);
        when(catalog.getService("REPAIR")).thenReturn(service);

        assertThat(tools.listMachines(" BM-200 ")).singleElement()
                .satisfies(item -> assertThat(item.modelCode()).isEqualTo("BM-200"));
        assertThat(tools.listServices(" REPAIR ")).singleElement()
                .satisfies(item -> assertThat(item.hourlyRate()).isEqualByComparingTo("180.00"));
        verify(catalog).getMachine("BM-200");
        verify(catalog).getService("REPAIR");
    }
}

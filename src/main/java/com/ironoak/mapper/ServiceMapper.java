package com.ironoak.mapper;

import com.ironoak.domain.MillingMachine;
import com.ironoak.domain.ServiceOffering;
import com.ironoak.dto.response.MillingMachineResponse;
import com.ironoak.dto.response.ServiceOfferingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps the service-side catalog: service offerings and the milling machines they support. */
@Component
public class ServiceMapper {

    // Reads the lazy category, so call it inside a transaction.
    public ServiceOfferingResponse toResponse(ServiceOffering service) {
        return new ServiceOfferingResponse(
                service.getId(),
                service.getCode(),
                service.getName(),
                service.getCategory().getName(),
                service.getDescription(),
                service.getPricingType(),
                service.getFixedPrice(),
                service.getHourlyRate(),
                service.getEstimatedMinHours(),
                service.getEstimatedMaxHours(),
                service.getPriceUnit());
    }

    public List<ServiceOfferingResponse> toResponses(List<ServiceOffering> services) {
        return services.stream().map(this::toResponse).toList();
    }

    public MillingMachineResponse toResponse(MillingMachine machine) {
        return new MillingMachineResponse(
                machine.getId(),
                machine.getModelCode(),
                machine.getName(),
                machine.getDescription(),
                machine.getPowerKw(),
                machine.getSpindleMinRpm(),
                machine.getSpindleMaxRpm(),
                machine.getTableLengthMm(),
                machine.getTableWidthMm(),
                machine.getPrice(),
                machine.getWarrantyMonths(),
                machine.getImageUrl());
    }

    public List<MillingMachineResponse> toMachineResponses(List<MillingMachine> machines) {
        return machines.stream().map(this::toResponse).toList();
    }
}

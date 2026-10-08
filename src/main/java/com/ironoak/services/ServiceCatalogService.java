package com.ironoak.services;

import com.ironoak.dto.response.MillingMachineResponse;
import com.ironoak.dto.response.ServiceOfferingResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.ServiceMapper;
import com.ironoak.repository.MillingMachineRepository;
import com.ironoak.repository.ServiceOfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side of the service-and-machines catalog. */
@Service
@Transactional(readOnly = true)
public class ServiceCatalogService {

    private final ServiceOfferingRepository serviceOfferings;
    private final MillingMachineRepository machines;
    private final ServiceMapper mapper;

    public ServiceCatalogService(ServiceOfferingRepository serviceOfferings,
                                 MillingMachineRepository machines,
                                 ServiceMapper mapper) {
        this.serviceOfferings = serviceOfferings;
        this.machines = machines;
        this.mapper = mapper;
    }

    public List<ServiceOfferingResponse> listServices() {
        return mapper.toResponses(serviceOfferings.findByIsActiveTrueOrderByCategoryNameAscNameAsc());
    }

    public ServiceOfferingResponse getService(String code) {
        return serviceOfferings.findByCode(code)
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Service", code));
    }

    public List<MillingMachineResponse> listMachines() {
        return mapper.toMachineResponses(machines.findByIsActiveTrueOrderByPriceAsc());
    }

    public MillingMachineResponse getMachine(String modelCode) {
        return machines.findByModelCode(modelCode)
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Milling machine", modelCode));
    }
}

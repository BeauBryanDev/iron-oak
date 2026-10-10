package com.ironoak.services;

import com.ironoak.domain.MillingMachine;
import com.ironoak.dto.request.MillingMachineRequest;
import com.ironoak.dto.response.AdminMillingMachineResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.DuplicateResourceException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.AdminCatalogMapper;
import com.ironoak.repository.MillingMachineRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Staff catalog management for milling machines. Machines are deactivated, never deleted. */
@Service
@Transactional
public class AdminMachineService {

    private final MillingMachineRepository machines;
    private final AdminCatalogMapper mapper;

    public AdminMachineService(MillingMachineRepository machines, AdminCatalogMapper mapper) {
        this.machines = machines;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AdminMillingMachineResponse> list(String search, Boolean active) {
        Specification<MillingMachine> spec = Specification.allOf(
                FilterSpecs.equal("isActive", active),
                !FilterSpecs.hasText(search) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, search,
                                root.<String>get("name"), root.<String>get("modelCode")));
        return machines.findAll(spec, Sort.by("price")).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminMillingMachineResponse get(Long id) {
        return mapper.toResponse(find(id));
    }

    public AdminMillingMachineResponse create(MillingMachineRequest request) {
        String modelCode = request.modelCode().trim();
        if (machines.findByModelCode(modelCode).isPresent()) {
            throw new DuplicateResourceException("Milling machine", "modelCode", modelCode);
        }
        checkSpindleRange(request);
        MillingMachine machine = new MillingMachine(modelCode, request.name().trim(),
                AdminProductService.blankToNull(request.description()), request.powerKw(),
                request.spindleMinRpm(), request.spindleMaxRpm(), request.tableLengthMm(),
                request.tableWidthMm(), request.price(), request.warrantyMonths(),
                AdminProductService.blankToNull(request.imageUrl()));
        machine.setWeightKg(request.weightKg());
        machine.setVolumeM3(request.volumeM3());
        machine.setIsActive(request.isActive() == null || request.isActive());
        machines.saveAndFlush(machine);
        return mapper.toResponse(machine);
    }

    public AdminMillingMachineResponse update(Long id, MillingMachineRequest request) {
        MillingMachine machine = find(id);
        String modelCode = request.modelCode().trim();
        machines.findByModelCode(modelCode)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Milling machine", "modelCode", modelCode);
                });
        checkSpindleRange(request);
        machine.setModelCode(modelCode);
        machine.setName(request.name().trim());
        machine.setDescription(AdminProductService.blankToNull(request.description()));
        machine.setPowerKw(request.powerKw());
        machine.setSpindleMinRpm(request.spindleMinRpm());
        machine.setSpindleMaxRpm(request.spindleMaxRpm());
        machine.setTableLengthMm(request.tableLengthMm());
        machine.setTableWidthMm(request.tableWidthMm());
        machine.setPrice(request.price());
        machine.setWarrantyMonths(request.warrantyMonths());
        machine.setWeightKg(request.weightKg());
        machine.setVolumeM3(request.volumeM3());
        machine.setImageUrl(AdminProductService.blankToNull(request.imageUrl()));
        if (request.isActive() != null) {
            machine.setIsActive(request.isActive());
        }
        machines.saveAndFlush(machine);
        return mapper.toResponse(machine);
    }

    public AdminMillingMachineResponse setActive(Long id, boolean active) {
        MillingMachine machine = find(id);
        machine.setIsActive(active);
        machines.saveAndFlush(machine);
        return mapper.toResponse(machine);
    }

    private MillingMachine find(Long id) {
        return machines.findById(id).orElseThrow(() -> new ResourceNotFoundException("Milling machine", id));
    }

    private void checkSpindleRange(MillingMachineRequest request) {
        if (request.spindleMaxRpm() < request.spindleMinRpm()) {
            throw new BusinessRuleException("spindleMaxRpm must be at least spindleMinRpm");
        }
    }
}

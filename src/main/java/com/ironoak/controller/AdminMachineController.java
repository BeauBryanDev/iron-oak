package com.ironoak.controller;

import com.ironoak.dto.request.MillingMachineRequest;
import com.ironoak.dto.request.UpdateActiveRequest;
import com.ironoak.dto.response.AdminMillingMachineResponse;
import com.ironoak.services.AdminMachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Staff milling-machine management (ROLE_ADMIN via /api/admin/**). */
@RestController
@RequestMapping("/api/admin/machines")
public class AdminMachineController {

    private final AdminMachineService machines;

    public AdminMachineController(AdminMachineService machines) {
        this.machines = machines;
    }

    @GetMapping
    public List<AdminMillingMachineResponse> list() {
        return machines.list();
    }

    @GetMapping("/{id}")
    public AdminMillingMachineResponse get(@PathVariable Long id) {
        return machines.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminMillingMachineResponse create(@Valid @RequestBody MillingMachineRequest request) {
        return machines.create(request);
    }

    @PutMapping("/{id}")
    public AdminMillingMachineResponse update(@PathVariable Long id, @Valid @RequestBody MillingMachineRequest request) {
        return machines.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminMillingMachineResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
        return machines.setActive(id, request.isActive());
    }
}

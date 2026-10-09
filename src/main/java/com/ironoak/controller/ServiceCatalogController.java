package com.ironoak.controller;

import com.ironoak.dto.response.MillingMachineResponse;
import com.ironoak.dto.response.ServiceOfferingResponse;
import com.ironoak.services.ServiceCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public technical-service catalog and the milling machines it supports. Read-only. */
@RestController
@RequestMapping("/api/services")
public class ServiceCatalogController {

    private final ServiceCatalogService catalog;

    public ServiceCatalogController(ServiceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ServiceOfferingResponse> listServices() {
        return catalog.listServices();
    }

    @GetMapping("/{code}")
    public ServiceOfferingResponse getService(@PathVariable String code) {
        return catalog.getService(code);
    }

    @GetMapping("/machines")
    public List<MillingMachineResponse> listMachines() {
        return catalog.listMachines();
    }

    @GetMapping("/machines/{modelCode}")
    public MillingMachineResponse getMachine(@PathVariable String modelCode) {
        return catalog.getMachine(modelCode);
    }
}

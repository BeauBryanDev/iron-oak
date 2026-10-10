package com.ironoak.mapper;

import com.ironoak.domain.MillingMachine;
import com.ironoak.domain.Product;
import com.ironoak.domain.ServiceOffering;
import com.ironoak.domain.ServiceOfferingCategory;
import com.ironoak.dto.response.AdminMillingMachineResponse;
import com.ironoak.dto.response.AdminProductResponse;
import com.ironoak.dto.response.AdminServiceOfferingResponse;
import com.ironoak.dto.response.ServiceCategoryResponse;
import org.springframework.stereotype.Component;

/**
 * Staff-side catalog views. These read lazy relations, so call them inside a
 * transaction.
 */
@Component
public class AdminCatalogMapper {

    public AdminProductResponse toResponse(Product product) {

        return new AdminProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getWarrantyMonths(),
                product.getWeightKg(),
                product.getVolumeM3(),
                product.getImageUrl(),
                Boolean.TRUE.equals(product.getIsActive()),
                product.getToolCategory().getId(),
                product.getToolCategory().getDisplayName(),
                product.getVisionName(),
                product.getCreatedAt());
    }

    public AdminMillingMachineResponse toResponse(MillingMachine machine) {

        return new AdminMillingMachineResponse(
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
                machine.getWeightKg(),
                machine.getVolumeM3(),
                machine.getImageUrl(),
                Boolean.TRUE.equals(machine.getIsActive()));
    }

    public AdminServiceOfferingResponse toResponse(ServiceOffering service) {

        return new AdminServiceOfferingResponse(
                service.getId(),
                service.getCode(),
                service.getName(),
                service.getCategory().getId(),
                service.getCategory().getName(),
                service.getDescription(),
                service.getPricingType(),
                service.getFixedPrice(),
                service.getHourlyRate(),
                service.getEstimatedMinHours(),
                service.getEstimatedMaxHours(),
                service.getPriceUnit(),
                Boolean.TRUE.equals(service.getIsActive()));
    }

    public ServiceCategoryResponse toResponse(ServiceOfferingCategory category) {

        return new ServiceCategoryResponse(category.getId(),
                category.getName());
    }
}

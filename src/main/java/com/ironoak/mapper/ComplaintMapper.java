package com.ironoak.mapper;

import com.ironoak.domain.Complaint;
import com.ironoak.dto.response.ComplaintResponse;
import org.springframework.stereotype.Component;

@Component
public class ComplaintMapper {

    public ComplaintResponse toResponse(Complaint complaint) {
        return new ComplaintResponse(
                complaint.getId(),
                complaint.getCustomerName(),
                complaint.getComplaintDatetime(),
                complaint.getProduct(),
                complaint.getDescription(),
                complaint.getStatus(),
                complaint.getCreatedAt());
    }
}

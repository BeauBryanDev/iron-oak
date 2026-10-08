package com.ironoak.services;

import com.ironoak.domain.Complaint;
import com.ironoak.domain.enums.ComplaintStatus;
import com.ironoak.dto.request.ComplaintRequest;
import com.ironoak.dto.response.ComplaintResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.ComplaintMapper;
import com.ironoak.repository.ComplaintRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ComplaintService {

    private final ComplaintRepository complaints;
    private final ComplaintMapper mapper;

    public ComplaintService(ComplaintRepository complaints, ComplaintMapper mapper) {
        this.complaints = complaints;
        this.mapper = mapper;
    }

    public ComplaintResponse create(ComplaintRequest request) {
        Complaint complaint = new Complaint(request.customerName().trim(), request.complaintDatetime(),
                request.product().trim(), request.description().trim());
        return mapper.toResponse(complaints.save(complaint));
    }

    @Transactional(readOnly = true)
    public Page<ComplaintResponse> list(ComplaintStatus status, Pageable pageable) {
        Page<Complaint> page = status == null
                ? complaints.findAll(pageable)
                : complaints.findByStatus(status, pageable);
        return page.map(mapper::toResponse);
    }

    public ComplaintResponse updateStatus(Long id, ComplaintStatus status) {
        Complaint complaint = complaints.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", id));
        complaint.setStatus(status);
        return mapper.toResponse(complaint);
    }
}

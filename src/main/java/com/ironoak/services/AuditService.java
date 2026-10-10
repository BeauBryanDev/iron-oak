package com.ironoak.services;

import com.ironoak.domain.AdminAuditLog;
import com.ironoak.domain.AdminUser;
import com.ironoak.domain.enums.AuditAction;
import com.ironoak.dto.request.AuditFilter;
import com.ironoak.dto.response.AuditLogResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.repository.AdminAuditLogRepository;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.security.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The admin audit trail (admin_audit_log, V9).
 * <p>
 * record(...) is called by the admin services inside their own transaction, so
 * an entry exists
 * exactly when the change committed. It writes only while a staff member is
 * signed in: the same
 * methods called by the Stripe webhook or the expiry job leave no entry. Only
 * the fields that
 * changed are stored. recordAuth(...) commits on its own, so failed logins are
 * kept even though
 * the login itself fails.
 */
@Service
public class AuditService {

    private static final int MAX_USERNAME = 100;
    private static final int MAX_USER_AGENT = 500;

    private final AdminAuditLogRepository entries;
    private final AdminUserRepository adminUsers;
    private final ClientIpResolver clientIp;
    private final ObjectMapper json;

    public AuditService(AdminAuditLogRepository entries,
            AdminUserRepository adminUsers,
            ClientIpResolver clientIp,
            ObjectMapper json) {

        this.entries = entries;
        this.adminUsers = adminUsers;
        this.clientIp = clientIp;
        this.json = json;
    }

    /**
     * A change made by the signed-in admin. before is null on create, after is null
     * on delete;
     * both may be any object Jackson can write (response records, maps).
     */
    @Transactional
    public void record(AuditAction action,
            String resourceType,
            Object resourceId,
            Object before,
            Object after) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return; // a system action (webhook, scheduled job), not a staff one
        }
        String username = auth.getName();
        Long adminId = adminUsers.findByUsername(username).map(AdminUser::getId).orElse(null);
        HttpServletRequest request = currentRequest();

        JsonNode oldTree = before == null ? null : json.valueToTree(before);
        JsonNode newTree = after == null ? null : json.valueToTree(after);

        if (oldTree instanceof ObjectNode o && newTree instanceof ObjectNode n) {

            keepChangedFields(o, n);
        }
        entries.save(new AdminAuditLog(adminId,
                username,
                action,
                resourceType,
                resourceId == null ? null : String.valueOf(resourceId),
                write(oldTree), write(newTree),
                request == null ? null : clientIp.resolve(request),
                request == null ? null : truncate(request.getHeader("User-Agent"), MAX_USER_AGENT)));
    }

    /** A sign-in event; committed even when the surrounding login fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuth(AuditAction action,
            String username,
            String ip,
            String userAgent) {

        String name = username == null || username.isBlank() ? "-" : truncate(username.trim(), MAX_USERNAME);

        Long adminId = adminUsers.findByUsername(name).map(AdminUser::getId).orElse(null);

        entries.save(new AdminAuditLog(adminId,
                name, action,
                "ADMIN_USER",
                adminId == null ? null : adminId.toString(),
                null, null, ip,
                truncate(userAgent, MAX_USER_AGENT)));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(AuditFilter filter, Pageable pageable) {

        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessRuleException("from must not be after to");
        }
        Specification<AdminAuditLog> spec = Specification.allOf(
                FilterSpecs.in("action", filter.action()),
                FilterSpecs.hasText(filter.username()) ? FilterSpecs.equal("username",
                        filter.username().trim()) : null,
                FilterSpecs.hasText(filter.resourceType())
                        ? FilterSpecs.equal("resourceType",
                                filter.resourceType().trim().toUpperCase())
                        : null,
                FilterSpecs.hasText(filter.resourceId()) ? FilterSpecs.equal("resourceId",
                        filter.resourceId().trim())
                        : null,
                FilterSpecs.dateRange("createdAt", filter.from(), filter.to()),
                !FilterSpecs.hasText(filter.q()) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, filter.q(),
                                root.<String>get("username"), root.<String>get("resourceId"),
                                root.<String>get("ipAddress")));

        return entries.findAll(spec, pageable).map(this::toResponse);
    }

    private AuditLogResponse toResponse(AdminAuditLog e) {
        return new AuditLogResponse(e.getId(),
                e.getCreatedAt(),
                e.getAdminUserId(),
                e.getUsername(),
                e.getAction(),
                e.getResourceType(),
                e.getResourceId(),
                read(e.getOldValue()),
                read(e.getNewValue()),
                e.getIpAddress(),
                e.getUserAgent());
    }

    /**
     * Drops every field that is equal before and after, leaving just the change.
     */
    private static void keepChangedFields(ObjectNode before, ObjectNode after) {

        Set<String> names = new LinkedHashSet<>();
        before.fieldNames().forEachRemaining(names::add);
        after.fieldNames().forEachRemaining(names::add);

        for (String name : names) {

            if (before.path(name).equals(after.path(name))) {

                before.remove(name);
                after.remove(name);
            }
        }
    }

    private String write(JsonNode tree) {

        if (tree == null || (tree.isObject() && tree.isEmpty())) {
            return null;
        }
        try {
            return json.writeValueAsString(tree);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write audit value", e);
        }
    }

    private JsonNode read(String value) {

        if (value == null) {
            return null;
        }
        try {
            return json.readTree(value);

        } catch (JsonProcessingException e) {
            return json.getNodeFactory().textNode(value);
        }
    }

    private static HttpServletRequest currentRequest() {

        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private static String truncate(String value, int max) {

        if (value == null) {
            return null;
        }
        String printable = value.replaceAll("[^\\p{Print}]", "?");

        return printable.length() > max ? printable.substring(0, max) : printable;
    }
}

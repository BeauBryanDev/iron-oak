package com.ironoak.services;

import com.ironoak.exceptions.BusinessRuleException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;

/**
 * Building blocks for the staff list filters. Every helper returns null when
 * its filter
 * is absent, and Specification.allOf(...) skips nulls, so a service lists only
 * the filters
 * it supports. Date filters are whole days in UTC, both ends inclusive.
 */
final class FilterSpecs {

    private FilterSpecs() {
    }

    static <T> Specification<T> in(String field, Collection<?> values) {

        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get(field).in(values);
    }

    static <T> Specification<T> equal(String field, Object value) {

        return value == null ? null : (root, query, cb) -> cb.equal(root.get(field), value);
    }

    /** Matches field >= from 00:00 and field < (to + 1 day) 00:00, in UTC. */
    static <T> Specification<T> dateRange(String field,
            LocalDate from, LocalDate to) {

        if (from == null && to == null) {
            return null;
        }
        if (from != null && to != null && to.isBefore(from)) {

            throw new BusinessRuleException("'to' must not be before 'from'");
        }
        return (root, query, cb) -> {

            Path<OffsetDateTime> path = root.get(field);

            Predicate lower = from == null ? cb.conjunction()
                    : cb.greaterThanOrEqualTo(path, from.atStartOfDay().atOffset(ZoneOffset.UTC));

            Predicate upper = to == null ? cb.conjunction()
                    : cb.lessThan(path, to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));

            return cb.and(lower, upper);
        };
    }

    /**
     * Case-insensitive contains-match of the search text against any of the given
     * text paths.
     */
    static Predicate anyContains(jakarta.persistence.criteria.CriteriaBuilder cb, String search,
            Expression<String>... fields) {

        String pattern = "%" + search.trim().toLowerCase().replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";

        Predicate[] matches = new Predicate[fields.length];

        for (int i = 0; i < fields.length; i++) {

            matches[i] = cb.like(cb.lower(fields[i]), pattern, '\\');
        }
        return cb.or(matches);
    }

    static boolean hasText(String value) {

        return value != null && !value.isBlank();
    }
}

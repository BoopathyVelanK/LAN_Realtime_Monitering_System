package com.securesoc.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Slim pagination envelope used by every monitoring read endpoint
 * (MonitoringController's GET methods). Deliberately not just returning
 * Spring Data's Page<T> directly - Page's default JSON shape is verbose
 * (pageable, sort, first/last flags, etc.) and leaks Spring Data
 * internals into the API contract; this trims it to exactly what the
 * frontend needs.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}

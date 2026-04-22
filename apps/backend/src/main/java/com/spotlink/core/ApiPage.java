package com.spotlink.core;

import java.util.List;
import org.springframework.data.domain.Page;

public record ApiPage<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
    public static <T> ApiPage<T> from(Page<T> page) {
        return new ApiPage<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }

    public static <T> ApiPage<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new ApiPage<>(content, totalElements, totalPages, page, size);
    }
}

package tn.digitworks.naanayamthavanai.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** OpenAPI paged response: { content, meta }. */
public record PageResponse<T>(List<T> content, PageMeta meta) {

    public record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}

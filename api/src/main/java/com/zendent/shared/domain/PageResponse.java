package com.zendent.shared.domain;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * The envelope every paginated listing returns.
 *
 * <p>Spring's own {@code Page} serialises its internal shape, which is neither
 * stable across versions nor pleasant to consume. This is the API's contract,
 * owned here so that changing Spring cannot change the API.
 *
 * @param content the page's items
 * @param page zero-based index of this page
 * @param size the requested page size
 * @param totalElements how many items exist in total, not just on this page
 * @param totalPages how many pages that comes to
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public PageResponse {
		content = List.copyOf(content);
	}

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	/** Maps the items while carrying the pagination facts across unchanged. */
	public <R> PageResponse<R> map(java.util.function.Function<T, R> mapper) {
		return new PageResponse<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
	}

}

package dev.wuoj.common.web;

import java.util.List;
import java.util.function.Function;

/** The contract's paging shape ({@code ProblemPage}, {@code SubmissionPage}). */
public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> Page<T> of(List<T> items, PageRequest request, long totalItems) {
        int totalPages = (int) ((totalItems + request.size() - 1) / request.size());
        return new Page<>(List.copyOf(items), request.page(), request.size(), totalItems, totalPages);
    }

    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(items.stream().<R>map(mapper).toList(), page, size, totalItems, totalPages);
    }
}

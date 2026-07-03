package com.mgh.backend.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Getter
@Setter
@Builder
public class PageResponseDto<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;
    private boolean last;
    private boolean first;
    private int numberOfElements;
    private boolean empty;

    @JsonProperty("page")
    public int getPage() {
        return number;
    }

    @JsonProperty("hasNext")
    public boolean isHasNext() {
        return !last;
    }

    @JsonProperty("hasPrevious")
    public boolean isHasPrevious() {
        return !first;
    }

    public static <E, D> PageResponseDto<D> from(Page<E> page, Function<E, D> mapper) {
        return PageResponseDto.<D>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .size(page.getSize())
                .number(page.getNumber())
                .last(page.isLast())
                .first(page.isFirst())
                .numberOfElements(page.getNumberOfElements())
                .empty(page.isEmpty())
                .build();
    }
}
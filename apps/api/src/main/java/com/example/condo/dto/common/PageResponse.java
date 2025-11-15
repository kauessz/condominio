package com.example.condo.dto.common;

import java.util.List;

/**
 * DTO genérico para respostas paginadas.
 *
 * @param <T> Tipo do conteúdo da página
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {

    /**
     * Cria uma resposta de página vazia.
     */
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(
            List.of(),
            page,
            size,
            0L,
            0,
            true,
            true
        );
    }

    /**
     * Cria uma resposta de página a partir de dados.
     */
    public static <T> PageResponse<T> of(
        List<T> content,
        int page,
        int size,
        long totalElements
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
            content,
            page,
            size,
            totalElements,
            totalPages,
            page == 0,
            page >= totalPages - 1
        );
    }
}

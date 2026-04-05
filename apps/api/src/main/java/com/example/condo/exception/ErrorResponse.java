package com.example.condo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO padrão para respostas de erro da API.
 *
 * Estrutura consistente para todos os erros:
 * - timestamp: momento do erro
 * - status: código HTTP
 * - error: nome do erro
 * - message: mensagem principal
 * - path: endpoint que gerou o erro
 * - validationErrors: erros de validação de campos (opcional)
 * - details: informações adicionais (opcional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors,
    Object details
) {

    /**
     * Construtor simples para erros sem validação.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(
            Instant.now(),
            status,
            error,
            message,
            path,
            null,
            null
        );
    }

    /**
     * Construtor para erros de validação.
     */
    public static ErrorResponse withValidationErrors(
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors
    ) {
        return new ErrorResponse(
            Instant.now(),
            status,
            error,
            message,
            path,
            validationErrors,
            null
        );
    }

    /**
     * Construtor completo com detalhes adicionais.
     */
    public static ErrorResponse withDetails(
        int status,
        String error,
        String message,
        String path,
        Object details
    ) {
        return new ErrorResponse(
            Instant.now(),
            status,
            error,
            message,
            path,
            null,
            details
        );
    }
}

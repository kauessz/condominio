package com.example.condo.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Tratamento global de exceções para toda a API.
 *
 * Captura exceções e retorna respostas HTTP padronizadas usando ErrorResponse.
 * Registra logs apropriados para cada tipo de erro.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 404 - Recurso não encontrado
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
        ResourceNotFoundException ex,
        HttpServletRequest request
    ) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * 401 - Não autenticado
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
        UnauthorizedException ex,
        HttpServletRequest request
    ) {
        log.warn("Tentativa de acesso não autorizado: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * 403 - Acesso negado (tenant mismatch, falta de permissão)
     */
    @ExceptionHandler({TenantMismatchException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(
        Exception ex,
        HttpServletRequest request
    ) {
        log.warn("Acesso negado: {}", ex.getMessage());

        String message = ex instanceof TenantMismatchException
            ? ex.getMessage()
            : "Você não tem permissão para acessar este recurso";

        ErrorResponse error = ErrorResponse.of(
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            message,
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * 422 - Regra de negócio violada
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request
    ) {
        log.warn("Regra de negócio violada: {}", ex.getMessage());

        ErrorResponse error = ex.getErrorCode() != null
            ? ErrorResponse.withDetails(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Business Rule Violation",
                ex.getMessage(),
                request.getRequestURI(),
                Map.of("errorCode", ex.getErrorCode())
            )
            : ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Business Rule Violation",
                ex.getMessage(),
                request.getRequestURI()
            );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * 422 - Validação de Bean Validation (@Valid)
     *
     * Retorna 422 Unprocessable Entity (em vez de 400) para que o frontend
     * possa diferenciar erros de validação de campos de erros de protocolo HTTP.
     * A resposta inclui um mapa field→message para exibição contextual no formulário.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        log.warn("Erros de validação: {}", validationErrors);

        ErrorResponse error = ErrorResponse.withValidationErrors(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Validation Failed",
            "Um ou mais campos contêm erros de validação: " +
                validationErrors.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(java.util.stream.Collectors.joining(", ")),
            request.getRequestURI(),
            validationErrors
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * 422 - Violação de constraint (validação programática)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
        ConstraintViolationException ex,
        HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            validationErrors.put(propertyPath, message);
        }

        log.warn("Constraint violations: {}", validationErrors);

        ErrorResponse error = ErrorResponse.withValidationErrors(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Constraint Violation",
            "Um ou mais campos violam restrições de validação",
            request.getRequestURI(),
            validationErrors
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * 409 - Conflito de dados (duplicação, constraint única, etc.)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
        DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        log.error("Violação de integridade de dados", ex);

        HttpStatus status = HttpStatus.CONFLICT;
        String message = "Operação não pode ser completada devido a conflito de dados";
        Map<String, Object> details = null;

        // Tenta extrair mensagem mais amigável de constraints conhecidas
        String causeMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : null;
        String normalizedCause = causeMessage != null ? causeMessage.toLowerCase() : "";
        String normalizedMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (!normalizedCause.isBlank() || !normalizedMessage.isBlank()) {
            String combinedMessage = normalizedCause + " " + normalizedMessage;
            if (normalizedCause.contains("unique_email")) {
                message = "Já existe um usuário com este e-mail";
            } else if (normalizedCause.contains("unique_unit_number")) {
                message = "Já existe uma unidade com este número/bloco neste condomínio";
            } else if (combinedMessage.contains("audit_log") && combinedMessage.contains("value too long for type character varying(64)")) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                message = "A operação foi processada, mas houve uma falha ao registrar a auditoria. Tente novamente se necessário.";
                details = Map.of("resource", "audit_log");
            } else if (normalizedCause.contains("uq_invoice_unit_launch_key")
                || normalizedCause.contains("invoice_unit_launch_key")) {
                message = "Já existe uma cobrança igual para esta unidade. Recarregue a lista para ver o lançamento existente.";
                details = Map.of("constraint", "uq_invoice_unit_launch_key");
            } else if (normalizedCause.contains("uq_invoice_unit_month")
                || normalizedCause.contains("invoice_unit_month")) {
                message = "Já existe uma cobrança persistida para esta unidade na competência informada. Se isso não era esperado, alinhe o schema local do financeiro.";
                details = Map.of("constraint", "uq_invoice_unit_month");
            } else if (normalizedCause.contains("reservation_no_overlap")) {
                message = "Já existe uma reserva para esta área no período informado";
            } else if (normalizedCause.contains("foreign key constraint")) {
                message = "Não é possível deletar este recurso pois ele está sendo usado";
            }
        }

        ErrorResponse error = details == null
            ? ErrorResponse.of(
                status.value(),
                "Data Integrity Violation",
                message,
                request.getRequestURI()
            )
            : ErrorResponse.withDetails(
                status.value(),
                "Data Integrity Violation",
                message,
                request.getRequestURI(),
                details
            );

        return ResponseEntity.status(status).body(error);
    }

    /**
     * 400 - Argumento ilegal (ex: enum inválido, formato incorreto)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex,
        HttpServletRequest request
    ) {
        log.warn("Argumento ilegal: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Preserva o status de exceções lançadas explicitamente pela camada de serviço.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
        ResponseStatusException ex,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();

        log.warn("Erro HTTP controlado [{}]: {}", status.value(), reason);

        ErrorResponse error = ErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            reason,
            request.getRequestURI()
        );

        return ResponseEntity.status(status).body(error);
    }

    /**
     * 500 - Erro interno do servidor (catch-all)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("Erro não tratado: ", ex);

        ErrorResponse error = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Ocorreu um erro inesperado. Por favor, tente novamente mais tarde.",
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

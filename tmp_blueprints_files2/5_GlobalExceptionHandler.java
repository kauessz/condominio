package com.condohub.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GlobalExceptionHandler — trata todas as exceções dos controllers.
 *
 * Mapeamento:
 *   BusinessException            → 422 Unprocessable Entity
 *   ResourceNotFoundException    → 404 Not Found
 *   AccessDeniedException        → 403 Forbidden
 *   MethodArgumentNotValidException → 400 Bad Request (com lista de erros)
 *   Exception (catch-all)        → 500 Internal Server Error (sem stack trace)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Acesso negado.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Dados inválidos");
        body.put("messages", errors);
        return ResponseEntity.badRequest().body(body);
    }

    // ── catch-all: 500 sem stack trace para o cliente ────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // Log completo no servidor para diagnóstico
        log.error("Erro interno não tratado: {}", ex.getMessage(), ex);
        // Resposta genérica — sem expor stack trace ou detalhes internos
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno. Por favor, tente novamente.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// CHECKLIST — resolveCondominiumId nos services financeiros
//
// Verifique manualmente que cada método abaixo chama:
//   Long condominiumId = UserContext.resolveCondominiumId(currentUser);
// ANTES de qualquer chamada ao repository.
//
// FinancialService:
//   [ ] summary(currentUser)              → condoId do JWT, não do parâmetro
//   [ ] listInvoices(filters, currentUser) → condoId do JWT no filtro
//   [ ] getConfig(currentUser)            → condoId do JWT
//   [ ] saveConfig(request, currentUser)  → condoId do JWT, ignorar condoId do body
//   [ ] registerPayment(id, ...)          → findByIdAndCondominiumId usando condoId do JWT
//   [ ] createExternalCharge(id, ...)     → findByIdAndCondominiumId usando condoId do JWT
//
// AuditService:
//   [ ] listLogs(filters, currentUser)    → condoId do JWT no filtro (SINDICO só vê seu condo)
//
// PADRÃO CORRETO:
//
//   public SomeResponse someMethod(SomeRequest request, AppUserDetails currentUser) {
//       Long condominiumId = UserContext.resolveCondominiumId(currentUser); // ← SEMPRE PRIMEIRO
//       // ... lógica usando condominiumId resolvido do JWT
//   }
//
// PADRÃO ERRADO (nunca faça):
//
//   public SomeResponse someMethod(SomeRequest request, AppUserDetails currentUser) {
//       Long condominiumId = request.getCondominiumId(); // ← NUNCA — vem do body não confiável
//       // ... lógica
//   }
// ─────────────────────────────────────────────────────────────────────────────

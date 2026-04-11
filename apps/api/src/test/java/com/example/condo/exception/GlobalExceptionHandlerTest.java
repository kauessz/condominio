package com.example.condo.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnSpecificMessageForLegacyInvoiceMonthConstraint() {
        HttpServletRequest request = request("/api/financial/invoices/launch");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
            "duplicate",
            new SQLException("duplicate key value violates unique constraint \"uq_invoice_unit_month\"")
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
            "Já existe uma cobrança persistida para esta unidade na competência informada. Se isso não era esperado, alinhe o schema local do financeiro.",
            response.getBody().message()
        );
        assertInstanceOf(Map.class, response.getBody().details());
        assertEquals("uq_invoice_unit_month", ((Map<?, ?>) response.getBody().details()).get("constraint"));
    }

    @Test
    void shouldReturnSpecificMessageForLaunchKeyConstraint() {
        HttpServletRequest request = request("/api/financial/invoices/launch");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
            "duplicate",
            new PSQLException("duplicate key value violates unique constraint \"uq_invoice_unit_launch_key\"", null)
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
            "Já existe uma cobrança igual para esta unidade. Recarregue a lista para ver o lançamento existente.",
            response.getBody().message()
        );
        assertEquals("uq_invoice_unit_launch_key", ((Map<?, ?>) response.getBody().details()).get("constraint"));
    }

    @Test
    void shouldReturnInternalServerErrorWhenAuditLogFieldOverflows() {
        HttpServletRequest request = request("/api/financial/invoices/launch");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
            "could not execute statement [ERROR: value too long for type character varying(64)] [insert into audit_log (...)]",
            new SQLException("ERROR: value too long for type character varying(64)")
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(
            "A operação foi processada, mas houve uma falha ao registrar a auditoria. Tente novamente se necessário.",
            response.getBody().message()
        );
        assertEquals("audit_log", ((Map<?, ?>) response.getBody().details()).get("resource"));
    }

    private HttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}

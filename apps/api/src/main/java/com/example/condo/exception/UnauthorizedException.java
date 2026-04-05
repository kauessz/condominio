package com.example.condo.exception;

/**
 * Exceção para falhas de autenticação.
 * Mapeia para HTTP 401 Unauthorized.
 *
 * Exemplos:
 * - Credenciais inválidas (email/senha incorretos)
 * - Token JWT expirado ou inválido
 * - Refresh token inválido
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}

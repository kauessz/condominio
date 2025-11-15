package com.example.condo.exception;

/**
 * Exceção para regras de negócio violadas.
 * Mapeia para HTTP 422 Unprocessable Entity.
 *
 * Exemplos:
 * - "Não é possível deletar condomínio com unidades cadastradas"
 * - "Reserva conflita com horário já reservado"
 * - "Visitante já foi aprovado, não pode ser rejeitado"
 * - "Morador não pode aprovar sua própria visita"
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

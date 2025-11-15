package com.example.condo.exception;

/**
 * Exceção lançada quando há tentativa de acesso a dados de outro tenant.
 * Mapeia para HTTP 403 Forbidden.
 *
 * Exemplos:
 * - Usuário do tenant "demo" tentando acessar unidade do tenant "outro"
 * - Tenant do JWT não bate com tenant do header
 * - Recurso não pertence ao tenant autenticado
 */
public class TenantMismatchException extends RuntimeException {

    private final String expectedTenant;
    private final String actualTenant;

    public TenantMismatchException(String expectedTenant, String actualTenant) {
        super(String.format(
            "Acesso negado: recurso pertence ao tenant '%s', mas você está autenticado como '%s'",
            actualTenant,
            expectedTenant
        ));
        this.expectedTenant = expectedTenant;
        this.actualTenant = actualTenant;
    }

    public TenantMismatchException(String message) {
        super(message);
        this.expectedTenant = null;
        this.actualTenant = null;
    }

    public String getExpectedTenant() {
        return expectedTenant;
    }

    public String getActualTenant() {
        return actualTenant;
    }
}

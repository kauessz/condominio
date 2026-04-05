package com.example.condo.tenant;

/**
 * Context ThreadLocal que armazena dados do usuário autenticado extraídos do JWT.
 *
 * Populado pelo JwtAuthFilter a cada requisição.
 * Limpo pelo JwtAuthFilter no bloco finally.
 *
 * Uso nos Services:
 *   UserContext.Data ctx = UserContext.get();
 *   Long condoId = UserContext.resolveCondominiumId(condoIdFromRequest);
 */
public final class UserContext {

    private static final ThreadLocal<Data> CTX = new ThreadLocal<>();

    private UserContext() {}

    /**
     * Dados do usuário autenticado extraídos do JWT.
     *
     * @param role          Role principal do usuário (ex: "SUPERUSER", "PORTARIA", "MORADOR")
     * @param condominiumId ID do condomínio do usuário. Null para SUPERUSER.
     * @param unitId        ID da unidade do usuário. Não-null apenas para MORADOR.
     * @param userId        ID numérico do usuário no banco.
     */
    public record Data(String role, Long condominiumId, Long unitId, Long userId) {}

    public static void set(Data data) {
        CTX.set(data);
    }

    public static Data get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }

    /**
     * Retorna true se o usuário atual é SUPERUSER.
     */
    public static boolean isSuperuser() {
        Data d = CTX.get();
        return d != null && "SUPERUSER".equalsIgnoreCase(d.role());
    }

    /**
     * Resolve o condominiumId efetivo para a requisição atual.
     *
     * Regra central de isolamento de tenant:
     * - SUPERUSER: usa o valor fornecido no request (fromRequest).
     * - Todos os outros roles: ignora fromRequest e usa o condominiumId do JWT.
     *
     * @param fromRequest condominiumId vindo do query param ou path (pode ser null).
     * @return condominiumId efetivo ou null.
     */
    public static Long resolveCondominiumId(Long fromRequest) {
        if (isSuperuser()) {
            return fromRequest;
        }
        Data d = CTX.get();
        return d != null ? d.condominiumId() : null;
    }

    /**
     * Retorna o unitId do usuário autenticado (usado para MORADOR).
     * Não faz consulta ao banco — vem direto do JWT.
     */
    public static Long unitId() {
        Data d = CTX.get();
        return d != null ? d.unitId() : null;
    }

    /**
     * Retorna o userId do usuário autenticado.
     * Não faz consulta ao banco — vem direto do JWT.
     */
    public static Long userId() {
        Data d = CTX.get();
        return d != null ? d.userId() : null;
    }
}

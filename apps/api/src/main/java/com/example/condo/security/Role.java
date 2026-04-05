package com.example.condo.security;

/**
 * Roles do sistema CondoHub — Fase 2
 *
 * Hierarquia de permissões:
 * SUPERUSER > ADMIN > SINDICO > FINANCEIRO > OPERADOR > ZELADOR > PORTARIA > MORADOR
 */
public enum Role {

  /**
   * Super Administrador do SaaS
   * - Acesso total a TODOS os condomínios, sem filtro de tenant
   * - Pode criar/editar qualquer dado no sistema
   */
  SUPERUSER("Super Admin", 100),

  /**
   * Administrador / Administradora
   * - Gerencia um condomínio específico
   * - Pode criar/editar moradores, unidades e visitantes
   * - Acessa entregas mas não visitas pessoais de moradores
   */
  ADMIN("Administrador", 70),

  /**
   * Síndico
   * - Gestão do SEU condomínio (moradores, unidades, assembleias)
   * - DEVE ter unitId (também é morador)
   * - Acessa entregas mas não visitas pessoais de moradores
   */
  SINDICO("Síndico", 80),

  FINANCEIRO("Financeiro", 75),

  OPERADOR("Operador", 65),

  /**
   * Zelador
   * - Ordens de serviço + manutenção + reservas
   * - DEVE ter unitId (também é morador)
   * - Acessa entregas mas não visitas pessoais de moradores
   * - Sem acesso financeiro
   */
  ZELADOR("Zelador", 60),

  /**
   * Portaria / Segurança
   * - Registro de entrada/saída de visitantes (qualquer unidade do condomínio)
   * - Vê todas as visitas e entregas do condomínio
   * - Consulta lista de moradores (somente leitura)
   * - Não vê dados financeiros nem gerencia moradores/unidades
   */
  PORTARIA("Portaria", 40),

  /**
   * Morador
   * - Portal próprio: apenas sua unidade e seus visitantes pessoais
   * - Pode pré-registrar visitantes apenas para a própria unidade
   * - Pode aprovar ou negar visitantes da sua unidade
   * - Não acessa dados de outros moradores ou unidades
   */
  MORADOR("Morador", 20);

  private final String displayName;
  private final int priority;

  Role(String displayName, int priority) {
    this.displayName = displayName;
    this.priority = priority;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getPriority() {
    return priority;
  }

  public boolean hasPermission(Role required) {
    return this.priority >= required.priority;
  }

  public boolean isAdminOrAbove() {
    return this == SUPERUSER || this == ADMIN || this == SINDICO || this == FINANCEIRO;
  }

  public boolean canManageVisitors() {
    return this.priority >= PORTARIA.priority;
  }

  /** Retorna true para roles que veem apenas entregas (DELIVERY), não visitas pessoais */
  public boolean onlySeesDeliveries() {
    return this == ADMIN || this == SINDICO || this == ZELADOR;
  }
}

package com.example.condo.security;

/**
 * Roles do sistema CondoHub
 * 
 * Hierarquia:
 * SUPER_ADMIN > ADMIN > MANAGER > STAFF > RESIDENT > GUEST
 */
public enum Role {
  
  /**
   * Super Administrador
   * - Dono do SaaS, suporte técnico
   * - Acesso total a TODOS os condomínios
   * - Pode criar/editar qualquer dado
   */
  SUPER_ADMIN("Super Admin", 100),
  
  /**
   * Administrador do Condomínio
   * - Síndico, empresa administradora
   * - Acesso total ao SEU condomínio
   * - Gestão financeira, moradores, unidades
   */
  ADMIN("Síndico/Administradora", 80),
  
  /**
   * Gestor/Zelador
   * - Zelador, subsíndico, gerente predial
   * - Operações do dia-a-dia
   * - Pode aprovar reservas, alocar vagas, ver inadimplência
   * - NÃO pode criar/editar unidades ou gerar boletos
   */
  MANAGER("Zelador/Gestor", 60),
  
  /**
   * Portaria/Segurança
   * - Porteiro, segurança
   * - Registro de entrada/saída de visitantes
   * - Controle de acesso
   * - NÃO vê dados financeiros
   */
  STAFF("Portaria", 40),
  
  /**
   * Morador
   * - Morador comum
   * - Vê apenas dados da própria unidade
   * - Pode fazer reservas, pré-autorizar visitantes
   * - Vê próprios boletos
   */
  RESIDENT("Morador", 20),
  
  /**
   * Visitante/Convidado
   * - Familiar temporário, inquilino sem vínculo
   * - Acesso apenas para visualização
   */
  GUEST("Visitante", 10);
  
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
  
  /**
   * Verifica se esta role tem permissão maior ou igual à role fornecida
   */
  public boolean hasPermission(Role required) {
    return this.priority >= required.priority;
  }
  
  /**
   * Retorna true se é admin (ADMIN ou SUPER_ADMIN)
   */
  public boolean isAdmin() {
    return this == ADMIN || this == SUPER_ADMIN;
  }
  
  /**
   * Retorna true se pode gerenciar operações (MANAGER ou superior)
   */
  public boolean canManage() {
    return this.priority >= MANAGER.priority;
  }
  
  /**
   * Retorna true se pode acessar área financeira (ADMIN ou superior)
   */
  public boolean canAccessFinance() {
    return this.priority >= ADMIN.priority;
  }
}
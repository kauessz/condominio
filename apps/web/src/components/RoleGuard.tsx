import { type ReactNode } from "react";
import { useCurrentUser } from "../hooks/useCurrentUser";
import { getRoleLabel } from "../lib/auth";

export type UserRole =
  | "SUPERUSER"
  | "SINDICO"
  | "ADMIN"
  | "PORTARIA"
  | "MORADOR";

interface RoleGuardProps {
  roles: UserRole[];
  children: ReactNode;
  fallback?: ReactNode;
  showLoading?: boolean;
}

/**
 * Mostra/oculta elementos baseado na role do usuário autenticado.
 *
 * Exemplos:
 *   <RoleGuard roles={["SUPERUSER","SINDICO","ADMIN"]}>
 *     <button>Criar Morador</button>
 *   </RoleGuard>
 *
 *   <RoleGuard roles={["MORADOR"]} fallback={<span>Sem acesso</span>}>
 *     <MinhaUnidade />
 *   </RoleGuard>
 */
export default function RoleGuard({
  roles,
  children,
  fallback = null,
  showLoading = false,
}: RoleGuardProps) {
  const { user, loading } = useCurrentUser();

  if (loading) {
    return showLoading ? <span className="text-slate-400 text-sm">...</span> : null;
  }

  if (!user || !roles.includes(user.role as UserRole)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}

export const RoleUtils = {
  /** SUPERUSER, SINDICO ou ADMIN */
  isManager: (role?: string) =>
    role === "SUPERUSER" || role === "SINDICO" || role === "ADMIN",

  /** Pode gerenciar moradores e unidades (criar/editar/excluir) */
  canManageResidents: (role?: string) =>
    role === "SUPERUSER" || role === "SINDICO" || role === "ADMIN",

  /** Pode ler lista de moradores (inclui PORTARIA — somente leitura) */
  canReadResidents: (role?: string) =>
    role === "SUPERUSER" || role === "SINDICO" || role === "ADMIN" || role === "PORTARIA",

  /** Pode registrar e operar visitantes (aprovar, checkout) */
  canManageVisitors: (role?: string) =>
    role === "SUPERUSER" || role === "SINDICO" || role === "ADMIN" || role === "PORTARIA",

  /** Pode criar visitante (inclui MORADOR para sua própria unidade) */
  canCreateVisitor: (role?: string) =>
    role === "SUPERUSER" || role === "SINDICO" || role === "ADMIN" ||
    role === "PORTARIA" || role === "MORADOR",

  /** Apenas SUPERUSER acessa configurações globais */
  isSuperuser: (role?: string) => role === "SUPERUSER",

  /** Rótulo amigável */
  getLabel: getRoleLabel,
};

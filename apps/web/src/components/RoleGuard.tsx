import { type ReactNode } from "react";
import { useCurrentUser } from "../hooks/useCurrentUser";

// Roles disponíveis no sistema
export type UserRole = 
  | 'SUPER_ADMIN'  // Dono do SaaS
  | 'ADMIN'        // Síndico/Administradora
  | 'MANAGER'      // Zelador/Gestor
  | 'STAFF'        // Portaria
  | 'RESIDENT'     // Morador
  | 'GUEST';       // Visitante

interface RoleGuardProps {
  roles: UserRole[];
  children: ReactNode;
  fallback?: ReactNode;
  showLoading?: boolean;
}

/**
 * Componente para mostrar/ocultar elementos baseado em roles
 * 
 * Exemplos de uso:
 * 
 * 1. Botão apenas para síndico/admin:
 * <RoleGuard roles={['ADMIN', 'SUPER_ADMIN']}>
 *   <button>Gerar Boleto</button>
 * </RoleGuard>
 * 
 * 2. Botão para gestor e acima:
 * <RoleGuard roles={['ADMIN', 'SUPER_ADMIN', 'MANAGER']}>
 *   <button>Aprovar Reserva</button>
 * </RoleGuard>
 * 
 * 3. Botão para portaria e acima:
 * <RoleGuard roles={['ADMIN', 'SUPER_ADMIN', 'MANAGER', 'STAFF']}>
 *   <button>Registrar Entrada</button>
 * </RoleGuard>
 * 
 * 4. Área exclusiva do morador:
 * <RoleGuard roles={['RESIDENT']}>
 *   <MyUnitPanel />
 * </RoleGuard>
 */
export default function RoleGuard({
  roles,
  children,
  fallback = null,
  showLoading = false
}: RoleGuardProps) {
  const { user, loading } = useCurrentUser();

  if (loading) {
    return showLoading ? <span className="text-slate-400">...</span> : null;
  }

  if (!user || !roles.includes(user.role as UserRole)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}

/**
 * Helper functions para verificar permissões
 */
export const RoleUtils = {
  // Verifica se é admin (SUPER_ADMIN ou ADMIN)
  isAdmin: (role?: string) => {
    return role === 'SUPER_ADMIN' || role === 'ADMIN';
  },
  
  // Verifica se pode gerenciar (MANAGER ou superior)
  canManage: (role?: string) => {
    return role === 'SUPER_ADMIN' || role === 'ADMIN' || role === 'MANAGER';
  },
  
  // Verifica se pode acessar financeiro
  canAccessFinance: (role?: string) => {
    return role === 'SUPER_ADMIN' || role === 'ADMIN';
  },
  
  // Verifica se pode registrar visitantes
  canRegisterVisitors: (role?: string) => {
    return role === 'SUPER_ADMIN' || role === 'ADMIN' || role === 'MANAGER' || role === 'STAFF';
  },
  
  // Verifica se pode fazer reservas
  canMakeReservations: (role?: string) => {
    return role === 'SUPER_ADMIN' || role === 'ADMIN' || role === 'MANAGER' || role === 'RESIDENT';
  },
  
  // Verifica se pode aprovar reservas
  canApproveReservations: (role?: string) => {
    return role === 'SUPER_ADMIN' || role === 'ADMIN' || role === 'MANAGER';
  },
  
  // Nome amigável da role
  getRoleDisplayName: (role?: string): string => {
    const names: Record<string, string> = {
      'SUPER_ADMIN': 'Super Admin',
      'ADMIN': 'Síndico',
      'MANAGER': 'Zelador',
      'STAFF': 'Portaria',
      'RESIDENT': 'Morador',
      'GUEST': 'Visitante'
    };
    return role ? (names[role] || role) : 'Desconhecido';
  }
};
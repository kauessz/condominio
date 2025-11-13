import { type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useCurrentUser } from "../hooks/useCurrentUser";

interface ProtectedRouteProps {
  children: ReactNode;
  allowedRoles?: string[];
  redirectTo?: string;
}

/**
 * Componente para proteger rotas baseado em roles
 * 
 * Uso:
 * <ProtectedRoute allowedRoles={['ADMIN']}>
 *   <AdminPanel />
 * </ProtectedRoute>
 */
export default function ProtectedRoute({
  children,
  allowedRoles = [],
  redirectTo = "/app/dashboard"
}: ProtectedRouteProps) {
  const { user, loading } = useCurrentUser();

  // Enquanto carrega, mostra loading
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-slate-500">Carregando...</div>
      </div>
    );
  }

  // Se não tem usuário, redireciona para login
  if (!user) {
    return <Navigate to="/login" replace />;
  }

  // Se tem roles permitidas e o usuário não tem permissão, redireciona
  if (allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
    return <Navigate to={redirectTo} replace />;
  }

  // Usuário autenticado e autorizado
  return <>{children}</>;
}
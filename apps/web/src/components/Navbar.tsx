import { Link, NavLink, useNavigate } from "react-router-dom";
import { clearAuth, getRoleLabel } from "../lib/auth";
import { useCurrentUser } from "../hooks/useCurrentUser";

/**
 * Navbar do CondoHub.
 *
 * Visibilidade por role:
 *   SUPERUSER:  Dashboard | Solicitações | Usuários  (condomínios ficam no Dashboard)
 *   ADMIN:      Dashboard | Unidades | Moradores | Visitantes | Usuários
 *   SINDICO:    Dashboard | Moradores | Visitantes | Usuários
 *   ZELADOR:    Dashboard | Visitantes (entregas)
 *   PORTARIA:   Dashboard | Moradores | Visitantes
 *   MORADOR:    Dashboard | Visitantes (suas visitas)
 */
export default function Navbar() {
  const { user, loading } = useCurrentUser();
  const nav = useNavigate();

  function logout() {
    clearAuth();
    nav("/");
  }

  const role = user?.role;
  const isSuperuser = role === "SUPERUSER";
  const isAdmin     = role === "ADMIN";
  const isSindico   = role === "SINDICO";
  const isZelador   = role === "ZELADOR";
  const isPortaria  = role === "PORTARIA";
  const isMorador   = role === "MORADOR";

  // Acesso a Unidades: SUPERUSER via painel de condominios, ADMIN/SINDICO diretamente
  const canSeeUnits = isAdmin || isSindico;

  // Acesso a Moradores: ADMIN, SINDICO, ZELADOR, PORTARIA
  const canSeeResidents = isAdmin || isSindico || isZelador || isPortaria;

  // Acesso a Visitantes: todos exceto SUPERUSER (que gerencia via condomínios)
  const canSeeVisitors = isAdmin || isSindico || isZelador || isPortaria || isMorador;

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    "px-3 py-1.5 rounded-md transition-colors text-sm " +
    (isActive
      ? "bg-slate-100 text-slate-900 font-medium"
      : "text-slate-500 hover:text-slate-800 hover:bg-slate-50");

  return (
    <header className="bg-white border-b border-slate-100">
      <div className="max-w-6xl mx-auto px-6 h-14 flex items-center gap-6">
        <Link to="/app/dashboard" className="font-semibold text-slate-800 text-sm tracking-tight">
          CondoHub
        </Link>

        <nav className="flex gap-1 text-sm">
          <NavLink to="/app/dashboard" className={linkClass}>
            Dashboard
          </NavLink>

          {/* SUPERUSER: painel de solicitações de onboarding (condomínios no Dashboard) */}
          {isSuperuser && (
            <NavLink to="/app/onboarding" className={linkClass}>
              Solicitações
            </NavLink>
          )}

          {/* Unidades: ADMIN e SINDICO */}
          {canSeeUnits && (
            <NavLink to="/app/units" className={linkClass}>
              Unidades
            </NavLink>
          )}

          {/* Moradores: ADMIN, SINDICO, ZELADOR, PORTARIA */}
          {canSeeResidents && (
            <NavLink to="/app/residents" className={linkClass}>
              Moradores
            </NavLink>
          )}

          {/* Visitantes / Entregas: todos exceto SUPERUSER */}
          {canSeeVisitors && (
            <NavLink to="/app/visitors" className={linkClass}>
              {isMorador ? "Minhas Visitas" : isZelador ? "Entregas" : "Visitantes"}
            </NavLink>
          )}

          {/* Usuários: SUPERUSER (gerencia todos), ADMIN e SINDICO (gerenciam o próprio condo) */}
          {(isSuperuser || isAdmin || isSindico) && (
            <NavLink to="/app/users" className={linkClass}>
              Usuários
            </NavLink>
          )}
        </nav>

        <div className="ml-auto flex items-center gap-3 text-sm">
          {loading ? (
            <span className="text-slate-300 text-xs">...</span>
          ) : user ? (
            <>
              <span className="text-slate-400">
                {user.name || user.email}
                <span className="ml-1.5 px-1.5 py-0.5 rounded text-xs bg-slate-100 text-slate-600 font-medium">
                  {getRoleLabel(user.role)}
                </span>
              </span>
              <button
                onClick={logout}
                className="text-slate-400 hover:text-rose-600 transition-colors text-xs"
              >
                Sair
              </button>
            </>
          ) : (
            <Link to="/" className="text-slate-600 hover:text-slate-900 text-xs">
              Entrar
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}

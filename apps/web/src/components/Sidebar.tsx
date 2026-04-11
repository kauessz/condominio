import { NavLink, useNavigate } from "react-router-dom";
import { clearAuth, getRoleLabel } from "../lib/auth";
import { useCurrentUser } from "../hooks/useCurrentUser";

// ── Ícones inline ────────────────────────────────────────────────
const Icon = {
  dashboard: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
    </svg>
  ),
  units: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  ),
  residents: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  ),
  visitors: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
    </svg>
  ),
  users: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  ),
  onboarding: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
    </svg>
  ),
  reservations: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
    </svg>
  ),
  workorders: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  ),
  parking: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M5 10l7-7m0 0l7 7M5 10v10a1 1 0 001 1h3m4-11v11m0 0h3a1 1 0 001-1V10" />
    </svg>
  ),
  assemblies: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
    </svg>
  ),
  financial: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  ),
  invoices: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 14l2 2 4-4m5 1V7a2 2 0 00-2-2H6a2 2 0 00-2 2v10a2 2 0 002 2h8" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M16 3v4M8 3v4M4 11h16" />
    </svg>
  ),
  audit: (
    <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6M7 4h10a2 2 0 012 2v12a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z" />
    </svg>
  ),
  logout: (
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
    </svg>
  ),
};

const ROLE_BADGE: Record<string, string> = {
  SUPERUSER: "bg-purple-500/20 text-purple-300",
  ADMIN:     "bg-blue-500/20   text-blue-300",
  SINDICO:   "bg-emerald-500/20 text-emerald-300",
  FINANCEIRO:"bg-cyan-500/20 text-cyan-300",
  OPERADOR:  "bg-orange-500/20 text-orange-300",
  ZELADOR:   "bg-amber-500/20  text-amber-300",
  PORTARIA:  "bg-slate-500/20  text-slate-300",
  MORADOR:   "bg-rose-500/20   text-rose-300",
};

function SectionLabel({ label }: { label: string }) {
  return (
    <p className="text-xs font-medium text-slate-600 uppercase tracking-wider px-6 pt-3 pb-1">{label}</p>
  );
}

export default function Sidebar() {
  const { user } = useCurrentUser();
  const nav = useNavigate();

  function logout() {
    clearAuth();
    nav("/");
  }

  const role = user?.role ?? "";
  const isSuperuser = role === "SUPERUSER";
  const isAdmin     = role === "ADMIN";
  const isSindico   = role === "SINDICO";
  const isFinanceiro = role === "FINANCEIRO";
  const isOperador   = role === "OPERADOR";
  const isZelador   = role === "ZELADOR";
  const isPortaria  = role === "PORTARIA";
  const isMorador   = role === "MORADOR";

  const canSeeUnits     = isSuperuser || isAdmin || isSindico || isPortaria;
  const canSeeResidents = isSuperuser || isAdmin || isSindico || isOperador || isZelador || isPortaria || isMorador;
  const canSeeVisitors  = isSuperuser || isAdmin || isSindico || isOperador || isZelador || isPortaria || isMorador;
  const canSeeUsers     = isSuperuser || isAdmin || isSindico;
  const canSeeAudit     = isSuperuser || isAdmin || isSindico;

  // Fase 2
  const canSeeReservations = true; // todos os roles
  const canSeeWorkOrders   = true;
  const canSeeParking      = true;
  const canSeeAssemblies   = isSuperuser || isAdmin || isSindico || isMorador;
  const canSeeFinancial    = isSuperuser || isAdmin || isSindico || isFinanceiro;
  const canSeeMyInvoices   = isMorador || isSindico || isZelador;

  const linkCls = ({ isActive }: { isActive: boolean }) =>
    `sidebar-link${isActive ? " active" : ""}`;

  const initials = user?.name
    ? user.name.split(" ").slice(0, 2).map((w) => w[0]).join("").toUpperCase()
    : user?.email?.[0]?.toUpperCase() ?? "?";

  return (
    <aside className="sidebar">
      {/* Logo */}
      <div className="px-5 py-5 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center flex-shrink-0">
            <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round"
                d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
          </div>
          <span className="text-white font-semibold text-sm" style={{ fontFamily: "var(--font-display)" }}>
            CondoHub
          </span>
        </div>
      </div>

      {/* Navegação */}
      <nav className="flex-1 py-3 overflow-y-auto">
        <SectionLabel label="Principal" />

        <NavLink to="/app/dashboard" className={linkCls}>
          {Icon.dashboard}
          Dashboard
        </NavLink>

        {isSuperuser && (
          <NavLink to="/app/onboarding" className={linkCls}>
            {Icon.onboarding}
            Solicitações
          </NavLink>
        )}

        {canSeeUnits && (
          <NavLink to="/app/units" className={linkCls}>
            {Icon.units}
            Unidades
          </NavLink>
        )}

        {canSeeResidents && (
          <NavLink to="/app/residents" className={linkCls}>
            {Icon.residents}
            Moradores
          </NavLink>
        )}

        {canSeeVisitors && (
          <NavLink to="/app/visitors" className={linkCls}>
            {Icon.visitors}
            {isMorador ? "Minhas Visitas" : isZelador ? "Entregas" : "Visitantes"}
          </NavLink>
        )}

        {canSeeUsers && (
          <NavLink to="/app/users" className={linkCls}>
            {Icon.users}
            Usuários
          </NavLink>
        )}

        {/* Fase 2 */}
        <SectionLabel label="Serviços" />

        {canSeeReservations && (
          <NavLink to="/app/reservations" className={linkCls}>
            {Icon.reservations}
            Reservas
          </NavLink>
        )}

        {canSeeWorkOrders && (
          <NavLink to="/app/work-orders" className={linkCls}>
            {Icon.workorders}
            Ordens de Serviço
          </NavLink>
        )}

        {canSeeParking && (
          <NavLink to="/app/parking" className={linkCls}>
            {Icon.parking}
            Vagas
          </NavLink>
        )}

        {canSeeAssemblies && (
          <NavLink to="/app/assemblies" className={linkCls}>
            {Icon.assemblies}
            Assembleias
          </NavLink>
        )}

        {canSeeFinancial && (
          <NavLink to="/app/financial" className={linkCls}>
            {Icon.financial}
            Financeiro
          </NavLink>
        )}

        {canSeeMyInvoices && (
          <NavLink to="/app/my-invoices" className={linkCls}>
            {Icon.invoices}
            Minhas Faturas
          </NavLink>
        )}

        {canSeeAudit && (
          <NavLink to="/app/audit" className={linkCls}>
            {Icon.audit}
            Auditoria
          </NavLink>
        )}
      </nav>

      {/* Rodapé */}
      <div className="border-t border-slate-800 p-4">
        <div className="flex items-center gap-3 mb-3">
          <div className="w-8 h-8 bg-indigo-600 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
            {initials}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-slate-200 text-xs font-medium truncate">
              {user?.name || user?.email || "Usuário"}
            </p>
            <span className={`inline-block text-xs px-1.5 py-0.5 rounded-full font-medium mt-0.5 ${ROLE_BADGE[role] ?? "bg-slate-700 text-slate-300"}`}>
              {getRoleLabel(role)}
            </span>
          </div>
        </div>
        <button
          onClick={logout}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-slate-400 hover:bg-slate-800 hover:text-slate-200 transition-colors text-xs font-medium"
        >
          {Icon.logout}
          Sair
        </button>
      </div>
    </aside>
  );
}

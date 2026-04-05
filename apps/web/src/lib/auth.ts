// apps/web/src/lib/auth.ts

/**
 * Roles do sistema CondoHub — sincronizadas com o backend (Role.java)
 *
 * Hierarquia operacional: SUPERUSER > SINDICO > ADMIN > FINANCEIRO > OPERADOR > ZELADOR > PORTARIA > MORADOR
 * VISITANTE não tem conta no sistema — apenas registro de entrada/saída.
 */
export type Role =
  | "SUPERUSER"   // Super Admin: acesso total a todos os condomínios
  | "ADMIN"       // Administrador: gerencia um condomínio específico
  | "SINDICO"     // Síndico: gestão do seu condomínio, deve ter unitId
  | "FINANCEIRO"  // Financeiro: cobranças, pagamentos e configuração financeira
  | "OPERADOR"    // Operador: apoio administrativo/operacional
  | "ZELADOR"     // Zelador: OS + manutenção + reservas, deve ter unitId
  | "PORTARIA"    // Portaria: controle de acesso + entregas
  | "MORADOR";    // Morador: sua unidade e suas visitas pessoais

export type User = {
  id: string;
  name: string;
  email: string;
  role: Role;
  unitId?: string | number | null;
  condominiumId?: string | number | null;
};

const LS_TOKEN   = "condo:token";
const LS_USER    = "condo:user";
const LS_TENANT  = "condo:tenant";
const LS_REFRESH = "condo:refresh";

let _user: User | null = null;

function readAnyToken(): string {
  return (
    localStorage.getItem(LS_TOKEN) ||
    localStorage.getItem("token") ||
    localStorage.getItem("accessToken") ||
    ""
  );
}

export function loadAuthFromStorage() {
  const token = readAnyToken();
  const raw = localStorage.getItem(LS_USER);
  const tenant = localStorage.getItem(LS_TENANT) || "demo";
  const refreshToken = localStorage.getItem(LS_REFRESH) || "";
  _user = raw ? (JSON.parse(raw) as User) : null;
  return { token, user: _user, tenant, refreshToken };
}

export function saveAuth(token: string, user: User, tenant: string = "demo", refresh?: string) {
  if (!token) return;
  localStorage.setItem(LS_TOKEN, token);
  localStorage.setItem("token", token);
  localStorage.setItem("accessToken", token);
  if (refresh) localStorage.setItem(LS_REFRESH, refresh);
  localStorage.setItem(LS_USER, JSON.stringify(user));
  localStorage.setItem(LS_TENANT, tenant);
  _user = user;
}

export function clearAuth() {
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_USER);
  localStorage.removeItem(LS_TENANT);
  localStorage.removeItem("token");
  localStorage.removeItem("accessToken");
  localStorage.removeItem(LS_REFRESH);
  _user = null;
}

export function getToken() { return readAnyToken(); }

export function getRefreshToken() {
  return localStorage.getItem(LS_REFRESH) || "";
}

export function getTenant() {
  return localStorage.getItem(LS_TENANT) || "demo";
}

export function getUser() {
  if (_user) return _user;
  const raw = localStorage.getItem(LS_USER);
  _user = raw ? (JSON.parse(raw) as User) : null;
  return _user;
}

export function setUser(u: User | null) {
  _user = u;
  if (u) localStorage.setItem(LS_USER, JSON.stringify(u));
  else localStorage.removeItem(LS_USER);
}

export function hasToken(): boolean {
  const t = getToken();
  return typeof t === "string" && t.length > 0;
}

export function hasTenant(): boolean {
  const t = getTenant();
  return typeof t === "string" && t.length > 0;
}

export type AppModule =
  | "dashboard"
  | "units"
  | "residents"
  | "visitors"
  | "reservations"
  | "workOrders"
  | "parking"
  | "assemblies"
  | "financial"
  | "users"
  | "onboarding";

export function hasAnyRole(...roles: Role[]) {
  const u = getUser();
  return !!u && roles.includes(u.role);
}

/** Verifica permissão de escrita baseada na role */
export function can(action: "create" | "edit" | "delete") {
  const u = getUser();
  if (!u) return false;
  if (u.role === "SUPERUSER" || u.role === "SINDICO" || u.role === "ADMIN") return true;
  if (u.role === "FINANCEIRO") return action === "edit";
  if (u.role === "OPERADOR") return action === "edit";
  // PORTARIA pode editar (ex: checkout, atualização de visita) mas não deletar nem criar moradores
  if (u.role === "PORTARIA") return action === "edit";
  // ZELADOR pode editar (OS, reservas) mas não criar moradores nem deletar
  if (u.role === "ZELADOR") return action === "edit";
  return false;
}

/** Retorna true se o usuário tem role de gestor (SUPERUSER, ADMIN ou SINDICO) */
export function isManager(): boolean {
  const u = getUser();
  if (!u) return false;
  return u.role === "SUPERUSER" || u.role === "ADMIN" || u.role === "SINDICO" || u.role === "FINANCEIRO";
}

export function canAccessModule(module: AppModule) {
  const u = getUser();
  if (!u) return false;

  switch (module) {
    case "dashboard":
      return true;
    case "units":
      return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "ZELADOR", "PORTARIA");
    case "residents":
      return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "OPERADOR", "ZELADOR", "PORTARIA", "MORADOR");
    case "visitors":
      return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "OPERADOR", "ZELADOR", "PORTARIA", "MORADOR");
    case "reservations":
      return true;
    case "workOrders":
      return true;
    case "parking":
      return true;
    case "assemblies":
      return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "MORADOR");
    case "financial":
      return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO", "MORADOR");
    case "users":
      return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO");
    case "onboarding":
      return hasAnyRole("SUPERUSER");
  }
}

export function canManageUnits() {
  return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO");
}

export function canViewUnits() {
  return canAccessModule("units");
}

export function canCreateResidents() {
  return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "MORADOR");
}

export function canEditResidents() {
  return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "MORADOR");
}

export function canDeleteResidents() {
  return hasAnyRole("SUPERUSER", "ADMIN", "SINDICO");
}

/** Retorna true se o usuário pode gerenciar visitantes */
export function canManageVisitors(): boolean {
  const u = getUser();
  if (!u) return false;
  return u.role === "SUPERUSER" || u.role === "ADMIN" || u.role === "SINDICO"
    || u.role === "PORTARIA" || u.role === "MORADOR";
}

/** Label amigável para exibição na UI */
export function getRoleLabel(role?: string): string {
  const labels: Record<string, string> = {
    SUPERUSER: "Super Admin",
    ADMIN:     "Administrador",
    SINDICO:   "Síndico",
    FINANCEIRO:"Financeiro",
    OPERADOR:  "Operador",
    ZELADOR:   "Zelador",
    PORTARIA:  "Portaria",
    MORADOR:   "Morador",
  };
  return role ? (labels[role] ?? role) : "—";
}

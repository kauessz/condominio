export type Role = "ADMIN" | "MANAGER" | "RESIDENT";
export type User = { id: string; name: string; email: string; role: Role };

const LS_TOKEN = "condo:token";
const LS_USER = "condo:user";
const LS_TENANT = "condo:tenant";

let _user: User | null = null;

export function loadAuthFromStorage() {
  const token = localStorage.getItem(LS_TOKEN) || "";
  const raw = localStorage.getItem(LS_USER);
  const tenant = localStorage.getItem(LS_TENANT) || "demo";
  _user = raw ? (JSON.parse(raw) as User) : null;
  return { token, user: _user, tenant };
}

export function saveAuth(token: string, user: User, tenant: string = "demo") {
  localStorage.setItem(LS_TOKEN, token);
  localStorage.setItem(LS_USER, JSON.stringify(user));
  localStorage.setItem(LS_TENANT, tenant);
  _user = user;
}

export function clearAuth() {
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_USER);
  localStorage.removeItem(LS_TENANT);
  _user = null;
}

export function getToken() {
  return localStorage.getItem(LS_TOKEN) || "";
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

export function can(action: "create" | "edit" | "delete") {
  const u = getUser();
  if (!u) return false;
  if (u.role === "ADMIN") return true;
  if (u.role === "MANAGER") return action !== "delete";
  return false;
}
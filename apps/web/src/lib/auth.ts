// src/lib/auth.ts
export type Role = "ADMIN" | "MANAGER" | "RESIDENT";
export type User = { id: string; name: string; email: string; role: Role };

const LS_TOKEN = "condo:token";
const LS_USER  = "condo:user";

let _user: User | null = null;

export function loadAuthFromStorage() {
  const token = localStorage.getItem(LS_TOKEN) || "";
  const raw = localStorage.getItem(LS_USER);
  _user = raw ? (JSON.parse(raw) as User) : null;
  return { token, user: _user };
}

export function saveAuth(token: string, user: User) {
  localStorage.setItem("condo:token", token);
  localStorage.setItem("condo:user", JSON.stringify(user));
  localStorage.setItem("condo:tenant", "demo"); // ou user.tenantId se vier do /auth/me
  _user = user;
}

export function clearAuth() {
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_USER);
  _user = null;
}

export function getUser() { return _user; }
export function setUser(u: User | null) { _user = u; if (u) localStorage.setItem(LS_USER, JSON.stringify(u)); }

export function can(action: "create" | "edit" | "delete") {
  if (!_user) return false;
  if (_user.role === "ADMIN") return true;
  if (_user.role === "MANAGER") return action !== "delete";
  return false;
}
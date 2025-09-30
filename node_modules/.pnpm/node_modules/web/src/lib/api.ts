// web/src/lib/api.ts
import axios from "axios";
import { clearAuth, loadAuthFromStorage } from "./auth";

// Em dev usamos '/api' com o proxy do Vite.
// Em produção, defina VITE_API_URL (ex.: 'https://minhaapi.com/api').
const API_BASE =
  (import.meta.env.VITE_API_URL as string) ||
  (import.meta.env.NEXT_PUBLIC_API_URL as string) ||
  "/api";

export const api = axios.create({ baseURL: API_BASE });

// Header default do tenant (fallback)
(api.defaults.headers.common as any)["X-Tenant"] = "demo";

export function setToken(token?: string) {
  if (token) {
    (api.defaults.headers.common as any)["Authorization"] = `Bearer ${token}`;
  } else {
    delete (api.defaults.headers.common as any)["Authorization"];
  }
}

console.log("[API] baseURL =", API_BASE);

// Hidrata token salvo ao carregar o app
const { token } = loadAuthFromStorage();
if (token) setToken(token);

// Injeta token e cabeçalhos úteis em toda request
api.interceptors.request.use((config) => {
  const t = localStorage.getItem("token");
  if (t) config.headers.Authorization = `Bearer ${t}`;
  config.headers["Cache-Control"] = "no-store";
  config.headers["Pragma"] = "no-cache";
  if (!config.headers["X-Tenant"]) config.headers["X-Tenant"] = "demo";
  return config;
});

// Redireciona para login em 401 (evita loop)
api.interceptors.response.use(
  (r) => r,
  (err) => {
    const status = err?.response?.status;
    const url = err?.config?.url ?? "";
    if (status === 401 && !url.includes("/auth/login")) {
      clearAuth();
      setToken(undefined);
      if (location.pathname !== "/") location.replace("/");
    }
    return Promise.reject(err);
  }
);
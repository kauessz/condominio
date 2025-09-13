// web/src/lib/api.ts
import axios from "axios";
import { clearAuth, loadAuthFromStorage } from "./auth";

const baseURL =
  (import.meta.env.VITE_API_URL as string) ||
  (import.meta.env.NEXT_PUBLIC_API_URL as string) ||
  "http://localhost:8080";

export const api = axios.create({ baseURL, timeout: 10000 });

// Header default do tenant (boa prática / fallback)
(api.defaults.headers.common as any)["X-Tenant"] = "demo";

export function setToken(token?: string) {
  if (token) (api.defaults.headers.common as any)["Authorization"] = `Bearer ${token}`;
  else delete (api.defaults.headers.common as any)["Authorization"];
}

console.log("[API] baseURL =", baseURL);

// Hidrata token salvo ao carregar o app
const { token } = loadAuthFromStorage();
if (token) setToken(token);

// >>> Injeta SEMPRE token e tenant em TODA request
api.interceptors.request.use((config) => {
  try {
    const ls = typeof window !== "undefined" ? window.localStorage : null;
    const tk = ls?.getItem("condo:token") || undefined;
    const tenant = ls?.getItem("condo:tenant") || "demo";

    config.headers = config.headers ?? {};
    if (tk) (config.headers as any)["Authorization"] = `Bearer ${tk}`;
    (config.headers as any)["X-Tenant"] = tenant;
    (config.headers as any)["X-Tenant-ID"] = tenant; // compat
  } catch {
    // no-op
  }
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
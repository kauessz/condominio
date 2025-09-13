// web/src/lib/api.ts
import axios from "axios";
import { clearAuth, loadAuthFromStorage } from "./auth";

const baseURL =
  (import.meta.env.VITE_API_URL as string) ||
  (import.meta.env.NEXT_PUBLIC_API_URL as string) ||
  "http://localhost:8080";

export const api = axios.create({ baseURL: "/api" });

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
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // evita cache bobo de alguns proxies
  config.headers["Cache-Control"] = "no-store";
  config.headers["Pragma"] = "no-cache";
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
// apps/web/src/lib/api.ts
import axios from "axios";
import type { InternalAxiosRequestConfig } from "axios";
import { getToken, getTenant, clearAuth, getRefreshToken, saveAuth } from "./auth";

export function normalizeToken(t?: string) {
  if (!t) return "";
  return t.replace(/^Bearer\s+/i, "").trim();
}

const api = axios.create({
  baseURL:
    import.meta.env.VITE_API_URL ||
    import.meta.env.NEXT_PUBLIC_API_URL ||
    "http://localhost:8080",
  withCredentials: false,
});

// flags opcionais
type InternalFlags = {
  noAuth?: boolean;
  skipAuthRedirect?: boolean;
};

// helpers p/ AxiosHeaders ou objeto literal
function hdrHas(hdrs: any, key: string) {
  if (!hdrs) return false;
  if (typeof hdrs.has === "function") return hdrs.has(key);
  return key in hdrs;
}
function hdrSet(hdrs: any, key: string, val: any) {
  if (!hdrs) return;
  if (typeof hdrs.set === "function") hdrs.set(key, val);
  else hdrs[key] = val;
}

// REQUEST
api.interceptors.request.use((config) => {
  const cfg = (config ?? {}) as InternalAxiosRequestConfig & InternalFlags;

  (cfg as any).headers = (cfg.headers ?? {}) as any;
  const headers: any = (cfg as any).headers;

  const noAuth =
    !!cfg.noAuth || (headers && (headers["No-Auth"] || headers["no-auth"]));

  const skipAuthRedirect =
    cfg.skipAuthRedirect !== undefined
      ? !!cfg.skipAuthRedirect
      : headers && headers["X-Skip-Auth-Redirect"] !== undefined
      ? !!headers["X-Skip-Auth-Redirect"]
      : false;

  if (!noAuth) {
    const tok = normalizeToken(getToken());
    if (tok && !hdrHas(headers, "Authorization")) {
      hdrSet(headers, "Authorization", `Bearer ${tok}`);
    }
    const tenant = getTenant();
    if (tenant && !hdrHas(headers, "X-Tenant")) {
      hdrSet(headers, "X-Tenant", tenant);
    }
  }

  hdrSet(headers, "X-Skip-Auth-Redirect", skipAuthRedirect);
  return cfg;
});

// ===== Refresh orchestration =====
let isRefreshing = false;
let pendingQueue: Array<(newToken: string) => void> = [];

function queueRequest(cb: (t: string) => void) {
  pendingQueue.push(cb);
}
function flushQueue(newToken: string) {
  pendingQueue.forEach((fn) => fn(newToken));
  pendingQueue = [];
}

// RESPONSE
api.interceptors.response.use(
  (res) => res,
  async (err) => {
    const status = err?.response?.status;
    const url = String(err?.config?.url || "");
    const headers = (err?.config?.headers ?? {}) as Record<string, any>;
    const skipRedirect =
      !!headers["X-Skip-Auth-Redirect"] || !!headers["No-Auth"];

    const alreadyRetried = (err?.config as any)?._retried;

    if (
      status === 401 &&
      !/\/auth\/(login|google|refresh|request-reset|reset|logout)/i.test(url) &&
      !skipRedirect &&
      !alreadyRetried
    ) {
      const rt = getRefreshToken();
      if (!rt) {
        clearAuth();
        location.replace("/");
        return Promise.reject(err);
      }

      // se já há refresh em andamento, espera na fila
      if (isRefreshing) {
        return new Promise((resolve) => {
          queueRequest((newToken) => {
            (err.config as any)._retried = true;
            err.config.headers["Authorization"] = `Bearer ${newToken}`;
            resolve(api.request(err.config));
          });
        });
      }

      try {
        isRefreshing = true;
        const tenant = getTenant();
        const r = await api.post(
          "/auth/refresh",
          { refreshToken: rt, tenant },
          { headers: { "No-Auth": "1", "X-Skip-Auth-Redirect": "1" } }
        );
        const newToken = normalizeToken(r.data?.token);
        const newRefresh = r.data?.refreshToken;

        if (!newToken || !newRefresh) {
          throw new Error("bad_refresh_response");
        }

        // atualiza storage sem mexer no user salvo
        const userRaw = localStorage.getItem("condo:user");
        const user = userRaw ? JSON.parse(userRaw) : null;
        saveAuth(newToken, user || { id: "user", name: "User", email: "", role: "ADMIN" }, tenant, newRefresh);

        // seta default header para novas requests
        api.defaults.headers.common["Authorization"] = `Bearer ${newToken}`;

        // libera fila
        flushQueue(newToken);

        // re-executa a request original
        (err.config as any)._retried = true;
        err.config.headers["Authorization"] = `Bearer ${newToken}`;
        return api.request(err.config);
      } catch (e) {
        clearAuth();
        location.replace("/");
        return Promise.reject(e);
      } finally {
        isRefreshing = false;
      }
    }

    // fallback: derruba sessão apenas para 401 "duros"
    if (status === 401 && !/\/auth\/login/i.test(url) && !skipRedirect) {
      const reasonHeader = err?.response?.headers?.["x-auth-error"];
      const reasonBody = err?.response?.data?.error;
      const reason = String((reasonHeader || reasonBody || "")).toLowerCase();
      const hardReasons = new Set([
        "unauthorized",
        "bad_issuer",
        "jwt_secret_not_configured",
        "no_subject",
      ]);
      if (hardReasons.has(reason)) {
        clearAuth();
        location.replace("/");
      }
    }
    return Promise.reject(err);
  }
);

// helper opcional
export function setToken(token?: string) {
  const t = normalizeToken(token);
  if (t) api.defaults.headers.common["Authorization"] = `Bearer ${t}`;
  else delete api.defaults.headers.common["Authorization"];
}

export default api;
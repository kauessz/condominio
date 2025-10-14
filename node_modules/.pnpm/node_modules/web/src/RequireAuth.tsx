import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { setToken } from "./lib/api";
import { loadAuthFromStorage, setUser, hasToken } from "./lib/auth";
import api from "./lib/api";

export default function RequireAuth({ children }: { children: ReactNode }) {
  const [ok, setOk] = useState(false);
  const nav = useNavigate();
  const loc = useLocation();

  useEffect(() => {
    let alive = true;

    const { token, user } = loadAuthFromStorage();

    // Sem token -> manda pro login via SPA
    if (!hasToken() || !token) {
      nav("/", { replace: true, state: { from: loc } });
      return;
    }

    // Garante header Authorization no axios
    setToken(token);

    // Se já temos o user no storage, seguimos sem round-trip
    if (user) {
      setUser(user);
      if (alive) setOk(true);
      return;
    }

    // Fallback: tenta buscar /auth/me (sem derrubar sessão via interceptor)
    api
      .get("/auth/me", { headers: { "X-Skip-Auth-Redirect": "true" } })
      .then((r) => {
        if (!alive) return;
        setUser(r.data);
        setOk(true);
      })
      .catch(() => {
        if (!alive) return;
        nav("/", { replace: true, state: { from: loc } });
      });

    return () => {
      alive = false;
    };
  }, [nav, loc]);

  if (!ok) return null;
  return <>{children}</>;
}
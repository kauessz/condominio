// src/RequireAuth.tsx
import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { api, setToken } from "./lib/api";
import { loadAuthFromStorage, setUser } from "./lib/auth";

export default function RequireAuth({ children }: { children: ReactNode }) {
  const [ok, setOk] = useState(false);

  useEffect(() => {
    const { token, user } = loadAuthFromStorage();
    if (!token) { location.replace("/"); return; }

    setToken(token);

    if (user) {
      setUser(user);
      setOk(true);
      return;
    }

    // fallback: pedir /auth/me se não tiver user no storage
    api.get("/auth/me")
      .then(r => { setUser(r.data); setOk(true); })
      .catch(() => { location.replace("/"); });
  }, []);

  if (!ok) return null; // (poderia exibir um spinner)
  return <>{children}</>;
}
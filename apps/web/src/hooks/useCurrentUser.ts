import { useState, useEffect } from "react";
import api from "../lib/api";
import { getUser, setUser, type User, type Role } from "../lib/auth";

interface ApiMeResponse {
  id: number;
  email: string;
  name: string;
  role: string;
  tenant: string;
  unitId?: number | null;
  createdAt?: string;
}

/**
 * Hook que retorna o usuário autenticado.
 *
 * Estratégia:
 * 1. Lê do cache em memória/localStorage para resposta imediata.
 * 2. Se não tiver cache, chama GET /api/auth/me para buscar dados atualizados.
 */
export function useCurrentUser() {
  const cached = getUser();
  const [user, setLocalUser] = useState<User | null>(cached);
  const [loading, setLoading] = useState(!cached);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (cached) {
      setLocalUser(cached);
      setLoading(false);
      return;
    }

    let alive = true;

    api
      .get<ApiMeResponse>("/api/auth/me")
      .then((res) => {
        if (!alive) return;
        const data = res.data;
        const u: User = {
          id: String(data.id ?? ""),
          name: data.name ?? data.email ?? "",
          email: data.email ?? "",
          role: (data.role as Role) ?? "ADMIN",
          unitId: data.unitId != null ? String(data.unitId) : undefined,
        };
        setUser(u);
        setLocalUser(u);
      })
      .catch((err) => {
        if (!alive) return;
        setError(err?.message ?? "Falha ao buscar usuário");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });

    return () => {
      alive = false;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return { user, loading, error };
}

import { useEffect, useState } from "react";
import type { User } from "../lib/auth";

const STORAGE_KEY = "condo:superuser:selectedCondominiumId";

export function useSuperadminCondominiumFilter(user: User | null | undefined) {
  const isSuperuser = user?.role === "SUPERUSER";

  const [selectedCondominiumId, setSelectedCondominiumId] = useState(() => {
    if (!isSuperuser) {
      return user?.condominiumId != null ? String(user.condominiumId) : "";
    }
    return localStorage.getItem(STORAGE_KEY) ?? "";
  });

  useEffect(() => {
    if (!isSuperuser) {
      setSelectedCondominiumId(user?.condominiumId != null ? String(user.condominiumId) : "");
      return;
    }
    const saved = localStorage.getItem(STORAGE_KEY) ?? "";
    setSelectedCondominiumId(saved);
  }, [isSuperuser, user?.condominiumId]);

  useEffect(() => {
    if (!isSuperuser) {
      return;
    }
    localStorage.setItem(STORAGE_KEY, selectedCondominiumId);
  }, [isSuperuser, selectedCondominiumId]);

  return { selectedCondominiumId, setSelectedCondominiumId, isSuperuser };
}

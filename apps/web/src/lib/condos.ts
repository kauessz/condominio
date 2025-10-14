// web/src/lib/condos.ts
import { api } from "./api";

export type Condo = {
  id: number;
  name: string;
  cnpj?: string;
  tenantId?: string;
  createdAt?: string;
};

type SpringPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // zero-based
  size: number;
};

export async function listCondominiums(page = 1, pageSize = 20) {
  // seu controller aceita page 1-based e converte para zero-based internamente
  const r = await api.get<SpringPage<Condo>>("/condominiums", {
    params: { page, pageSize },
  });
  const p = r.data;
  return {
    items: p.content,
    total: p.totalElements,
    totalPages: p.totalPages,
    page: p.number + 1,
    pageSize: p.size,
  };
}

export async function createCondominium(data: { name: string; cnpj: string }) {
  // se quiser, normaliza o CNPJ aqui (apenas dígitos)
  const onlyDigits = (s: string) => s.replace(/\D/g, "");
  const body = { ...data, cnpj: onlyDigits(data.cnpj) };
  const r = await api.post<Condo>("/condominiums", body);
  return r.data;
}

export async function updateCondominium(
  id: number,
  data: { name: string; cnpj: string }
) {
  const r = await api.put<Condo>(`/condominiums/${id}`, data);
  return r.data;
}

export async function deleteCondominium(id: number) {
  await api.delete<void>(`/condominiums/${id}`);
}
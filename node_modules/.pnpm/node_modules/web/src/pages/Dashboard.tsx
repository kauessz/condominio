import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import api from "../lib/api";
import { useToast } from "../components/Toast";

type Condo = {
  id: number;
  name: string;
  cnpj?: string | null;
  tenantId?: string | null;
};

type CondoWithCounters = Condo & {
  unitCount?: number;
  residentCount?: number;
  pendingVisitors?: number;
};

function normalizePage<T = any>(raw: any): { items: T[]; total: number; page: number; size: number } {
  if (!raw) return { items: [], total: 0, page: 0, size: 0 };
  if (Array.isArray(raw.items) && typeof raw.total === "number")
    return { items: raw.items as T[], total: raw.total, page: Number(raw.page ?? 0), size: Number(raw.pageSize ?? raw.size ?? raw.items.length) };
  if (Array.isArray(raw.content) && typeof raw.totalElements === "number")
    return { items: raw.content as T[], total: raw.totalElements, page: Number(raw.number ?? 0), size: Number(raw.size ?? raw.content.length) };
  if (raw.data && Array.isArray(raw.data) && raw.meta?.total != null)
    return { items: raw.data as T[], total: Number(raw.meta.total), page: Number(raw.meta.page ?? 0), size: Number(raw.meta.size ?? raw.data.length) };
  if (Array.isArray(raw)) return { items: raw as T[], total: raw.length, page: 0, size: raw.length };
  for (const k of ["rows", "list", "result"]) {
    if (Array.isArray(raw?.[k])) return { items: raw[k] as T[], total: Number(raw?.total ?? raw?.count ?? raw?.[k]?.length ?? 0), page: 0, size: raw[k].length };
  }
  return { items: [], total: 0, page: 0, size: 0 };
}

export default function Dashboard() {
  const nav = useNavigate();
  const toast = useToast();
  const [sp, setSp] = useSearchParams();

  const [items, setItems] = useState<CondoWithCounters[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const page = Number(sp.get("page") ?? 0);
  const size = Number(sp.get("size") ?? 8);
  const q = sp.get("q") ?? "";

  const pageCount = useMemo(() => Math.max(1, Math.ceil(total / Math.max(size, 1))), [total, size]);

  function sync(next: Partial<Record<string, string | number>>) {
    const n = new URLSearchParams(sp);
    if (next.page !== undefined) n.set("page", String(next.page));
    if (next.size !== undefined) n.set("size", String(next.size));
    if (next.q !== undefined) n.set("q", String(next.q));
    setSp(n, { replace: true });
  }

  async function fetchCounters(c: Condo): Promise<CondoWithCounters> {
    try {
      const r = await api.get<{ units: number; residents: number; pendingVisitors: number }>(
        `/condominiums/${c.id}/counters`
      );
      return {
        ...c,
        unitCount: r.data.units,
        residentCount: r.data.residents,
        pendingVisitors: r.data.pendingVisitors,
      };
    } catch {
      return { ...c };
    }
  }

  async function load() {
    try {
      setLoading(true);
      const r = await api.get("/condominiums", { params: { q, page, size } });
      const pageData = normalizePage<Condo>(r.data);

      const withCounters = await Promise.all(pageData.items.map(fetchCounters));

      setItems(withCounters);
      setTotal(pageData.total);

      const last = Math.max(0, Math.ceil(pageData.total / Math.max(size, 1)) - 1);
      if (page > last) sync({ page: last });
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.error || "Falha ao carregar condomínios" });
      setItems([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [q, page, size]);

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <div className="text-xl font-semibold">Condomínios</div>
        <div className="flex items-center gap-3">
          <div className="text-slate-600">Itens por página</div>
          <select
            value={size}
            onChange={(e) => sync({ size: Number(e.target.value), page: 0 })}
            className="border rounded-lg px-2 py-2"
          >
            {[8, 12, 20].map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
          <button
            onClick={() => nav("/app/condo/new")}
            className="bg-slate-900 text-white px-4 py-2 rounded-lg"
          >
            Novo condomínio
          </button>
        </div>
      </div>

      {loading ? (
        <div className="text-slate-500">Carregando…</div>
      ) : items.length === 0 ? (
        <div className="text-slate-500">Nenhum condomínio encontrado.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {items.map((c) => (
            <div key={c.id} className="rounded-xl border p-4 shadow-sm">
              <div className="font-semibold">{c.name}</div>
              <div className="text-xs text-slate-500">{c.cnpj || ""}</div>

              <div className="mt-2 text-sm text-slate-700 flex flex-wrap gap-4">
                <div>Unidades: <b>{c.unitCount ?? "—"}</b></div>
                <div>Moradores: <b>{c.residentCount ?? "—"}</b></div>
                <div>Pendentes: <b>{c.pendingVisitors ?? "—"}</b></div>
              </div>

              <div className="mt-3 flex items-center gap-4 text-sm">
                <Link className="text-blue-600" to={`/app/units?condoId=${c.id}`}>Unidades →</Link>
                <Link className="text-blue-600" to={`/app/residents?condoId=${c.id}`}>Moradores →</Link>
                <Link className="text-blue-600" to={`/app/visitors?condoId=${c.id}`}>Visitantes →</Link>
                <span className="mx-1 text-slate-300">|</span>
                <Link className="text-blue-600" to={`/app/condo/${c.id}/edit`}>Editar</Link>
                <span className="mx-1 text-slate-300">|</span>
                <Link className="text-red-600" to={`/app/condo/${c.id}/delete`}>Excluir</Link>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="mt-6 flex items-center gap-3">
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => sync({ page: Math.max(0, page - 1) })}
          className="border rounded-lg px-3 py-2 disabled:opacity-50"
        >
          Anterior
        </button>
        <span className="text-slate-600">
          Página {page + 1} de {pageCount}
        </span>
        <button
          type="button"
          disabled={page + 1 >= pageCount}
          onClick={() => sync({ page: page + 1 })}
          className="border rounded-lg px-3 py-2 disabled:opacity-50"
        >
          Próxima
        </button>
      </div>
    </div>
  );
}
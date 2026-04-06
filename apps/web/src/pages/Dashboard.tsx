import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import { getUser } from "../lib/auth";

type Condo = {
  id: number;
  name: string;
  cnpj?: string | null;
  tenantId?: string | null;
};

type CondoWithCounters = Condo & {
  unitCount?: number;
  residentCount?: number;
  visitorCount?: number;
  pendingVisitors?: number;
};

type UnitSummary = {
  id: number;
  number?: string;
  block?: string | null;
  code?: string;
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
  return { items: [], total: 0, page: 0, size: 0 };
}

// ── Card de condomínio ────────────────────────────────────────────
function CondoCard({
  c,
  isSuperuser,
  isAdmin,
}: {
  c: CondoWithCounters;
  isSuperuser: boolean;
  isAdmin: boolean;
}) {
  const hasPending = (c.pendingVisitors ?? 0) > 0;

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden hover:shadow-md transition-shadow">
      {/* Borda colorida no topo */}
      <div className="h-1 bg-indigo-500" />

      <div className="p-5">
        {/* Header */}
        <div className="flex items-start gap-3 mb-4">
          <div className="w-10 h-10 bg-indigo-50 rounded-xl flex items-center justify-center flex-shrink-0">
            <svg className="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
              <path strokeLinecap="round" strokeLinejoin="round"
                d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="font-semibold text-slate-900 text-sm leading-tight" style={{ fontFamily: "var(--font-display)" }}>
              {c.name}
            </h3>
            {c.cnpj && <p className="text-xs text-slate-400 mt-0.5">{c.cnpj}</p>}
          </div>
          {hasPending && (
            <span className="flex-shrink-0 inline-flex items-center gap-1 text-xs px-2 py-1 rounded-full bg-amber-100 text-amber-700 font-medium">
              <span className="w-1.5 h-1.5 bg-amber-500 rounded-full" />
              {c.pendingVisitors} pendente{c.pendingVisitors !== 1 ? "s" : ""}
            </span>
          )}
        </div>

        {/* Métricas */}
        <div className="grid grid-cols-3 gap-2 mb-4">
          {[
            { label: "Unidades",  value: c.unitCount },
            { label: "Moradores", value: c.residentCount },
            { label: "Visitas",   value: c.visitorCount },
          ].map(({ label, value }) => (
            <div key={label} className="bg-slate-50 rounded-xl p-3 text-center">
              <p className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
                {value ?? "—"}
              </p>
              <p className="text-xs text-slate-500 mt-0.5">{label}</p>
            </div>
          ))}
        </div>

        {/* Links rápidos */}
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm border-t border-slate-100 pt-3">
          <Link className="text-indigo-600 hover:text-indigo-700 font-medium hover:underline" to={`/app/units?condoId=${c.id}`}>
            Unidades →
          </Link>
          <Link className="text-indigo-600 hover:text-indigo-700 font-medium hover:underline" to={`/app/residents?condoId=${c.id}`}>
            Moradores →
          </Link>
          <Link className="text-indigo-600 hover:text-indigo-700 font-medium hover:underline" to={`/app/visitors?condoId=${c.id}`}>
            Visitantes →
          </Link>

          {(isSuperuser || isAdmin) && (
            <Link className="text-slate-500 hover:text-slate-700 ml-auto" to={`/app/condo/${c.id}/edit`}>
              Editar
            </Link>
          )}
          {isSuperuser && (
            <Link className="text-rose-500 hover:text-rose-700" to={`/app/condo/${c.id}/delete`}>
              Excluir
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Estado vazio ──────────────────────────────────────────────────
function EmptyState({ isSuperuser, onNew }: { isSuperuser: boolean; onNew: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="w-16 h-16 bg-slate-100 rounded-2xl flex items-center justify-center mb-4">
        <svg className="w-8 h-8 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round"
            d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
        </svg>
      </div>
      <h3 className="text-slate-800 font-semibold mb-1" style={{ fontFamily: "var(--font-display)" }}>
        Nenhum condomínio encontrado
      </h3>
      <p className="text-slate-500 text-sm mb-5">
        {isSuperuser ? "Cadastre o primeiro condomínio para começar." : "Seu condomínio ainda não está configurado."}
      </p>
      {isSuperuser && (
        <button
          onClick={onNew}
          className="bg-indigo-600 hover:bg-indigo-700 text-white px-5 py-2.5 rounded-lg text-sm font-medium transition-colors shadow-sm"
        >
          + Novo condomínio
        </button>
      )}
    </div>
  );
}

// ── Dashboard ─────────────────────────────────────────────────────
export default function Dashboard() {
  const nav = useNavigate();
  const toast = useToast();
  const [sp, setSp] = useSearchParams();

  const currentUser = getUser();
  const isSuperuser = currentUser?.role === "SUPERUSER";
  const isAdmin     = currentUser?.role === "ADMIN";
  const isMorador   = currentUser?.role === "MORADOR";
  const isMultiCondo = isSuperuser;

  const [items, setItems] = useState<CondoWithCounters[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [myCondo, setMyCondo] = useState<Condo | null>(null);
  const [myUnit, setMyUnit] = useState<UnitSummary | null>(null);
  const [myResidentCount, setMyResidentCount] = useState(0);
  const [myParkingCode, setMyParkingCode] = useState<string | null>(null);

  const page = Number(sp.get("page") ?? 0);
  const size = Number(sp.get("size") ?? 8);
  const q    = sp.get("q") ?? "";

  const pageCount = useMemo(() => Math.max(1, Math.ceil(total / Math.max(size, 1))), [total, size]);

  function sync(next: Partial<Record<string, string | number>>) {
    const n = new URLSearchParams(sp);
    if (next.page !== undefined) n.set("page", String(next.page));
    if (next.size !== undefined) n.set("size", String(next.size));
    if (next.q   !== undefined) n.set("q",    String(next.q));
    setSp(n, { replace: true });
  }

  async function fetchCounters(c: Condo): Promise<CondoWithCounters> {
    try {
      const r = await api.get<{ units: number; residents: number; visitors: number; pendingVisitors: number }>(
        `/condominiums/${c.id}/counters`
      );
      return {
        ...c,
        unitCount: r.data.units,
        residentCount: r.data.residents,
        visitorCount: r.data.visitors,
        pendingVisitors: r.data.pendingVisitors,
      };
    } catch {
      return { ...c };
    }
  }

  async function load() {
    if (isMorador) {
      try {
        setLoading(true);
        const condominiumId = currentUser?.condominiumId ? Number(currentUser.condominiumId) : null;
        const unitId = currentUser?.unitId ? Number(currentUser.unitId) : null;
        if (!condominiumId) {
          setMyCondo(null);
          setMyUnit(null);
          setMyResidentCount(0);
          setMyParkingCode(null);
          return;
        }

        const [condoRes, unitRes, residentsRes, parkingRes] = await Promise.all([
          api.get(`/condominiums/${condominiumId}`),
          unitId ? api.get(`/units/${unitId}`) : Promise.resolve({ data: null }),
          api.get("/residents", { params: { page: 0, pageSize: 100 } }).catch(() => ({ data: { content: [] } })),
          api.get("/api/parking/my-assignment").catch(() => ({ data: { assignment: null } })),
        ]);

        setMyCondo(condoRes.data ?? null);
        setMyUnit(unitRes.data ?? null);
        setMyResidentCount((residentsRes.data?.content ?? residentsRes.data?.items ?? []).length);
        setMyParkingCode(parkingRes.data?.assignment ? `Vaga #${parkingRes.data.assignment.spotId}` : null);
        setItems([]);
        setTotal(0);
        return;
      } catch (err: any) {
        toast.show({ type: "error", msg: err?.response?.data?.error || "Falha ao carregar seu contexto" });
        setMyCondo(null);
        setMyUnit(null);
        setMyResidentCount(0);
        setMyParkingCode(null);
        return;
      } finally {
        setLoading(false);
      }
    }

    try {
      setLoading(true);
      const r = await api.get("/condominiums", { params: { q, page, size } });
      const pageData = normalizePage<Condo>(r.data);
      const withCounters = await Promise.all(pageData.items.map(fetchCounters));
      setItems(withCounters);
      setTotal(pageData.total);
      if (isMultiCondo) {
        const last = Math.max(0, Math.ceil(pageData.total / Math.max(size, 1)) - 1);
        if (page > last) sync({ page: last });
      }
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.error || "Falha ao carregar condomínios" });
      setItems([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [q, page, size]);

  if (isMorador) {
    const unitLabel = myUnit
      ? `Unidade ${myUnit.number ?? myUnit.code ?? myUnit.id}${myUnit.block ? ` • Bloco ${myUnit.block}` : ""}`
      : "Unidade não vinculada";

    return (
      <div className="p-6 max-w-4xl">
        <div className="mb-6">
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            Minha unidade
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">Seu contexto residencial e operacional</p>
        </div>

        {loading ? (
          <div className="bg-white rounded-2xl border border-slate-100 shadow-sm h-52 animate-pulse" />
        ) : (
          <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
            <div className="h-1 bg-indigo-500" />
            <div className="p-5">
              <div className="flex items-start gap-3 mb-4">
                <div className="w-10 h-10 bg-indigo-50 rounded-xl flex items-center justify-center flex-shrink-0">
                  <svg className="w-5 h-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.8}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                  </svg>
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="font-semibold text-slate-900 text-sm leading-tight" style={{ fontFamily: "var(--font-display)" }}>
                    {myCondo?.name ?? "Condomínio não vinculado"}
                  </h3>
                  <p className="text-xs text-slate-500 mt-0.5">{unitLabel}</p>
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2 mb-4">
                {[
                  { label: "Minha unidade", value: myUnit ? "1" : "—" },
                  { label: "Moradores da unidade", value: String(myResidentCount) },
                  { label: "Minha vaga", value: myParkingCode ?? "—" },
                ].map(({ label, value }) => (
                  <div key={label} className="bg-slate-50 rounded-xl p-3 text-center">
                    <p className="text-lg font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
                      {value}
                    </p>
                    <p className="text-xs text-slate-500 mt-0.5">{label}</p>
                  </div>
                ))}
              </div>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm border-t border-slate-100 pt-3">
                <Link className="text-indigo-600 hover:text-indigo-700 font-medium hover:underline" to="/app/residents">
                  Moradores da unidade →
                </Link>
                <Link className="text-indigo-600 hover:text-indigo-700 font-medium hover:underline" to="/app/financial">
                  Minhas cobranças →
                </Link>
                <Link className="text-indigo-600 hover:text-indigo-700 font-medium hover:underline" to="/app/reservations">
                  Reservas →
                </Link>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="p-6 max-w-5xl">
      {/* Page header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            {isMultiCondo ? "Condomínios" : "Meu Condomínio"}
          </h1>
          {isMultiCondo && total > 0 && (
            <p className="text-sm text-slate-500 mt-0.5">{total} condomínio{total !== 1 ? "s" : ""} cadastrado{total !== 1 ? "s" : ""}</p>
          )}
        </div>

        <div className="flex items-center gap-3">
          {isMultiCondo && (
            <select
              value={size}
              onChange={(e) => sync({ size: Number(e.target.value), page: 0 })}
              className="border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm text-slate-600"
            >
              {[8, 12, 20].map((n) => (
                <option key={n} value={n}>{n} por página</option>
              ))}
            </select>
          )}

          {isSuperuser && (
            <button
              onClick={() => nav("/app/condo/new")}
              className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors shadow-sm"
            >
              + Novo condomínio
            </button>
          )}
        </div>
      </div>

      {/* Busca (só para SUPERUSER) */}
      {isMultiCondo && (
        <div className="mb-5">
          <input
            type="text"
            placeholder="Buscar condomínio…"
            value={q}
            onChange={(e) => sync({ q: e.target.value, page: 0 })}
            className="border border-slate-200 rounded-lg px-3 py-2 text-sm w-full max-w-sm"
          />
        </div>
      )}

      {/* Conteúdo */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[1, 2].map((i) => (
            <div key={i} className="bg-white rounded-2xl border border-slate-100 shadow-sm h-52 animate-pulse" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <EmptyState isSuperuser={isSuperuser} onNew={() => nav("/app/condo/new")} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {items.map((c) => (
            <CondoCard key={c.id} c={c} isSuperuser={isSuperuser} isAdmin={isAdmin} />
          ))}
        </div>
      )}

      {/* Paginação */}
      {isMultiCondo && pageCount > 1 && (
        <div className="mt-6 flex items-center gap-3">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => sync({ page: Math.max(0, page - 1) })}
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-50 transition-colors"
          >
            ← Anterior
          </button>
          <span className="text-sm text-slate-500">
            Página {page + 1} de {pageCount}
          </span>
          <button
            type="button"
            disabled={page + 1 >= pageCount}
            onClick={() => sync({ page: page + 1 })}
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-50 transition-colors"
          >
            Próxima →
          </button>
        </div>
      )}
    </div>
  );
}

// apps/web/src/pages/Units.tsx
import { useEffect, useMemo, useState } from "react";
import { Navigate, useNavigate, useSearchParams, useLocation } from "react-router-dom";
import api from "../lib/api";
import { canAccessModule, canManageUnits, getUser } from "../lib/auth";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type SortBy = "number" | "block";
type SortDir = "asc" | "desc";

type Unit = {
  id: number;
  number: string | number;
  block?: string | null;
  residentCount?: number;
};

type ResidentLite = { id: number; unitId?: number | null; unit?: { id: number } | null };
type Condo = { id: number; name: string };

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

export default function Units() {
  const nav = useNavigate();
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const loc = useLocation() as any;

  const currentUser = getUser();
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);
  const canManage = canManageUnits();

  // filtros
  const condoId = isSuperuser
    ? Number(selectedCondominiumId || "0")
    : Number(sp.get("condoId") || sp.get("condominiumId") || currentUser?.condominiumId || "0");
  const [q, setQ] = useState(sp.get("q") ?? "");
  const [page, setPage] = useState<number>(Number(sp.get("page") ?? 0));
  const [pageSize, setPageSize] = useState<number>(Number(sp.get("pageSize") ?? 8));
  const [sortBy, setSortBy] = useState<SortBy>((sp.get("sortBy") as SortBy) ?? "number");
  const [sortDir, setSortDir] = useState<SortDir>((sp.get("sortDir") as SortDir) ?? "asc");

  // dados
  const [items, setItems] = useState<Unit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  // nome do condomínio (pode vir do state do Link do dashboard)
  const [condoName, setCondoName] = useState<string>(loc?.state?.condoName ?? "");

  // contagens (fallback)
  const [counts, setCounts] = useState<Record<number, number>>({});
  const [condos, setCondos] = useState<Condo[]>([]);

  const pageCount = useMemo(() => Math.max(1, Math.ceil(total / pageSize)), [total, pageSize]);

  if (!canAccessModule("units")) {
    return <Navigate to="/app/dashboard" replace />;
  }

  function syncUrl(next: Partial<Record<string, string | number>>) {
    const nextSp = new URLSearchParams(sp);
    if (next.q !== undefined) nextSp.set("q", String(next.q));
    if (next.page !== undefined) nextSp.set("page", String(next.page));
    if (next.pageSize !== undefined) nextSp.set("pageSize", String(next.pageSize));
    if (next.sortBy !== undefined) nextSp.set("sortBy", String(next.sortBy));
    if (next.sortDir !== undefined) nextSp.set("sortDir", String(next.sortDir));
    setSp(nextSp, { replace: true });
  }

  useEffect(() => {
    if (!isSuperuser) return;
    api.get("/condominiums", { params: { pageSize: 100 } })
      .then((r) => {
        const raw = r.data;
        const list: Condo[] = Array.isArray(raw.content) ? raw.content : Array.isArray(raw.items) ? raw.items : Array.isArray(raw) ? raw : [];
        setCondos(list);
      })
      .catch(() => setCondos([]));
  }, [isSuperuser]);

  // buscar nome do condomínio (se não veio pelo state — apenas para SUPERUSER que tem condoId na URL)
  useEffect(() => {
    if (!condoId || condoName) return;
    api.get(`/condominiums/${condoId}`)
      .then(r => setCondoName(r.data?.name ?? ""))
      .catch(() => setCondoName(""));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [condoId]);

  async function loadCountsAgg(): Promise<boolean> {
    try {
      const params: Record<string, any> = {};
      if (isSuperuser && condoId) {
        params.condoId = condoId;
        params.condominiumId = condoId;
      }
      const r = await api.get<any>("/residents/count-by-unit", { params });
      if (r?.data && typeof r.data === "object") {
        const map: Record<number, number> = {};
        for (const k of Object.keys(r.data)) map[Number(k)] = Number(r.data[k]) || 0;
        setCounts(map);
        return true;
      }
    } catch {}
    return false;
  }

  async function loadCountsFallback() {
    try {
      const params: Record<string, any> = {
        q: "",
        page: 0,
        size: 1000,
        pageSize: 1000,
        sort: "name,asc",
        sortBy: "name",
        sortDir: "asc",
      };
      if (isSuperuser && condoId) {
        params.condoId = condoId;
        params.condominiumId = condoId;
      }
      const r = await api.get<any>("/residents", { params });
      const pageData = normalizePage<ResidentLite>(r.data);
      const map: Record<number, number> = {};
      for (const res of pageData.items) {
        const id = res.unitId ?? res.unit?.id ?? null;
        if (!id) continue;
        map[id] = (map[id] || 0) + 1;
      }
      setCounts(map);
    } catch {
      setCounts({});
    }
  }

  async function load() {
    try {
      setLoading(true);
      const params: Record<string, any> = {
        q,
        page,
        size: pageSize,
        pageSize,
        sort: `${sortBy},${sortDir}`,
        sortBy,
        sortDir,
      };
      if (isSuperuser && condoId) {
        params.condoId = condoId;
        params.condominiumId = condoId;
      }
      const resp = await api.get<any>("/units", { params });

      const pageData = normalizePage<Unit>(resp.data);
      setItems(pageData.items);
      setTotal(pageData.total);

      const lastPage = Math.max(0, Math.ceil(pageData.total / pageSize) - 1);
      if (page > lastPage) {
        setPage(lastPage);
        syncUrl({ page: lastPage });
      }

      const pageHasResidentCount = pageData.items.some((u) => typeof u.residentCount === "number");
      if (pageHasResidentCount) {
        const m: Record<number, number> = {};
        pageData.items.forEach((u) => {
          if (typeof u.residentCount === "number") m[u.id] = u.residentCount!;
        });
        setCounts((prev) => ({ ...prev, ...m }));
      } else {
        const ok = await loadCountsAgg();
        if (!ok) await loadCountsFallback();
      }
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.error || "Falha ao carregar unidades",
      });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [condoId, q, page, pageSize, sortBy, sortDir]);

  // modal criar/editar
  const [openModal, setOpenModal] = useState(false);
  const [editing, setEditing] = useState<Unit | null>(null);
  const [form, setForm] = useState<{ number: string; block: string }>({ number: "", block: "" });

  function openCreate() {
    if (isSuperuser && !condoId) {
      toast.show({ type: "error", msg: "Selecione um condomínio antes de criar uma unidade." });
      return;
    }
    setEditing(null);
    setForm({ number: "", block: "" });
    setOpenModal(true);
  }
  function openEdit(u: Unit) {
    setEditing(u);
    setForm({ number: String(u.number ?? ""), block: u.block ?? "" });
    setOpenModal(true);
  }

  async function submitModal() {
    try {
      if (!form.number.trim()) {
        toast.show({ type: "error", msg: "Número é obrigatório" });
        return;
      }
      if (isSuperuser && !condoId) {
        toast.show({ type: "error", msg: "Selecione um condomínio antes de salvar uma unidade." });
        return;
      }

      if (editing) {
        await api.put(`/units/${editing.id}`, {
          number: form.number.trim(),
          block: form.block.trim() || null,
        });
        toast.show({ type: "success", msg: "Unidade atualizada" });
      } else {
        // condominiumId é enviado mas para não-SUPERUSER o backend usa JWT
        await api.post("/units", {
          number: form.number.trim(),
          block: form.block.trim() || null,
          condoId: condoId || undefined,
          condominiumId: condoId || undefined,
        });
        toast.show({ type: "success", msg: "Unidade criada" });
        setPage(0);
        syncUrl({ page: 0 });
      }

      setOpenModal(false);
      load();
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.error || "Falha ao salvar unidade",
      });
    }
  }

  async function onDelete(u: Unit) {
    if (!confirm("Excluir esta unidade?")) return;
    try {
      await api.delete(`/units/${u.id}`);
      toast.show({ type: "success", msg: "Unidade excluída" });
      load();
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.error || "Não foi possível excluir",
      });
    }
  }

  function goResidents() {
    nav(`/app/residents`, { state: { condoName } });
  }
  function goBack() {
    nav(`/app/dashboard`);
  }

  function blockLabel(b?: string | null) {
    return b && b.trim() ? `Bloco ${b}` : "Sem bloco";
  }
  const groups = items.reduce<Record<string, Unit[]>>((acc, u) => {
    const key = blockLabel(u.block);
    (acc[key] ||= []).push(u);
    return acc;
  }, {});
  const groupKeys = Object.keys(groups).sort((a, b) => {
    const aIsSem = a === "Sem bloco";
    const bIsSem = b === "Sem bloco";
    if (aIsSem && !bIsSem) return 1;
    if (!aIsSem && bIsSem) return -1;
    return a.localeCompare(b, "pt-BR");
  });

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-2">
        <button type="button" onClick={goBack} className="text-slate-600 hover:underline">
          ← Voltar
        </button>
        <div className="text-sm text-slate-500">
          {condoName ? <>Condomínio • <span className="font-medium text-slate-700">{condoName}</span></> : null}
        </div>
        <div className="flex items-center gap-4">
          <button type="button" onClick={goResidents} className="text-blue-600 hover:underline">
            Ir para Moradores →
          </button>
          {canManage && (
            <button type="button" onClick={openCreate} className="bg-slate-900 text-white px-4 py-2 rounded-lg">
              Nova unidade
            </button>
          )}
        </div>
      </div>

      {isSuperuser && (
        <div className="mb-4 max-w-sm">
          <label className="block text-sm text-slate-600 mb-1">Condomínio</label>
          <select
            value={selectedCondominiumId}
            onChange={(e) => {
              setSelectedCondominiumId(e.target.value);
              setCondoName(condos.find((condo) => String(condo.id) === e.target.value)?.name ?? "");
            }}
            className="border rounded-lg px-3 py-2 w-full"
          >
            <option value="">Selecione um condomínio…</option>
            {condos.map((condo) => (
              <option key={condo.id} value={condo.id}>{condo.name}</option>
            ))}
          </select>
        </div>
      )}

      {/* Filtros */}
      <div className="flex flex-wrap gap-3 items-center mb-4">
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
            syncUrl({ q: e.target.value, page: 0 });
          }}
          placeholder="Buscar (número/bloco)"
          className="border rounded-lg px-3 py-2 min-w-[280px]"
        />

        <span className="text-slate-600">Ordenar por</span>
        <select
          value={sortBy}
          onChange={(e) => {
            const v = e.target.value as SortBy;
            setSortBy(v);
            setPage(0);
            syncUrl({ sortBy: v, page: 0 });
          }}
          className="border rounded-lg px-3 py-2"
        >
          <option value="number">Número</option>
          <option value="block">Bloco</option>
        </select>

        <select
          value={sortDir}
          onChange={(e) => {
            const v = e.target.value as SortDir;
            setSortDir(v);
            setPage(0);
            syncUrl({ sortDir: v, page: 0 });
          }}
          className="border rounded-lg px-3 py-2"
        >
          <option value="asc">Ascendente</option>
          <option value="desc">Descendente</option>
        </select>

        <div className="ml-auto flex items-center gap-2">
          <span className="text-slate-600">Itens por página</span>
          <select
            value={pageSize}
            onChange={(e) => {
              const next = Number(e.target.value);
              setPageSize(next);
              setPage(0);
              syncUrl({ pageSize: next, page: 0 });
            }}
            className="border rounded-lg px-2 py-2"
          >
            {[8, 12, 20, 50].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Lista agrupada por bloco */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={index} className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5 animate-pulse h-36" />
          ))}
        </div>
      ) : (
        <div className="space-y-6">
          {groupKeys.map((g) => (
            <div key={g}>
              <div className="font-semibold text-base mb-2 text-slate-800">{g}</div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {groups[g].map((u) => {
                  const c = counts[u.id] ?? u.residentCount ?? 0;
                  return (
                    <div key={u.id} className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5 transition-shadow hover:shadow-md">
                      <div className="flex items-start justify-between">
                        <div>
                          <div className="font-semibold text-slate-900 text-lg">
                            Unidade {u.number}
                          </div>
                          <div className="text-slate-500 text-sm mt-1">
                            {u.block ? `Bloco ${u.block}` : "Sem bloco"}
                          </div>
                        </div>
                        <span className="text-xs bg-slate-100 text-slate-600 px-2.5 py-1 rounded-full font-medium">
                          {c} {c === 1 ? "morador" : "moradores"}
                        </span>
                      </div>
                      {canManage && (
                        <div className="mt-4 flex gap-4 text-sm">
                          <button type="button" onClick={() => openEdit(u)} className="text-blue-600 hover:underline">Editar</button>
                          <button type="button" onClick={() => onDelete(u)} className="text-red-600 hover:underline">Excluir</button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
          {items.length === 0 && (
            <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-8 text-slate-500 text-sm">
              {isSuperuser && !condoId ? "Selecione um condomínio para visualizar e cadastrar unidades." : "Nenhuma unidade encontrada."}
            </div>
          )}
        </div>
      )}

      {/* Paginação */}
      <div className="mt-6 flex items-center gap-3">
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => {
            const p = Math.max(0, page - 1);
            setPage(p);
            syncUrl({ page: p });
          }}
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
          onClick={() => {
            const p = Math.min(page + 1, pageCount - 1);
            setPage(p);
            syncUrl({ page: p });
          }}
          className="border rounded-lg px-3 py-2 disabled:opacity-50"
        >
          Próxima
        </button>
      </div>

      {/* Modal */}
      <Modal open={openModal} onClose={() => setOpenModal(false)} title={editing ? "Editar unidade" : "Nova unidade"}>
        <div className="space-y-3">
          <div>
            <label className="block text-sm text-slate-600">Número</label>
            <input
              value={form.number}
              onChange={(e) => setForm((f) => ({ ...f, number: e.target.value }))}
              className="border rounded-lg px-3 py-2 w-full"
            />
          </div>
          <div>
            <label className="block text-sm text-slate-600">Bloco</label>
            <input
              value={form.block}
              onChange={(e) => setForm((f) => ({ ...f, block: e.target.value }))}
              className="border rounded-lg px-3 py-2 w-full"
            />
          </div>

          <div className="mt-4 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setOpenModal(false)}
              className="border px-4 py-2 rounded-lg"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={submitModal}
              className="bg-slate-900 text-white px-4 py-2 rounded-lg"
            >
              Salvar
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

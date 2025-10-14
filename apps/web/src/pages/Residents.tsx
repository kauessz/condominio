import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";

type SortBy = "name" | "email";
type SortDir = "asc" | "desc";

type Resident = {
  id: number;
  name: string;
  email: string;
  phone?: string | null;
  unitId?: number | null;
  unit?: { id: number; number?: string | number; block?: string | null } | null;
};

type UnitOpt = { id: number; label: string };

// ---------- helper de normalização ----------
function normalizePage<T = any>(raw: any): {
  items: T[];
  total: number;
  page: number;
  size: number;
} {
  if (!raw) return { items: [], total: 0, page: 0, size: 0 };
  if (Array.isArray(raw.items) && typeof raw.total === "number")
    return {
      items: raw.items as T[],
      total: raw.total,
      page: Number(raw.page ?? 0),
      size: Number(raw.pageSize ?? raw.size ?? raw.items.length),
    };
  if (Array.isArray(raw.content) && typeof raw.totalElements === "number")
    return {
      items: raw.content as T[],
      total: raw.totalElements,
      page: Number(raw.number ?? 0),
      size: Number(raw.size ?? raw.content.length),
    };
  if (raw.data && Array.isArray(raw.data) && raw.meta?.total != null)
    return {
      items: raw.data as T[],
      total: Number(raw.meta.total),
      page: Number(raw.meta.page ?? 0),
      size: Number(raw.meta.size ?? raw.data.length),
    };
  if (Array.isArray(raw))
    return { items: raw as T[], total: raw.length, page: 0, size: raw.length };
  return { items: [], total: 0, page: 0, size: 0 };
}

// ===== helpers de exibição de unidade (evitam erros de tipo) =====
function unitPretty(u?: { number?: string | number; block?: string | null } | null) {
  if (!u) return "sem unidade";
  const n = u.number ?? "";
  const b = u.block ? ` • Bloco ${u.block}` : "";
  return n ? `Unidade ${n}${b}` : "sem unidade";
}

function residentUnitText(r: Resident): string {
  // 1) payload já trouxe a unidade expandida?
  if (r.unit && (r.unit.number != null || r.unit.block != null)) {
    return unitPretty({ number: r.unit.number, block: r.unit.block ?? null });
  }
  // 2) apenas o id
  if (r.unitId) return `Unidade ${r.unitId}`;
  return "sem unidade";
}

export default function Residents() {
  const nav = useNavigate();
  const toast = useToast();
  const [sp, setSp] = useSearchParams();

  // ===== filtros URL =====
  const condoId = Number(sp.get("condoId") || sp.get("condominiumId") || "0");
  const [q, setQ] = useState(sp.get("q") ?? "");
  const [pageIndex, setPageIndex] = useState<number>(Number(sp.get("page") ?? 0));
  const [pageSize, setPageSize] = useState<number>(Number(sp.get("pageSize") ?? 8));
  const [sortBy, setSortBy] = useState<SortBy>((sp.get("sortBy") as SortBy) ?? "name");
  const [sortDir, setSortDir] = useState<SortDir>((sp.get("sortDir") as SortDir) ?? "asc");

  // ===== dados =====
  const [items, setItems] = useState<Resident[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  // unidades para o select
  const [unitOpts, setUnitOpts] = useState<UnitOpt[]>([]);

  const pageCount = useMemo(
    () => Math.max(1, Math.ceil(total / pageSize)),
    [total, pageSize]
  );

  function syncUrl(next: Partial<Record<string, string | number>>) {
    const nextSp = new URLSearchParams(sp);
    if (next.q !== undefined) nextSp.set("q", String(next.q));
    if (next.page !== undefined) nextSp.set("page", String(next.page));
    if (next.pageSize !== undefined) nextSp.set("pageSize", String(next.pageSize));
    if (next.sortBy !== undefined) nextSp.set("sortBy", String(next.sortBy));
    if (next.sortDir !== undefined) nextSp.set("sortDir", String(next.sortDir));
    setSp(nextSp, { replace: true });
  }

  async function loadUnitsOptions() {
    if (!condoId) return;
    try {
      const resp = await api.get<any>("/units", {
        params: { condoId, page: 0, pageSize: 1000, sortBy: "number", sortDir: "asc" },
      });
      const pageData = normalizePage<{ id: number; number: string | number; block?: string | null }>(resp.data);
      const opts: UnitOpt[] = pageData.items.map((u) => ({
        id: u.id,
        label: `${u.number}${u.block ? ` - Bloco ${u.block}` : ""}`,
      }));
      setUnitOpts(opts);
    } catch {
      // silencioso
    }
  }

  async function load() {
    if (!condoId) return;
    try {
      setLoading(true);
      const resp = await api.get<any>("/residents", {
        params: { condoId, q, page: pageIndex, pageSize, sortBy, sortDir },
      });
      const pageData = normalizePage<Resident>(resp.data);

      setItems(pageData.items);
      setTotal(pageData.total);

      const lastPage = Math.max(0, Math.ceil(pageData.total / pageSize) - 1);
      if (pageIndex > lastPage && pageCount > 1) {
        setPageIndex(lastPage);
        syncUrl({ page: lastPage });
      }
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.error || "Falha ao carregar moradores",
      });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadUnitsOptions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [condoId]);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [condoId, q, pageIndex, pageSize, sortBy, sortDir]);

  // ===== modal criar/editar =====
  const [openModal, setOpenModal] = useState(false);
  const [editing, setEditing] = useState<Resident | null>(null);
  const [form, setForm] = useState<{ name: string; email: string; phone: string; unitId: string }>(
    { name: "", email: "", phone: "", unitId: "" }
  );

  function openCreate() {
    setEditing(null);
    setForm({ name: "", email: "", phone: "", unitId: "" });
    setOpenModal(true);
  }

  function openEdit(r: Resident) {
    const selectedUnitId = r.unitId ?? r.unit?.id ?? null;
    setEditing(r);
    setForm({
      name: r.name ?? "",
      email: r.email ?? "",
      phone: r.phone ?? "",
      unitId: selectedUnitId ? String(selectedUnitId) : "",
    });
    setOpenModal(true);
  }

  async function submitModal() {
    try {
      if (!form.name.trim()) {
        toast.show({ type: "error", msg: "Nome é obrigatório" });
        return;
      }
      if (!form.email.trim()) {
        toast.show({ type: "error", msg: "Email é obrigatório" });
        return;
      }
      const unitId = form.unitId ? Number(form.unitId) : null;

      if (editing) {
        await api.put(`/residents/${editing.id}`, {
          name: form.name.trim(),
          email: form.email.trim(),
          phone: form.phone.trim() || null,
          condoId,
          unitId,
        });
        toast.show({ type: "success", msg: "Morador atualizado" });
      } else {
        await api.post("/residents", {
          name: form.name.trim(),
          email: form.email.trim(),
          phone: form.phone.trim() || null,
          condoId,
          unitId,
        });
        toast.show({ type: "success", msg: "Morador criado" });
        setPageIndex(0);
        syncUrl({ page: 0 });
      }

      setOpenModal(false);
      load();
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.error || "Falha ao salvar morador",
      });
    }
  }

  async function onDelete(r: Resident) {
    if (!confirm("Excluir este morador?")) return;
    try {
      await api.delete(`/residents/${r.id}`);
      toast.show({ type: "success", msg: "Morador excluído" });
      load();
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.error || "Não foi possível excluir",
      });
    }
  }

  function goBack() {
    nav(`/app/units?condoId=${condoId}`);
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <button onClick={goBack} className="text-slate-600 hover:underline">
          ← Voltar
        </button>
        <button
          onClick={openCreate}
          className="bg-slate-900 text-white px-4 py-2 rounded-lg"
        >
          Novo morador
        </button>
      </div>

      {/* filtros */}
      <div className="flex flex-wrap gap-3 items-center mb-4">
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPageIndex(0);
            syncUrl({ q: e.target.value, page: 0 });
          }}
          placeholder="Buscar (nome/email/telefone)"
          className="border rounded-lg px-3 py-2 min-w-[300px]"
        />

        <span className="text-slate-600">Ordenar por</span>
        <select
          value={sortBy}
          onChange={(e) => {
            const v = e.target.value as SortBy;
            setSortBy(v);
            setPageIndex(0);
            syncUrl({ sortBy: v, page: 0 });
          }}
          className="border rounded-lg px-3 py-2"
        >
          <option value="name">Nome</option>
          <option value="email">Email</option>
        </select>

        <select
          value={sortDir}
          onChange={(e) => {
            const v = e.target.value as SortDir;
            setSortDir(v);
            setPageIndex(0);
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
              setPageIndex(0);
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

      {/* lista */}
      {loading ? (
        <div className="text-slate-500">Carregando…</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {items.map((r) => {
            const unitText = residentUnitText(r);
            return (
              <div key={r.id} className="rounded-xl border p-4 shadow-sm">
                <div className="font-semibold">{r.name}</div>
                <div className="text-slate-600 text-sm">
                  {r.email} {r.phone ? ` • ${r.phone}` : ""}
                </div>
                <div className="text-slate-500 text-xs mt-1">
                  {unitText}
                </div>

                <div className="mt-3 flex gap-4 text-sm">
                  <button onClick={() => openEdit(r)} className="text-blue-600">
                    Editar
                  </button>
                  <button onClick={() => onDelete(r)} className="text-red-600">
                    Excluir
                  </button>
                </div>
              </div>
            );
          })}
          {items.length === 0 && (
            <div className="text-slate-500">Nenhum morador encontrado.</div>
          )}
        </div>
      )}

      {/* paginação */}
      <div className="mt-6 flex items-center gap-3">
        <button
          disabled={pageIndex <= 0}
          onClick={() => {
            const p = Math.max(0, pageIndex - 1);
            setPageIndex(p);
            syncUrl({ page: p });
          }}
          className="border rounded-lg px-3 py-2 disabled:opacity-50"
        >
          Anterior
        </button>
        <span className="text-slate-600">
          Página {pageIndex + 1} de {pageCount}
        </span>
        <button
          disabled={pageIndex + 1 >= pageCount}
          onClick={() => {
            const p = Math.min(pageIndex + 1, pageCount - 1);
            setPageIndex(p);
            syncUrl({ page: p });
          }}
          className="border rounded-lg px-3 py-2 disabled:opacity-50"
        >
          Próxima
        </button>
      </div>

      {/* modal */}
      <Modal
        open={openModal}
        onClose={() => setOpenModal(false)}
        title={editing ? "Editar morador" : "Novo morador"}
      >
        <div className="space-y-3">
          <div>
            <label className="block text-sm text-slate-600">Nome</label>
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              className="border rounded-lg px-3 py-2 w-full"
            />
          </div>

          <div>
            <label className="block text-sm text-slate-600">Email</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              className="border rounded-lg px-3 py-2 w-full"
            />
          </div>

          <div>
            <label className="block text-sm text-slate-600">Telefone</label>
            <input
              value={form.phone}
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
              className="border rounded-lg px-3 py-2 w-full"
            />
          </div>

          <div>
            <label className="block text-sm text-slate-600">
              Unidade (do condomínio atual)
            </label>
            <select
              value={form.unitId}
              onChange={(e) => setForm((f) => ({ ...f, unitId: e.target.value }))}
              className="border rounded-lg px-3 py-2 w-full"
            >
              <option value="">Sem unidade</option>
              {unitOpts.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.label}
                </option>
              ))}
            </select>
          </div>

          <div className="mt-4 flex justify-end gap-2">
            <button
              onClick={() => setOpenModal(false)}
              className="border px-4 py-2 rounded-lg"
            >
              Cancelar
            </button>
            <button
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
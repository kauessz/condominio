import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import api from "../lib/api";
import { can } from "../lib/auth";

type SortBy = "checkInAt" | "checkOutAt" | "name";
type SortDir = "asc" | "desc";
type Status = "PENDING" | "APPROVED" | "REJECTED" | "CHECKED_OUT" | "ALL";
type VType = "VISITOR" | "DELIVERY" | "SERVICE" | "ALL";

type VisitorRow = {
  id: number;
  name: string;
  document?: string;
  plate?: string;
  phone?: string | null;
  email?: string | null;
  type: "VISITOR" | "DELIVERY" | "SERVICE";
  carrier?: string | null;
  packages?: number | null;
  checkInAt?: string | null;
  checkOutAt?: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CHECKED_OUT";
};

type PageAny = {
  content?: VisitorRow[];
  totalElements?: number | string;
  total?: number | string;
  number?: number | string;
  size?: number | string;
  pageSize?: number | string;
  items?: VisitorRow[];
  rows?: VisitorRow[];
  page?: any;
  data?: any;
  result?: any;
};

type UnitLite = { id: number; number: string | number; block?: string | null };

/* ====================== helpers ====================== */

let _loggedOnce = false;

function unwrap(raw: any): any {
  if (!raw) return raw;
  if (raw.data) return unwrap(raw.data);
  if (raw.result) return unwrap(raw.result);
  if (raw.page && (raw.page.content || raw.page.items || raw.page.rows)) {
    return unwrap(raw.page);
  }
  return raw;
}

function normPage(input: any) {
  let raw: PageAny = unwrap(input) ?? {};
  const content = Array.isArray(raw.content) ? raw.content
                  : Array.isArray(raw.items) ? raw.items
                  : Array.isArray(raw.rows) ? raw.rows
                  : Array.isArray(raw) ? (raw as any[])
                  : [];

  const total =
    raw.totalElements ?? raw.total ?? (Array.isArray(content) ? content.length : 0);
  const page =
    raw.number ?? raw.page ?? 0;
  const size =
    raw.size ?? raw.pageSize ?? (Array.isArray(content) ? content.length : 0);

  const pageObj = {
    items: content as VisitorRow[],
    total: Number(total ?? 0),
    page: Number(page ?? 0),
    size: Number(size ?? 0),
  };

  if (!_loggedOnce && pageObj.items.length === 0) {
    _loggedOnce = true;
    // eslint-disable-next-line no-console
    console.debug("[visitors] payload recebido (vazio):", input);
  }

  return pageObj;
}

function unitLabel(u: UnitLite) {
  return `Unidade ${u.number}${u.block ? ` • Bloco ${u.block}` : ""}`;
}

function fmtDateTime(v?: string | null) {
  if (!v) return "—";
  try {
    const d = new Date(v);
    return d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
  } catch {
    return v;
  }
}

function badgeClass(base: string) {
  return `inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${base}`;
}

function statusBadge(s: VisitorRow["status"]) {
  switch (s) {
    case "PENDING":
      return <span className={badgeClass("bg-amber-50 text-amber-700 ring-1 ring-amber-200")}>PENDENTE</span>;
    case "APPROVED":
      return <span className={badgeClass("bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200")}>APROVADO</span>;
    case "REJECTED":
      return <span className={badgeClass("bg-rose-50 text-rose-700 ring-1 ring-rose-200")}>REJEITADO</span>;
    case "CHECKED_OUT":
      return <span className={badgeClass("bg-slate-100 text-slate-700 ring-1 ring-slate-200")}>CHECK-OUT</span>;
  }
}

function typeBadge(t: VisitorRow["type"]) {
  if (t === "DELIVERY") return <span className={badgeClass("bg-amber-100 text-amber-800 ring-1 ring-amber-200")}>Entrega</span>;
  if (t === "SERVICE") return <span className={badgeClass("bg-violet-100 text-violet-800 ring-1 ring-violet-200")}>Serviço</span>;
  return <span className={badgeClass("bg-slate-100 text-slate-700 ring-1 ring-slate-200")}>Visitante</span>;
}

/* ====================== component ====================== */

export default function Visitors() {
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();

  const condoId = Number(sp.get("condoId") || sp.get("condominiumId") || "0");

  const [q, setQ] = useState(sp.get("q") ?? "");
  const [from, setFrom] = useState(sp.get("from") ?? "");
  const [to, setTo] = useState(sp.get("to") ?? "");
  const [status, setStatus] = useState<Status>((sp.get("status") as Status) || "ALL");
  const [vtype, setVtype] = useState<VType>((sp.get("type") as VType) || "ALL");
  const [sortBy, setSortBy] = useState<SortBy>((sp.get("sortBy") as SortBy) || "checkInAt");
  const [sortDir, setSortDir] = useState<SortDir>((sp.get("sortDir") as SortDir) || "desc");
  const [page, setPage] = useState(Number(sp.get("page") || 0));
  const [size, setSize] = useState(Number(sp.get("size") || 8));

  const [rows, setRows] = useState<VisitorRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const [units, setUnits] = useState<UnitLite[]>([]);
  const [loadingUnits, setLoadingUnits] = useState(false);

  const pages = useMemo(() => Math.max(1, Math.ceil(total / Math.max(1, size))), [total, size]);

  function sync() {
    const n = new URLSearchParams(sp);
    n.set("condoId", String(condoId || ""));
    n.set("q", q || "");
    if (from) n.set("from", from); else n.delete("from");
    if (to) n.set("to", to); else n.delete("to");
    n.set("status", status);
    n.set("type", vtype);
    n.set("sortBy", sortBy);
    n.set("sortDir", sortDir);
    n.set("page", String(page));
    n.set("size", String(size));
    setSp(n, { replace: true });
  }

  async function load() {
    if (!condoId) return;
    setLoading(true);
    try {
      const params: Record<string, any> = {
        condoId,
        condominiumId: condoId,
        q: q || undefined,
        page,
        pageSize: size,
        sortBy,
        sortDir,
      };
      if (from) params.from = from;
      if (to) params.to = to;
      if (status !== "ALL") params.status = status;
      if (vtype !== "ALL") params.type = vtype;

      const r = await api.get<PageAny>("/visitors", { params });
      const p = normPage(r.data);
      setRows(p.items);
      setTotal(p.total);
      const last = Math.max(0, Math.ceil(p.total / Math.max(1, size)) - 1);
      if (page > last) setPage(last);
    } catch (err: any) {
      if (err?.response?.status !== 401) {
        const msg = err?.response?.data?.error || err?.response?.data?.message || "Falha ao carregar visitantes";
        window.alert(msg);
      }
    } finally {
      setLoading(false);
    }
  }

  async function loadUnits() {
    if (!condoId) return;
    setLoadingUnits(true);
    try {
      const r = await api.get<any>("/units", {
        params: {
          condoId,
          condominiumId: condoId,
          q: "",
          page: 0,
          pageSize: 1000,
          sortBy: "number",
          sortDir: "asc",
        },
      });
      const p = normPage(r.data);
      setUnits(p.items.map((u: any) => ({ id: u.id, number: u.number, block: u.block ?? null })));
    } catch (err: any) {
      if (err?.response?.status !== 401) setUnits([]);
    } finally {
      setLoadingUnits(false);
    }
  }

  useEffect(() => { sync(); /* eslint-disable-next-line */ }, [q, from, to, status, vtype, sortBy, sortDir, page, size]);
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [sp]);
  useEffect(() => { loadUnits(); /* eslint-disable-next-line */ }, [condoId]);

  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<{
    type: VType | "VISITOR";
    name: string;
    document: string;
    plate: string;
    phone: string;
    email: string;
    carrier: string;
    packages: string;
    unitId: string;
    expectedInAt: string;
  }>({
    type: "VISITOR",
    name: "",
    document: "",
    plate: "",
    phone: "",
    email: "",
    carrier: "",
    packages: "",
    unitId: "",
    expectedInAt: "",
  });

  function openModal() {
    setEditingId(null);
    setForm({
      type: "VISITOR",
      name: "",
      document: "",
      plate: "",
      phone: "",
      email: "",
      carrier: "",
      packages: "",
      unitId: "",
      expectedInAt: "",
    });
    setOpen(true);
  }

  function openEdit(v: VisitorRow) {
    setEditingId(v.id);
    setForm({
      type: v.type,
      name: v.name || "",
      document: v.document || "",
      plate: v.plate || "",
      phone: (v.phone ?? "") || "",
      email: (v.email ?? "") || "",
      carrier: (v.carrier ?? "") || "",
      packages: v.packages != null ? String(v.packages) : "",
      unitId: "",
      expectedInAt: "",
    });
    setOpen(true);
  }

  function goBack() {
    if (window.history.length > 1) nav(-1);
    else nav("/app");
  }

  async function submit() {
    if (!condoId) { window.alert("Condomínio inválido."); return; }
    if (!form.name.trim()) { window.alert("Nome é obrigatório."); return; }
    setSaving(true);
    try {
      const base: any = {
        name: form.name.trim(),
        document: form.document.trim() || null,
        plate: form.plate.trim() || null,
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        type: (form.type === "ALL" ? "VISITOR" : form.type) || "VISITOR",
        unitId: form.unitId ? Number(form.unitId) : null,
        expectedInAt: form.expectedInAt ? new Date(form.expectedInAt).toISOString() : null,
      };
      if (base.type === "DELIVERY") {
        base.carrier = form.carrier.trim() || null;
        base.packages = form.packages.trim() === "" ? null : Number(form.packages) || 0;
      } else {
        base.carrier = null;
        base.packages = null;
      }

      if (editingId == null) {
        // CREATE
        const payload = { condominiumId: condoId, status: "PENDING", ...base };
        await api.post("/visitors", payload);
        window.alert("Check-in criado com sucesso!");
      } else {
        // UPDATE
        await api.put(`/visitors/${editingId}`, base);
        window.alert("Registro atualizado!");
      }

      setOpen(false);
      await load();
    } catch (err: any) {
      if (err?.response?.status !== 401) {
        const msg = err?.response?.data?.error || err?.response?.data?.message || "Falha ao salvar";
        window.alert(msg);
      }
    } finally {
      setSaving(false);
    }
  }

  async function act(path: string, body?: any) {
    try {
      await api.post(path, body ?? {});
      await load();
    } catch (e: any) {
      if (e?.response?.status !== 401) {
        window.alert(e?.response?.data?.error || "Falha na operação");
      }
    }
  }

  async function approve(id: number) { await act(`/visitors/${id}/approve`); }
  async function reject(id: number) {
    const reason = window.prompt("Motivo (opcional):") || "";
    await act(`/visitors/${id}/reject`, reason ? { reason } : {});
  }
  async function checkout(id: number) { await act(`/visitors/${id}/checkout`); }
  async function handoff(id: number) { await act(`/visitors/${id}/handoff`); }

  return (
    <div className="mx-auto max-w-6xl px-6 py-6">
      <div className="mb-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            className="group inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-slate-600 hover:bg-slate-50"
            onClick={goBack}
            type="button"
          >
            <span className="inline-block h-4 w-4 rounded-full border border-slate-300 text-center leading-[14px] group-hover:border-slate-400">←</span>
            Voltar
          </button>
          <div className="hidden text-sm text-slate-500 md:block">/</div>
          <div className="text-lg font-semibold text-slate-800">Dashboard</div>
        </div>

        <button
          className="rounded-xl bg-slate-900 px-4 py-2.5 font-medium text-white shadow-sm transition hover:bg-slate-800 active:scale-[0.99]"
          onClick={openModal}
          type="button"
        >
          Novo check-in
        </button>
      </div>

      <div className="mb-4 grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-white/70 p-3 shadow-sm md:grid-cols-12">
        <div className="md:col-span-4">
          <input
            className="w-full rounded-lg border border-slate-300 px-3 py-2 outline-none ring-0 placeholder:text-slate-400 focus:border-slate-400"
            placeholder="Buscar (nome/documento/placa)"
            value={q}
            onChange={(e) => { setQ(e.target.value); setPage(0); }}
          />
        </div>

        <div className="md:col-span-2">
          <input
            id="from"
            type="datetime-local"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
            value={from}
            onChange={(e) => { setFrom(e.target.value); setPage(0); }}
          />
        </div>
        <div className="md:col-span-2">
          <input
            id="to"
            type="datetime-local"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
            value={to}
            onChange={(e) => { setTo(e.target.value); setPage(0); }}
          />
        </div>

        <div className="md:col-span-2">
          <select
            className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
            value={sortBy}
            onChange={(e) => { setSortBy(e.target.value as SortBy); setPage(0); }}
          >
            <option value="checkInAt">Entrada</option>
            <option value="checkOutAt">Saída</option>
            <option value="name">Nome</option>
          </select>
        </div>
        <div className="md:col-span-2">
          <select
            className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
            value={sortDir}
            onChange={(e) => { setSortDir(e.target.value as SortDir); setPage(0); }}
          >
            <option value="desc">Desc</option>
            <option value="asc">Asc</option>
          </select>
        </div>

        <div className="md:col-span-2">
          <select
            className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
            value={status}
            onChange={(e) => { setStatus(e.target.value as Status); setPage(0); }}
          >
            <option value="ALL">Todos</option>
            <option value="PENDING">Pendentes</option>
            <option value="APPROVED">Aprovados</option>
            <option value="REJECTED">Rejeitados</option>
            <option value="CHECKED_OUT">Check-out</option>
          </select>
        </div>

        <div className="md:col-span-2">
          <select
            className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
            value={vtype}
            onChange={(e) => { setVtype(e.target.value as VType); setPage(0); }}
          >
            <option value="ALL">Todos</option>
            <option value="VISITOR">Visitante</option>
            <option value="DELIVERY">Entrega</option>
            <option value="SERVICE">Serviço</option>
          </select>
        </div>

        <div className="md:col-span-2">
          <div className="flex items-center justify-end gap-2">
            <span className="text-sm text-slate-600">Itens/página</span>
            <select
              className="rounded-lg border border-slate-300 px-2 py-2 focus:border-slate-400"
              value={size}
              onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
            >
              {[8, 12, 20].map((n) => (<option key={n} value={n}>{n}</option>))}
            </select>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="rounded-xl border border-slate-200 bg-white/60 p-6 text-slate-500 shadow-sm">Carregando…</div>
      ) : rows.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white/50 p-6 text-slate-600">
          Nenhum visitante encontrado.
        </div>
      ) : (
        <div className="divide-y rounded-xl border border-slate-200 bg-white/70 shadow-sm">
          {rows.map((v) => (
            <div
              key={v.id}
              className="group flex items-start justify-between gap-4 p-4 transition hover:bg-slate-50/60"
            >
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex items-center gap-2">
                  <div className="truncate text-base font-semibold text-slate-800">{v.name}</div>
                  {typeBadge(v.type)}
                </div>
                <div className="text-sm text-slate-600">
                  {v.document || "—"} • {v.plate || "—"}
                  {v.type === "DELIVERY" ? (
                    <> • {v.carrier || "—"} • {v.packages ?? 0} vol.</>
                  ) : null}
                </div>
              </div>

              <div className="flex shrink-0 items-center gap-2">
                {can("edit") && (
                  <button
                    className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100"
                    onClick={() => openEdit(v)}
                  >
                    Editar
                  </button>
                )}
                {v.status === "PENDING" && (
                  <>
                    <button
                      className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-700 hover:bg-emerald-100"
                      onClick={() => approve(v.id)}
                    >
                      Aprovar
                    </button>
                    <button
                      className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-medium text-rose-700 hover:bg-rose-100"
                      onClick={() => reject(v.id)}
                    >
                      Rejeitar
                    </button>
                  </>
                )}
                {v.type === "DELIVERY" && v.status !== "CHECKED_OUT" && (
                  <button
                    className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-800 hover:bg-amber-100"
                    onClick={() => handoff(v.id)}
                  >
                    Entregar/Retirado
                  </button>
                )}
                {!v.checkOutAt && (
                  <button
                    className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100"
                    onClick={() => checkout(v.id)}
                  >
                    Check-out
                  </button>
                )}
              </div>

              <div className="min-w-[230px] text-right text-sm text-slate-600">
                <div>Entrada: <span className="font-medium text-slate-800">{fmtDateTime(v.checkInAt)}</span></div>
                <div>Saída: <span className="font-medium text-slate-800">{fmtDateTime(v.checkOutAt)}</span></div>
                <div className="mt-1">{statusBadge(v.status)}</div>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="mt-5 flex items-center justify-center gap-3">
        <button
          className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-700 shadow-sm disabled:opacity-50"
          onClick={() => setPage(Math.max(0, page - 1))}
          disabled={page <= 0}
        >
          Anterior
        </button>
        <span className="text-slate-600">Página <span className="font-semibold text-slate-800">{page + 1}</span> de <span className="font-semibold text-slate-800">{pages}</span></span>
        <button
          className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-700 shadow-sm disabled:opacity-50"
          onClick={() => setPage(Math.min(pages - 1, page + 1))}
          disabled={page + 1 >= pages}
        >
          Próxima
        </button>
      </div>

      {open && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4">
          <div className="w-[720px] max-w-[95vw] rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl">
            <div className="mb-3 flex items-center justify-between">
              <div className="text-lg font-semibold text-slate-900">
                {editingId == null ? "Novo check-in" : "Editar check-in"}
              </div>
              <button
                className="rounded-full p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                onClick={() => setOpen(false)}
                type="button"
                aria-label="Fechar"
              >
                ✕
              </button>
            </div>

            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <div>
                <label htmlFor="f-type" className="mb-1 block text-sm text-slate-600">Tipo</label>
                <select
                  id="f-type"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.type}
                  onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as any }))}
                >
                  <option value="VISITOR">Visitante</option>
                  <option value="DELIVERY">Entrega</option>
                  <option value="SERVICE">Serviço</option>
                </select>
              </div>

              <div>
                <label htmlFor="f-unit" className="mb-1 block text-sm text-slate-600">Unidade</label>
                <select
                  id="f-unit"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.unitId}
                  onChange={(e) => setForm((f) => ({ ...f, unitId: e.target.value }))}
                >
                  <option value="">— Selecionar —</option>
                  {loadingUnits ? (
                    <option value="" disabled>Carregando…</option>
                  ) : (
                    units.map((u) => (
                      <option key={u.id} value={u.id}>{unitLabel(u)}</option>
                    ))
                  )}
                </select>
              </div>

              <div className="md:col-span-2">
                <label htmlFor="f-name" className="mb-1 block text-sm text-slate-600">Nome *</label>
                <input
                  id="f-name"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                />
              </div>

              <div>
                <label htmlFor="f-doc" className="mb-1 block text-sm text-slate-600">Documento</label>
                <input
                  id="f-doc"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.document}
                  onChange={(e) => setForm((f) => ({ ...f, document: e.target.value }))}
                />
              </div>

              <div>
                <label htmlFor="f-plate" className="mb-1 block text-sm text-slate-600">Placa</label>
                <input
                  id="f-plate"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.plate}
                  onChange={(e) => setForm((f) => ({ ...f, plate: e.target.value }))}
                />
              </div>

              <div>
                <label htmlFor="f-phone" className="mb-1 block text-sm text-slate-600">Telefone</label>
                <input
                  id="f-phone"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.phone}
                  onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                />
              </div>

              <div>
                <label htmlFor="f-email" className="mb-1 block text-sm text-slate-600">E-mail</label>
                <input
                  id="f-email"
                  type="email"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.email}
                  onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                />
              </div>

              {form.type === "DELIVERY" && (
                <>
                  <div>
                    <label htmlFor="f-carrier" className="mb-1 block text-sm text-slate-600">Transportadora</label>
                    <input
                      id="f-carrier"
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                      value={form.carrier}
                      onChange={(e) => setForm((f) => ({ ...f, carrier: e.target.value }))}
                    />
                  </div>
                  <div>
                    <label htmlFor="f-packages" className="mb-1 block text-sm text-slate-600">Volumes</label>
                    <input
                      id="f-packages"
                      type="number"
                      min={0}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                      value={form.packages}
                      onChange={(e) => setForm((f) => ({ ...f, packages: e.target.value }))}
                    />
                  </div>
                </>
              )}

              <div className="md:col-span-2">
                <label htmlFor="f-expected" className="mb-1 block text-sm text-slate-600">Previsto para</label>
                <input
                  id="f-expected"
                  type="datetime-local"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-slate-400"
                  value={form.expectedInAt}
                  onChange={(e) => setForm((f) => ({ ...f, expectedInAt: e.target.value }))}
                />
              </div>
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
                onClick={() => setOpen(false)}
                disabled={saving}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="rounded-lg bg-slate-900 px-4 py-2 font-medium text-white shadow-sm transition hover:bg-slate-800 disabled:opacity-50"
                onClick={submit}
                disabled={saving}
              >
                {saving ? "Salvando…" : (editingId == null ? "Salvar" : "Atualizar")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
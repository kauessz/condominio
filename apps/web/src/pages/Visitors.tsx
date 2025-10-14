import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import api from "../lib/api";

// ====== Tipos ======
type SortBy = "checkInAt" | "checkOutAt" | "name";
type SortDir = "asc" | "desc";
type Status = "PENDING" | "APPROVED" | "REJECTED" | "CHECKED_OUT" | "ALL";
type VType = "VISITOR" | "DELIVERY" | "SERVICE" | "ALL";

type VisitorRow = {
  id: number;
  name: string;
  document?: string;
  plate?: string;
  type: "VISITOR" | "DELIVERY" | "SERVICE";
  carrier?: string | null;
  packages?: number | null;
  checkInAt?: string | null;
  checkOutAt?: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CHECKED_OUT";
};

type PageAny = {
  content?: VisitorRow[];
  totalElements?: number;
  total?: number;
  number?: number;
  size?: number;
  items?: VisitorRow[];
};

type UnitLite = { id: number; number: string | number; block?: string | null };

// ====== Utils ======
function normPage(raw: any) {
  if (!raw) return { items: [], total: 0, page: 0, size: 0 };
  if (Array.isArray(raw.content))
    return {
      items: raw.content as VisitorRow[],
      total: Number(raw.totalElements ?? raw.content.length),
      page: Number(raw.number ?? 0),
      size: Number(raw.size ?? raw.content.length),
    };
  if (Array.isArray(raw.items))
    return {
      items: raw.items as VisitorRow[],
      total: Number(raw.total ?? raw.items.length),
      page: Number(raw.page ?? 0),
      size: Number(raw.pageSize ?? raw.items.length),
    };
  if (Array.isArray(raw)) return { items: raw as VisitorRow[], total: raw.length, page: 0, size: raw.length };
  return { items: [], total: 0, page: 0, size: 0 };
}

function unitLabel(u: UnitLite) {
  return `Unidade ${u.number}${u.block ? ` • Bloco ${u.block}` : ""}`;
}

// ====== Página ======
export default function Visitors() {
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();

  const condoId = Number(sp.get("condoId") || sp.get("condominiumId") || "0");

  // filtros
  const [q, setQ] = useState(sp.get("q") ?? "");
  const [from, setFrom] = useState(sp.get("from") ?? "");
  const [to, setTo] = useState(sp.get("to") ?? "");
  const [status, setStatus] = useState<Status>((sp.get("status") as Status) || "ALL");
  const [vtype, setVtype] = useState<VType>((sp.get("type") as VType) || "ALL");
  const [sortBy, setSortBy] = useState<SortBy>((sp.get("sortBy") as SortBy) || "checkInAt");
  const [sortDir, setSortDir] = useState<SortDir>((sp.get("sortDir") as SortDir) || "desc");
  const [page, setPage] = useState(Number(sp.get("page") || 0));
  const [size, setSize] = useState(Number(sp.get("size") || 8));

  // dados
  const [rows, setRows] = useState<VisitorRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  // unidades para o modal
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
        q: q || undefined,
        page,
        pageSize: size, // mantemos pageSize; se o seu backend aceitar "size", troque aqui.
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
    } catch {
      // silencioso; opcionalmente exibir toast
    } finally {
      setLoading(false);
    }
  }

  async function loadUnits() {
    if (!condoId) return;
    setLoadingUnits(true);
    try {
      const r = await api.get<any>("/units", {
        params: { condoId, q: "", page: 0, pageSize: 1000, sortBy: "number", sortDir: "asc" },
      });
      const p = normPage(r.data);
      setUnits(
        p.items.map((u: any) => ({
          id: u.id,
          number: u.number,
          block: u.block ?? null,
        }))
      );
    } catch {
      setUnits([]);
    } finally {
      setLoadingUnits(false);
    }
  }

  useEffect(() => {
    sync();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, from, to, status, vtype, sortBy, sortDir, page, size]);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sp]);

  useEffect(() => {
    loadUnits();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [condoId]);

  // ====== Modal "Novo check-in" ======
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
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
    expectedInAt: string; // datetime-local
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

  // navegação segura (evita reload/logout)
  function goBack() {
    if (window.history.length > 1) nav(-1);
    else nav("/app");
  }

  async function submit() {
    if (!condoId) {
      window.alert("Condomínio inválido.");
      return;
    }
    if (!form.name.trim()) {
      window.alert("Nome é obrigatório.");
      return;
    }
    setSaving(true);
    try {
      const payload: any = {
        // >>> backend exige condominiumId
        condominiumId: condoId,
        name: form.name.trim(),
        document: form.document.trim() || null,
        plate: form.plate.trim() || null,
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        type: (form.type === "ALL" ? "VISITOR" : form.type) || "VISITOR",
        unitId: form.unitId ? Number(form.unitId) : null,
        // checkInAt: opcional; se nulo, servidor usa Instant.now()
        // enviar expectedInAt quando preenchido
        expectedInAt: form.expectedInAt ? new Date(form.expectedInAt).toISOString() : null,
        status: "PENDING",
      };

      if (payload.type === "DELIVERY") {
        payload.carrier = form.carrier.trim() || null;
        payload.packages = form.packages.trim() === "" ? null : Number(form.packages) || 0;
      }

      await api.post("/visitors", payload);
      setOpen(false);
      await load();
      window.alert("Check-in criado com sucesso!");
    } catch (err: any) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        "Falha ao salvar";
      window.alert(msg);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <button className="text-slate-600 hover:underline" onClick={goBack} type="button">
          ← Voltar
        </button>
        <button className="text-slate-600 hover:underline" onClick={() => nav("/app")} type="button">
          Dashboard
        </button>
        <button className="bg-slate-900 text-white px-4 py-2 rounded-lg" onClick={openModal} type="button">
          Novo check-in
        </button>
      </div>

      {/* Filtros */}
      <div className="flex flex-wrap items-center gap-2 mb-3">
        <input
          className="border rounded-lg px-3 py-2 min-w-[260px]"
          placeholder="Buscar (nome/documento/placa)"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
        />
        <input
          type="datetime-local"
          className="border rounded-lg px-3 py-2"
          value={from}
          onChange={(e) => {
            setFrom(e.target.value);
            setPage(0);
          }}
        />
        <input
          type="datetime-local"
          className="border rounded-lg px-3 py-2"
          value={to}
          onChange={(e) => {
            setTo(e.target.value);
            setPage(0);
          }}
        />

        <select
          className="border rounded-lg px-3 py-2"
          value={sortBy}
          onChange={(e) => {
            setSortBy(e.target.value as SortBy);
            setPage(0);
          }}
        >
          <option value="checkInAt">Entrada</option>
          <option value="checkOutAt">Saída</option>
          <option value="name">Nome</option>
        </select>

        <select
          className="border rounded-lg px-3 py-2"
          value={sortDir}
          onChange={(e) => {
            setSortDir(e.target.value as SortDir);
            setPage(0);
          }}
        >
          <option value="desc">Desc</option>
          <option value="asc">Asc</option>
        </select>

        <select
          className="border rounded-lg px-3 py-2"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as Status);
            setPage(0);
          }}
        >
          <option value="ALL">Todos</option>
          <option value="PENDING">Pendentes</option>
          <option value="APPROVED">Aprovados</option>
          <option value="REJECTED">Rejeitados</option>
          <option value="CHECKED_OUT">Check-out</option>
        </select>

        <select
          className="border rounded-lg px-3 py-2"
          value={vtype}
          onChange={(e) => {
            setVtype(e.target.value as VType);
            setPage(0);
          }}
        >
          <option value="ALL">Todos</option>
          <option value="VISITOR">Visitante</option>
          <option value="DELIVERY">Entrega</option>
          <option value="SERVICE">Serviço</option>
        </select>

        <div className="ml-auto flex items-center gap-2">
          <span className="text-slate-600">Itens por página</span>
          <select
            className="border rounded-lg px-2 py-2"
            value={size}
            onChange={(e) => {
              setSize(Number(e.target.value));
              setPage(0);
            }}
          >
            {[8, 12, 20].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Lista */}
      {loading ? (
        <div className="text-slate-500">Carregando…</div>
      ) : rows.length === 0 ? (
        <div className="border rounded-lg p-4 text-slate-600">Nenhum visitante encontrado.</div>
      ) : (
        <div className="border rounded-lg divide-y">
          {rows.map((v) => (
            <div key={v.id} className="p-3 flex items-center justify-between">
              <div className="min-w-0">
                <div className="font-medium truncate">
                  {v.name}{" "}
                  <span className="text-xs border rounded-full px-2 py-0.5 ml-2">
                    {v.type === "DELIVERY" ? "Entrega" : v.type}
                  </span>
                </div>
                <div className="text-sm text-slate-600">
                  {v.document || "-"} • {v.plate || "-"}
                  {v.type === "DELIVERY" ? (
                    <>
                      {" "}• {v.carrier || "—"} • {v.packages ?? 0} vol.
                    </>
                  ) : null}
                </div>
              </div>
              <div className="text-sm text-slate-600 text-right">
                <div>
                  Entrada: {v.checkInAt ? new Date(v.checkInAt).toLocaleString() : "—"}
                </div>
                <div>
                  Saída: {v.checkOutAt ? new Date(v.checkOutAt).toLocaleString() : "—"}
                </div>
                <div className="mt-1">
                  <span className="border rounded-full px-2 py-0.5 text-xs">{v.status}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Paginação */}
      <div className="mt-4 flex items-center gap-3">
        <button
          className="border rounded-lg px-3 py-2 disabled:opacity-50"
          onClick={() => setPage(Math.max(0, page - 1))}
          disabled={page <= 0}
        >
          Anterior
        </button>
        <span className="text-slate-600">
          Página {page + 1} de {pages}
        </span>
        <button
          className="border rounded-lg px-3 py-2 disabled:opacity-50"
          onClick={() => setPage(Math.min(pages - 1, page + 1))}
          disabled={page + 1 >= pages}
        >
          Próxima
        </button>
      </div>

      {/* ===== Modal "Novo check-in" ===== */}
      {open && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl w-[680px] max-w-[92vw] p-5 shadow-xl">
            <div className="flex items-center justify-between mb-3">
              <div className="text-lg font-semibold">Novo check-in</div>
              <button
                className="text-slate-500 hover:text-slate-700"
                onClick={() => setOpen(false)}
                type="button"
              >
                ✕
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <label className="block text-sm text-slate-600">Tipo</label>
                <select
                  className="border rounded-lg px-3 py-2 w-full"
                  value={form.type}
                  onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as any }))}
                >
                  <option value="VISITOR">Visitante</option>
                  <option value="DELIVERY">Entrega</option>
                  <option value="SERVICE">Serviço</option>
                </select>
              </div>

              <div>
                <label className="block text-sm text-slate-600">Unidade</label>
                <select
                  className="border rounded-lg px-3 py-2 w-full"
                  value={form.unitId}
                  onChange={(e) => setForm((f) => ({ ...f, unitId: e.target.value }))}
                >
                  <option value="">— Selecionar —</option>
                  {loadingUnits ? (
                    <option value="" disabled>Carregando…</option>
                  ) : (
                    units.map((u) => (
                      <option key={u.id} value={u.id}>
                        {unitLabel(u)}
                      </option>
                    ))
                  )}
                </select>
              </div>

              <div className="md:col-span-2">
                <label className="block text-sm text-slate-600">Nome *</label>
                <input
                  className="border rounded-lg px-3 py-2 w-full"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                />
              </div>

              <div>
                <label className="block text-sm text-slate-600">Documento</label>
                <input
                  className="border rounded-lg px-3 py-2 w-full"
                  value={form.document}
                  onChange={(e) => setForm((f) => ({ ...f, document: e.target.value }))}
                />
              </div>

              <div>
                <label className="block text-sm text-slate-600">Placa</label>
                <input
                  className="border rounded-lg px-3 py-2 w-full"
                  value={form.plate}
                  onChange={(e) => setForm((f) => ({ ...f, plate: e.target.value }))}
                />
              </div>

              <div>
                <label className="block text-sm text-slate-600">Telefone</label>
                <input
                  className="border rounded-lg px-3 py-2 w-full"
                  value={form.phone}
                  onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                />
              </div>

              <div>
                <label className="block text-sm text-slate-600">E-mail</label>
                <input
                  className="border rounded-lg px-3 py-2 w-full"
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                />
              </div>

              {form.type === "DELIVERY" && (
                <>
                  <div>
                    <label className="block text-sm text-slate-600">Transportadora</label>
                    <input
                      className="border rounded-lg px-3 py-2 w-full"
                      value={form.carrier}
                      onChange={(e) => setForm((f) => ({ ...f, carrier: e.target.value }))}
                    />
                  </div>
                  <div>
                    <label className="block text-sm text-slate-600">Volumes</label>
                    <input
                      className="border rounded-lg px-3 py-2 w-full"
                      type="number"
                      min={0}
                      value={form.packages}
                      onChange={(e) => setForm((f) => ({ ...f, packages: e.target.value }))}
                    />
                  </div>
                </>
              )}

              <div className="md:col-span-2">
                <label className="block text-sm text-slate-600">Previsto para</label>
                <input
                  type="datetime-local"
                  className="border rounded-lg px-3 py-2 w-full"
                  value={form.expectedInAt}
                  onChange={(e) => setForm((f) => ({ ...f, expectedInAt: e.target.value }))}
                />
              </div>
            </div>

            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                className="border px-4 py-2 rounded-lg"
                onClick={() => setOpen(false)}
                disabled={saving}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="bg-slate-900 text-white px-4 py-2 rounded-lg disabled:opacity-50"
                onClick={submit}
                disabled={saving}
              >
                {saving ? "Salvando…" : "Salvar"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
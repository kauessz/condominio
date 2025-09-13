// apps/web/src/pages/Residents.tsx
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import Card from "../components/Card";
import Modal from "../components/Modal";
import { api } from "../lib/api";
import { can } from "../lib/auth";
import { useToast } from "../components/Toast";

/* Tipos usados na tela */
type Unit = { id: string; number: string; block: string | null };
type Resident = {
  id: string;
  name: string;
  email: string;
  phone: string;
  condoId: string;
  unitId: string | null;
  unit?: Unit | null;
};

type SortBy = "name" | "unit";
type SortDir = "asc" | "desc";

/** Render de unidade como string */
function unitLabel(u?: Unit | null) {
  if (!u) return "Sem unidade";
  return `Unidade ${u.number}${u.block ? ` - Bloco ${u.block}` : ""}`;
}

export default function Residents() {
  const nav = useNavigate();
  const toast = useToast();

  // 1) pega da query string ?condoId=...
  const [sp] = useSearchParams();
  const condoIdFromQuery = sp.get("condoId") ?? "";

  // 2) fallback: se a rota for /app/condos/:id/residents
  const { id: condoIdFromParam } = useParams();

  // 3) escolhe o condoId definitivo
  const condoId = useMemo(
    () => condoIdFromQuery || (condoIdFromParam ?? ""),
    [condoIdFromQuery, condoIdFromParam]
  );

  // dados
  const [residentsRaw, setResidentsRaw] = useState<Resident[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  const [total, setTotal] = useState(0);

  // busca/paginação
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);           // UI 1-based
  const [pageSize] = useState(8);
  const [loading, setLoading] = useState(true);

  // ordenação
  const [sortBy, setSortBy] = useState<SortBy>("name");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  // modal (criar/editar)
  const [openModal, setOpenModal] = useState(false);
  const [editing, setEditing] = useState<Resident | null>(null);
  const [form, setForm] = useState({ name: "", email: "", phone: "", unitId: "" });

  function openCreate() {
    setEditing(null);
    setForm({ name: "", email: "", phone: "", unitId: "" });
    setOpenModal(true);
  }
  function openEdit(r: Resident) {
    setEditing(r);
    setForm({
      name: r.name,
      email: r.email,
      phone: r.phone,
      unitId: r.unitId ?? "",
    });
    setOpenModal(true);
  }

  /** Carrega moradores (sempre com condoId válido) */
  async function loadResidents() {
    if (!condoId) return; // evita call com undefined
    setLoading(true);
    try {
      const r = await api.get("/residents", {
        params: { condoId, q, page: Math.max(0, page - 1), size: pageSize, pageSize },
      });

      const data = r.data;
      const items: Resident[] = Array.isArray(data)
        ? data
        : data?.items ?? data?.content ?? [];
      const tot: number =
        typeof data?.total === "number"
          ? data.total
          : typeof data?.totalElements === "number"
          ? data.totalElements
          : Array.isArray(data)
          ? data.length
          : 0;

      setResidentsRaw(items);
      setTotal(tot);
    } finally {
      setLoading(false);
    }
  }

  /** Carrega unidades para o select do formulário */
  async function loadUnits() {
    if (!condoId) return;
    try {
      const r = await api.get("/units", {
        params: { condoId, page: 0, size: 999, pageSize: 999 },
      });
      const data = r.data;
      const items: Unit[] = Array.isArray(data) ? data : data?.items ?? data?.content ?? [];
      setUnits(items);
    } catch (err: any) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        "Erro ao carregar unidades";
      toast.show({ type: "error", msg });
      setUnits([]);
    }
  }

  useEffect(() => {
    if (!condoId) {
      // condoId ausente: volta para o dashboard
      toast.show({ type: "error", msg: "Selecione um condomínio para ver os moradores." });
      nav("/app", { replace: true });
      return;
    }
    loadResidents();
    loadUnits();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [condoId, page, q]);

  /** Ordenação client-side */
  const residents = useMemo(() => {
    const arr = [...residentsRaw];
    const cmp = (a: Resident, b: Resident) => {
      let r = 0;
      if (sortBy === "name") {
        r = a.name.localeCompare(b.name, "pt-BR");
      } else {
        const ua = unitLabel(a.unit);
        const ub = unitLabel(b.unit);
        r = ua.localeCompare(ub, "pt-BR", { numeric: true });
      }
      return sortDir === "asc" ? r : -r;
    };
    return arr.sort(cmp);
  }, [residentsRaw, sortBy, sortDir]);

  /** Criar/editar morador */
  async function submitModal(e: React.FormEvent) {
    e.preventDefault();
    try {
      if (editing) {
        await api.put(`/residents/${editing.id}`, {
          name: form.name.trim(),
          email: form.email.trim(),
          phone: form.phone.trim(),
          // condoId não muda no update
          unitId: form.unitId ? form.unitId : null,
        });
        toast.show({ type: "success", msg: "Morador atualizado" });
      } else {
        await api.post("/residents", {
          name: form.name.trim(),
          email: form.email.trim(),
          phone: form.phone.trim(),
          condoId,
          unitId: form.unitId ? form.unitId : null,
        });
        toast.show({ type: "success", msg: "Morador criado" });
        setPage(1);
      }
      setOpenModal(false);
      setEditing(null);
      loadResidents();
    } catch (err: any) {
      const msg = err?.response?.data?.error ?? err?.response?.message ?? "Erro ao salvar morador";
      toast.show({ type: "error", msg });
    }
  }

  /** Excluir */
  async function onDelete(id: string) {
    if (!confirm("Excluir morador?")) return;
    try {
      await api.delete(`/residents/${id}`);
      toast.show({ type: "success", msg: "Morador excluído" });
      loadResidents();
    } catch {
      toast.show({ type: "error", msg: "Erro ao excluir morador" });
    }
  }

  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="space-y-6">
      {/* Cabeçalho e navegação */}
      <div className="flex items-center gap-3">
        <button className="text-slate-600 hover:underline" onClick={() => nav(-1)}>
          ← Voltar
        </button>
        <h1 className="text-2xl font-semibold">Moradores</h1>
      </div>

      {/* Busca + Ordenação + Botão novo */}
      <div className="flex flex-wrap items-center gap-3">
        <input
          value={q}
          onChange={(e) => {
            setPage(1);
            setQ(e.target.value);
          }}
          className="border rounded-lg p-2 w-64"
          placeholder="Buscar (nome/email/telefone)"
        />

        <div className="flex items-center gap-2">
          <label className="text-sm text-slate-600">Ordenar por</label>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as SortBy)}
            className="border rounded-lg p-2"
          >
            <option value="name">Nome</option>
            <option value="unit">Unidade</option>
          </select>

          <select
            value={sortDir}
            onChange={(e) => setSortDir(e.target.value as SortDir)}
            className="border rounded-lg p-2"
          >
            <option value="asc">Ascendente</option>
            <option value="desc">Descendente</option>
          </select>
        </div>

        {can("create") && (
          <button onClick={openCreate} className="ml-auto bg-slate-900 text-white rounded-lg px-4 py-2">
            Novo morador
          </button>
        )}
      </div>

      {/* Lista */}
      {loading ? (
        <div className="text-slate-500">Carregando…</div>
      ) : residents.length === 0 ? (
        <div className="text-slate-500">Nenhum morador encontrado.</div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {residents.map((r) => (
              <Card key={r.id}>
                <div className="font-medium">{r.name}</div>
                <div className="text-sm text-slate-600">
                  {r.email} • {r.phone}
                </div>
                <div className="text-sm mt-1">{unitLabel(r.unit)}</div>

                <div className="mt-2 flex gap-4 text-sm">
                  {can("edit") && (
                    <button onClick={() => openEdit(r)} className="text-blue-700">
                      Editar
                    </button>
                  )}
                  {can("delete") && (
                    <button onClick={() => onDelete(r.id)} className="text-rose-600">
                      Excluir
                    </button>
                  )}
                </div>
              </Card>
            ))}
          </div>

          {/* Paginação */}
          <div className="flex items-center gap-3">
            <button
              disabled={page <= 1}
              onClick={() => setPage((p) => p - 1)}
              className="px-3 py-1 border rounded disabled:opacity-50"
            >
              Anterior
            </button>
            <span className="text-sm">
              Página {page} de {pageCount}
            </span>
            <button
              disabled={page >= pageCount}
              onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1 border rounded disabled:opacity-50"
            >
              Próxima
            </button>
          </div>
        </>
      )}

      {/* Modal Criar/Editar */}
      <Modal
        open={openModal}
        onClose={() => setOpenModal(false)}
        title={editing ? "Editar morador" : "Novo morador"}
        footer={
          <>
            <button onClick={() => setOpenModal(false)} className="px-4 py-2 rounded border">
              Cancelar
            </button>
            <button onClick={(e) => submitModal(e as any)} className="px-4 py-2 rounded bg-slate-900 text-white">
              {editing ? "Salvar" : "Adicionar"}
            </button>
          </>
        }
      >
        <form onSubmit={submitModal} className="space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-sm text-slate-600">Nome</label>
              <input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="border rounded-lg p-2 w-full"
                placeholder="Ex: João Silva"
              />
            </div>
            <div>
              <label className="text-sm text-slate-600">Email</label>
              <input
                required
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="border rounded-lg p-2 w-full"
                placeholder="email@ex.com"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-sm text-slate-600">Telefone</label>
              <input
                required
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                className="border rounded-lg p-2 w-full"
                placeholder="(11) 99999-9999"
              />
            </div>
            <div>
              <label className="text-sm text-slate-600">Unidade (opcional)</label>
              <select
                value={form.unitId}
                onChange={(e) => setForm({ ...form, unitId: e.target.value })}
                className="border rounded-lg p-2 w-full"
              >
                <option value="">— Sem unidade —</option>
                {units.map((u) => (
                  <option key={u.id} value={u.id}>
                    {unitLabel(u)}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
}
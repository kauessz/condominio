// Página de Unidades com:
// - Busca + Paginação
// - Ordenação client-side (número ou bloco, asc/desc)
// - Modal para Criar/Editar (formulário completo)
// - RBAC + toasts

import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import Card from "../components/Card";
import { can } from "../lib/auth";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";

type Unit = { id: string; number: string; block: string | null };

type SortBy = "number" | "block";
type SortDir = "asc" | "desc";

export default function Units() {
  const { id: idFromParam } = useParams();
  const [sp] = useSearchParams();
  const condoIdFromQuery = sp.get("condoId") ?? "";
  const condoId = useMemo(
    () => idFromParam || condoIdFromQuery || "",
    [idFromParam, condoIdFromQuery]
  );
  const nav = useNavigate();
  const toast = useToast();

  useEffect(() => {
    if (!condoId) {
      toast.show({ type: "error", msg: "Selecione um condomínio." });
      nav("/app", { replace: true });
    }
  }, [condoId]);

  // dados
  const [unitsRaw, setUnitsRaw] = useState<Unit[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);           // UI 1-based
  const [pageSize] = useState(8);
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(true);

  // ordenação
  const [sortBy, setSortBy] = useState<SortBy>("number");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  // modal (criar/editar)
  const [openModal, setOpenModal] = useState(false);
  const [editing, setEditing] = useState<null | Unit>(null);
  const [form, setForm] = useState({ number: "", block: "" });

  function openCreate() {
    setEditing(null);
    setForm({ number: "", block: "" });
    setOpenModal(true);
  }
  function openEdit(u: Unit) {
    setEditing(u);
    setForm({ number: u.number, block: u.block ?? "" });
    setOpenModal(true);
  }

  // carregar da API
  async function load() {
    if (!condoId) return;
    try {
      setLoading(true);

      // manda paginação zero-based para Spring; envia "size" e "pageSize" por compat
      const r = await api.get(`/units`, {
        params: {
          condoId,
          q,
          page: Math.max(0, page - 1),
          size: pageSize,
          pageSize, // compat
        },
      });

      const data = r.data;
      // Suporta 3 formatos: array, {items,total}, {content,totalElements}
      const items: Unit[] = Array.isArray(data)
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

      setUnitsRaw(items);
      setTotal(tot);
    } catch (err: any) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        err?.message ||
        "Erro ao carregar unidades";
      toast.show({ type: "error", msg });
      setUnitsRaw([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [condoId, page, q]);

  // ordenação client-side
  const units = useMemo(() => {
    const arr = [...unitsRaw];
    const cmp = (a: Unit, b: Unit) => {
      if (sortBy === "number") {
        // tenta comparar número numericamente (ex: "201")
        const na = parseInt(a.number, 10);
        const nb = parseInt(b.number, 10);
        const r =
          Number.isFinite(na) && Number.isFinite(nb)
            ? na - nb
            : a.number.localeCompare(b.number, "pt-BR", { numeric: true });
        return sortDir === "asc" ? r : -r;
      } else {
        const A = (a.block ?? "").toLowerCase();
        const B = (b.block ?? "").toLowerCase();
        const r = A.localeCompare(B, "pt-BR", { numeric: true });
        return sortDir === "asc" ? r : -r;
      }
    };
    return arr.sort(cmp);
  }, [unitsRaw, sortBy, sortDir]);

  // salvar (criar/editar) via modal
  async function submitModal(e: React.FormEvent) {
    e.preventDefault();
    if (!form.number.trim()) return;
    try {
      if (editing) {
        await api.put(`/units/${editing.id}`, {
          number: form.number.trim(),
          block: form.block.trim() || undefined,
        });
        toast.show({ type: "success", msg: "Unidade atualizada" });
      } else {
        await api.post("/units", {
          number: form.number.trim(),
          block: (form.block ?? "").trim(), // envia '' e o banco aceita
          condoId,
        });
        toast.show({ type: "success", msg: "Unidade criada" });
        setPage(1);
      }
      setOpenModal(false);
      setEditing(null);
      load();
    } catch (err: any) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        "Erro ao salvar unidade";
      toast.show({ type: "error", msg });
    }
  }

  // excluir
  async function onDelete(id: string) {
    if (!confirm("Excluir unidade?")) return;
    try {
      await api.delete(`/units/${id}`);
      toast.show({ type: "success", msg: "Unidade excluída" });
      load();
    } catch (err: any) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        "Erro ao excluir unidade";
      toast.show({ type: "error", msg });
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
        <h1 className="text-2xl font-semibold">Unidades</h1>

        <button
          onClick={() => nav(`/app/residents?condoId=${condoId}`)}
          className="ml-auto text-sm text-blue-700 hover:underline"
        >
          Ir para Moradores →
        </button>
      </div>

      {/* Filtros: busca + ordenação */}
      <div className="flex flex-wrap items-center gap-3">
        <input
          value={q}
          onChange={(e) => {
            setPage(1);
            setQ(e.target.value);
          }}
          className="border rounded-lg p-2 w-64"
          placeholder="Buscar (número/bloco)"
        />

        <div className="flex items-center gap-2">
          <label className="text-sm text-slate-600">Ordenar por</label>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as SortBy)}
            className="border rounded-lg p-2"
          >
            <option value="number">Número</option>
            <option value="block">Bloco</option>
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
          <button
            onClick={openCreate}
            className="ml-auto bg-slate-900 text-white rounded-lg px-4 py-2"
          >
            Nova unidade
          </button>
        )}
      </div>

      {/* Lista */}
      {loading ? (
        <div className="text-slate-500">Carregando…</div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {units.map((u) => (
              <Card key={u.id}>
                <div className="font-medium">
                  Unidade {u.number} {u.block ? `- Bloco ${u.block}` : ""}
                </div>
                <div className="mt-2 flex gap-4 text-sm">
                  {can("edit") && (
                    <button onClick={() => openEdit(u)} className="text-blue-700">
                      Editar
                    </button>
                  )}
                  {can("delete") && (
                    <button onClick={() => onDelete(u.id)} className="text-red-600">
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

      {/* Modal criar/editar */}
      <Modal
        open={openModal}
        onClose={() => setOpenModal(false)}
        title={editing ? "Editar unidade" : "Nova unidade"}
        footer={
          <>
            <button onClick={() => setOpenModal(false)} className="px-4 py-2 rounded border">
              Cancelar
            </button>
            <button
              onClick={(e) => submitModal(e as any)}
              className="px-4 py-2 rounded bg-slate-900 text-white"
            >
              {editing ? "Salvar" : "Adicionar"}
            </button>
          </>
        }
      >
        <form onSubmit={submitModal} className="space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-sm text-slate-600">Número</label>
              <input
                required
                value={form.number}
                onChange={(e) => setForm({ ...form, number: e.target.value })}
                className="border rounded-lg p-2 w-full"
                placeholder="Ex: 201"
              />
            </div>
            <div>
              <label className="text-sm text-slate-600">Bloco</label>
              <input
                value={form.block}
                onChange={(e) => setForm({ ...form, block: e.target.value })}
                className="border rounded-lg p-2 w-full"
                placeholder="Ex: B"
              />
            </div>
          </div>
          {/* botão submit fica no footer da modal */}
        </form>
      </Modal>
    </div>
  );
}
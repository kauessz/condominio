import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import Modal from "../components/Modal";
import { useToast } from "../components/Toast";
import { can } from "../lib/auth";

type CondoCard = {
  id: string;
  name: string;
  cnpj: string;
  _count?: { units: number; residents: number };
  createdAt?: string;
};

type PageResp<T> = { items: T[]; total: number; page: number; pageSize: number } | T[];

export default function Dashboard() {
  const toast = useToast();

  const [condos, setCondos] = useState<CondoCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // paginação simples (backend já aceita)
  const [page, setPage] = useState(1);
  const [pageSize] = useState(50);
  const [total, setTotal] = useState(0);

  // modal criar/editar
  const [openModal, setOpenModal] = useState(false);
  const [editing, setEditing] = useState<CondoCard | null>(null);
  const [form, setForm] = useState({ name: "", cnpj: "" });

  const pageCount = useMemo(() => Math.max(1, Math.ceil(total / pageSize)), [total, pageSize]);

  async function load() {
    try {
      setLoading(true);

      // a API agora retorna { items, total, page, pageSize } (mas mantemos compat)
      const r = await api.get<PageResp<CondoCard>>("/condominiums", {
        params: { page, pageSize },
      });

      const list: CondoCard[] = Array.isArray(r.data) ? (r.data as CondoCard[]) : (r.data as any)?.items ?? [];
      const tot = Array.isArray(r.data) ? list.length : (r.data as any)?.total ?? list.length;

      setCondos(list);
      setTotal(tot);
      setError(null);
    } catch (e: any) {
      setError(e?.response?.data?.error ?? e?.message ?? "Erro ao carregar condomínios");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  function openCreate() {
    setEditing(null);
    setForm({ name: "", cnpj: "" });
    setOpenModal(true);
  }
  function openEdit(c: CondoCard) {
    setEditing(c);
    setForm({ name: c.name, cnpj: c.cnpj });
    setOpenModal(true);
  }

  async function submitModal(e: React.FormEvent) {
    e.preventDefault();
    try {
      if (!form.name.trim() || !form.cnpj.trim()) return;

      if (editing) {
        await api.put(`/condominiums/${editing.id}`, {
          name: form.name.trim(),
          cnpj: form.cnpj.trim(),
        });
        toast.show({ type: "success", msg: "Condomínio atualizado" });
      } else {
        await api.post(`/condominiums`, {
          name: form.name.trim(),
          cnpj: form.cnpj.trim(),
        });
        toast.show({ type: "success", msg: "Condomínio criado" });
        setPage(1);
      }

      setOpenModal(false);
      setEditing(null);
      load();
    } catch (err: any) {
      const msg = err?.response?.data?.error ?? "Erro ao salvar condomínio";
      toast.show({ type: "error", msg });
    }
  }

  async function onDelete(id: string) {
    if (!confirm("Excluir condomínio? Todas as unidades e moradores associados poderão ser impactados.")) return;
    try {
      await api.delete(`/condominiums/${id}`);
      toast.show({ type: "success", msg: "Condomínio excluído" });
      load();
    } catch (err) {
      toast.show({ type: "error", msg: "Erro ao excluir condomínio" });
    }
  }

  if (loading) return <div className="p-6">Carregando…</div>;
  if (error) return <div className="p-6 text-red-600">{error}</div>;

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">Dashboard</h1>

        {can("create") && (
          <button onClick={openCreate} className="ml-auto bg-slate-900 text-white rounded-lg px-4 py-2">
            Novo condomínio
          </button>
        )}
      </div>

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {condos.map((c) => (
          <div key={c.id} className="bg-white rounded-xl shadow-sm p-4 hover:shadow-md transition">
            <Link to={`/app/units?condoId=${c.id}`} className="block">
              <div className="font-medium">{c.name}</div>
              <div className="text-sm text-slate-500">CNPJ {c.cnpj}</div>
              <div className="text-sm text-slate-600 mt-1">
                Unidades: {c._count?.units ?? 0} | Moradores: {c._count?.residents ?? 0}
              </div>
            </Link>

            <div className="mt-3 flex gap-4 text-sm">
              {can("edit") && (
                <button onClick={() => openEdit(c)} className="text-blue-700">
                  Editar
                </button>
              )}
              {can("delete") && (
                <button onClick={() => onDelete(c.id)} className="text-rose-600">
                  Excluir
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Paginação simples */}
      <div className="flex items-center gap-3">
        <button
          disabled={page <= 1}
          onClick={() => setPage((p) => p - 1)}
          className="px-3 py-1 border rounded disabled:opacity-50"
        >
          Anterior
        </button>
        <span className="text-sm">Página {page} de {pageCount}</span>
        <button
          disabled={page >= pageCount}
          onClick={() => setPage((p) => p + 1)}
          className="px-3 py-1 border rounded disabled:opacity-50"
        >
          Próxima
        </button>
      </div>

      {/* Modal criar/editar condomínio */}
      <Modal
        open={openModal}
        onClose={() => setOpenModal(false)}
        title={editing ? "Editar condomínio" : "Novo condomínio"}
        footer={
          <>
            <button onClick={() => setOpenModal(false)} className="px-4 py-2 rounded border">Cancelar</button>
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
                placeholder="Ex: Condomínio Jardim"
              />
            </div>
            <div>
              <label className="text-sm text-slate-600">CNPJ</label>
              <input
                required
                value={form.cnpj}
                onChange={(e) => setForm({ ...form, cnpj: e.target.value })}
                className="border rounded-lg p-2 w-full"
                placeholder="00.000.000/0000-00"
              />
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
}
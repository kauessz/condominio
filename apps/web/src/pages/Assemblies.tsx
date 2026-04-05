import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";
import { canAccessModule, getUser } from "../lib/auth";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type Assembly = {
  id: number;
  condominiumId: number;
  condominiumName?: string;
  title: string;
  description?: string;
  scheduledAt: string;
  location?: string;
  status: "SCHEDULED" | "OPEN" | "CLOSED" | "CANCELLED";
  agendaItemCount?: number;
  canVote?: boolean | null;
  alreadyVoted?: boolean | null;
  voteStatus?: string | null;
};

type Condo = {
  id: number;
  name: string;
};

type AgendaItem = {
  id: number;
  assemblyId: number;
  title: string;
  description?: string;
  requiresVote: boolean;
  sortOrder: number;
};

type VoteResult = {
  itemId: number;
  itemTitle: string;
  totalVotes: number;
  totalUnits: number;
  yes: number;
  no: number;
  abstain: number;
  quorumPct: number;
};

const STATUS_LABELS: Record<string, string> = {
  SCHEDULED: "Agendada",
  OPEN: "Em andamento",
  CLOSED: "Encerrada",
  CANCELLED: "Cancelada",
};

const STATUS_COLORS: Record<string, string> = {
  SCHEDULED: "bg-amber-100 text-amber-700",
  OPEN: "bg-emerald-100 text-emerald-700",
  CLOSED: "bg-slate-100 text-slate-600",
  CANCELLED: "bg-rose-100 text-rose-700",
};

const VOTE_STATUS_LABELS: Record<string, string> = {
  CAN_VOTE: "Sua unidade pode votar",
  ALREADY_VOTED: "Sua unidade já votou",
  CLOSED: "Votação encerrada",
  NOT_OPEN: "Votação ainda não liberada",
  BLOCKED_NO_UNIT: "Votação bloqueada: usuário sem unidade vinculada",
  NO_VOTABLE_ITEMS: "Assembleia sem itens de votação",
};

function VoteBar({ label, count, total, color }: { label: string; count: number; total: number; color: string }) {
  const pct = total > 0 ? (count / total) * 100 : 0;
  return (
    <div>
      <div className="flex justify-between text-xs text-slate-600 mb-1">
        <span>{label}</span>
        <span>{count} ({pct.toFixed(0)}%)</span>
      </div>
      <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
        <div className={`h-full ${color} rounded-full transition-all`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

export default function AssembliesPage() {
  const toast = useToast();
  const currentUser = getUser();
  const canAccess = canAccessModule("assemblies");
  const canManage = ["SUPERUSER", "ADMIN", "SINDICO"].includes(currentUser?.role ?? "");
  const isMorador = currentUser?.role === "MORADOR";
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);

  const [assemblies, setAssemblies] = useState<Assembly[]>([]);
  const [condos, setCondos] = useState<Condo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedAssembly, setSelectedAssembly] = useState<Assembly | null>(null);
  const [agenda, setAgenda] = useState<AgendaItem[]>([]);
  const [voteResults, setVoteResults] = useState<Map<number, VoteResult>>(new Map());
  const [showDetail, setShowDetail] = useState(false);
  const [voting, setVoting] = useState<Map<number, string>>(new Map());

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ title: "", description: "", scheduledAt: "", location: "" });

  const [showAgendaModal, setShowAgendaModal] = useState(false);
  const [agendaForm, setAgendaForm] = useState({ title: "", description: "", requiresVote: true });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isSuperuser) return;
    api.get("/condominiums", { params: { pageSize: 100 } })
      .then((res) => {
        const raw = res.data;
        const list: Condo[] = Array.isArray(raw.content)
          ? raw.content
          : Array.isArray(raw.items)
            ? raw.items
            : Array.isArray(raw)
              ? raw
              : [];
        setCondos(list);
      })
      .catch(() => setCondos([]));
  }, [isSuperuser]);

  async function load() {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get("/api/assemblies", {
        params: {
          size: 20,
          condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined,
        },
      });
      setAssemblies(res.data.content ?? []);
    } catch (err: any) {
      const message = err?.response?.status === 403
        ? "Você não tem permissão para acessar assembleias."
        : "Falha ao carregar assembleias";
      setError(message);
      toast.show({ type: "error", msg: message });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { if (canAccess) load(); /* eslint-disable-next-line */ }, [selectedCondominiumId, canAccess]);

  async function openDetail(assembly: Assembly) {
    setSelectedAssembly(assembly);
    setVoteResults(new Map());
    try {
      const res = await api.get(`/api/assemblies/${assembly.id}/agenda`);
      const items: AgendaItem[] = res.data ?? [];
      setAgenda(items);
      if (assembly.status !== "SCHEDULED") {
        const results = await Promise.all(
          items.filter((i) => i.requiresVote).map(async (item) => {
            const voteRes = await api.get(`/api/assemblies/${assembly.id}/agenda/${item.id}/votes`);
            return [item.id, voteRes.data] as [number, VoteResult];
          })
        );
        setVoteResults(new Map(results));
      }
    } catch {
      setAgenda([]);
    }
    setShowDetail(true);
  }

  async function handleCreate() {
    if (isSuperuser && !selectedCondominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para criar a assembleia." });
      return;
    }
    try {
      setSaving(true);
      await api.post("/api/assemblies", {
        condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined,
        title: form.title,
        description: form.description || null,
        scheduledAt: new Date(form.scheduledAt).toISOString(),
        location: form.location || null,
      });
      toast.show({ type: "success", msg: "Assembleia criada!" });
      setShowCreate(false);
      setForm({ title: "", description: "", scheduledAt: "", location: "" });
      load();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao criar assembleia" });
    } finally {
      setSaving(false);
    }
  }

  async function handleAddAgenda() {
    if (!selectedAssembly) return;
    try {
      setSaving(true);
      await api.post(`/api/assemblies/${selectedAssembly.id}/agenda`, agendaForm);
      toast.show({ type: "success", msg: "Item adicionado à pauta!" });
      setShowAgendaModal(false);
      setAgendaForm({ title: "", description: "", requiresVote: true });
      openDetail(selectedAssembly);
      load();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao adicionar pauta" });
    } finally {
      setSaving(false);
    }
  }

  async function handleOpenClose(id: number, action: "open" | "close") {
    try {
      await api.patch(`/api/assemblies/${id}/${action}`);
      toast.show({ type: "success", msg: action === "open" ? "Assembleia aberta!" : "Assembleia encerrada!" });
      load();
      if (selectedAssembly?.id === id) {
        const res = await api.get(`/api/assemblies/${id}`);
        setSelectedAssembly({ ...selectedAssembly, ...res.data });
      }
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro na operação" });
    }
  }

  async function handleVote(assemblyId: number, itemId: number, vote: string) {
    setVoting((prev) => new Map(prev).set(itemId, "saving"));
    try {
      await api.post(`/api/assemblies/${assemblyId}/agenda/${itemId}/vote`, { vote });
      toast.show({ type: "success", msg: "Voto registrado!" });
      const res = await api.get(`/api/assemblies/${assemblyId}/agenda/${itemId}/votes`);
      setVoteResults((prev) => new Map(prev).set(itemId, res.data));
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao votar" });
    } finally {
      setVoting((prev) => {
        const map = new Map(prev);
        map.delete(itemId);
        return map;
      });
    }
  }

  if (!canAccess) {
    return <Navigate to="/app/dashboard" replace />;
  }

  return (
    <div className="p-6 max-w-5xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            Assembleias
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">Reuniões de condôminos e votações</p>
        </div>
        {canManage && (
          <button
            onClick={() => setShowCreate(true)}
            className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors shadow-sm"
          >
            + Nova assembleia
          </button>
        )}
      </div>

      {isSuperuser && (
        <div className="mb-5 max-w-sm">
          <label className="block text-sm font-medium text-slate-700 mb-1.5">Condomínio</label>
          <select
            value={selectedCondominiumId}
            onChange={(e) => setSelectedCondominiumId(e.target.value)}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
          >
            <option value="">Todos os condomínios</option>
            {condos.map((condo) => (
              <option key={condo.id} value={String(condo.id)}>{condo.name}</option>
            ))}
          </select>
        </div>
      )}

      {loading ? (
        <div className="space-y-3">{[1, 2].map((i) => <div key={i} className="bg-white rounded-xl border border-slate-100 h-24 animate-pulse" />)}</div>
      ) : error ? (
        <div className="bg-white rounded-xl border border-rose-200 p-10 text-center">
          <p className="text-sm font-medium text-rose-700">Nao foi possivel carregar as assembleias.</p>
          <p className="text-sm text-rose-500 mt-1">{error}</p>
        </div>
      ) : assemblies.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">
          Nenhuma assembleia cadastrada.
        </div>
      ) : (
        <div className="space-y-3">
          {assemblies.map((assembly) => (
            <div
              key={assembly.id}
              onClick={() => openDetail(assembly)}
              className="bg-white rounded-xl border border-slate-100 shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <span className="font-medium text-slate-900 text-sm">{assembly.title}</span>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[assembly.status]}`}>
                      {STATUS_LABELS[assembly.status]}
                    </span>
                  </div>
                  <p className="text-xs text-slate-500">
                    {new Date(assembly.scheduledAt).toLocaleString("pt-BR")}
                    {assembly.location && ` • ${assembly.location}`}
                  </p>
                  <div className="mt-1 flex items-center gap-3 flex-wrap text-xs text-slate-400">
                    <span>{assembly.condominiumName ?? `Condomínio #${assembly.condominiumId}`}</span>
                    <span>{assembly.agendaItemCount ?? 0} item(ns) de pauta</span>
                  </div>
                  {isMorador && assembly.voteStatus && (
                    <p className={`text-xs mt-2 ${assembly.canVote ? "text-emerald-600" : "text-slate-500"}`}>
                      {VOTE_STATUS_LABELS[assembly.voteStatus] ?? assembly.voteStatus}
                    </p>
                  )}
                  {assembly.description && (
                    <p className="text-xs text-slate-500 mt-2 line-clamp-2">{assembly.description}</p>
                  )}
                </div>
                {canManage && (
                  <div className="flex gap-1.5 flex-shrink-0" onClick={(e) => e.stopPropagation()}>
                    {assembly.status === "SCHEDULED" && (
                      <button
                        onClick={() => handleOpenClose(assembly.id, "open")}
                        className="text-xs px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 font-medium"
                      >
                        Abrir
                      </button>
                    )}
                    {assembly.status === "OPEN" && (
                      <button
                        onClick={() => handleOpenClose(assembly.id, "close")}
                        className="text-xs px-2.5 py-1 rounded-lg bg-slate-100 text-slate-600 hover:bg-slate-200 font-medium"
                      >
                        Encerrar
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        title="Nova Assembleia"
        footer={
          <>
            <button onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button>
            <button
              onClick={handleCreate}
              disabled={saving || !form.title || !form.scheduledAt}
              className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50"
            >
              {saving ? "Salvando…" : "Criar"}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          {isSuperuser && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Condomínio *</label>
              <select
                value={selectedCondominiumId}
                onChange={(e) => setSelectedCondominiumId(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
              >
                <option value="">Selecione um condomínio…</option>
                {condos.map((condo) => (
                  <option key={condo.id} value={String(condo.id)}>{condo.name}</option>
                ))}
              </select>
            </div>
          )}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Título *</label>
            <input
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder="Ex: Assembleia Geral Ordinária 2026"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Data e hora *</label>
              <input
                type="datetime-local"
                value={form.scheduledAt}
                onChange={(e) => setForm({ ...form, scheduledAt: e.target.value })}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Local</label>
              <input
                value={form.location}
                onChange={(e) => setForm({ ...form, location: e.target.value })}
                placeholder="Ex: Salão de reuniões"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Descrição</label>
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={3}
              placeholder="Pauta resumida da assembleia…"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            />
          </div>
        </div>
      </Modal>

      <Modal open={showDetail} onClose={() => setShowDetail(false)} title={selectedAssembly?.title ?? ""} size="lg">
        {selectedAssembly && (
          <div className="space-y-5">
            <div className="flex items-center gap-2 flex-wrap">
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[selectedAssembly.status]}`}>
                {STATUS_LABELS[selectedAssembly.status]}
              </span>
              <span className="text-xs text-slate-500">
                {new Date(selectedAssembly.scheduledAt).toLocaleString("pt-BR")}
                {selectedAssembly.location && ` • ${selectedAssembly.location}`}
              </span>
              <span className="text-xs text-slate-400">
                {selectedAssembly.condominiumName ?? `Condomínio #${selectedAssembly.condominiumId}`}
              </span>
            </div>

            {selectedAssembly.description && (
              <p className="text-sm text-slate-600">{selectedAssembly.description}</p>
            )}

            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-700">Pauta</h3>
              {canManage && selectedAssembly.status !== "CLOSED" && selectedAssembly.status !== "CANCELLED" && (
                <button
                  onClick={() => setShowAgendaModal(true)}
                  className="text-xs text-indigo-600 hover:text-indigo-700 font-medium"
                >
                  + Adicionar item
                </button>
              )}
            </div>

            {agenda.length === 0 ? (
              <p className="text-sm text-slate-400 text-center py-4">Nenhum item na pauta.</p>
            ) : (
              <div className="space-y-4">
                {agenda.map((item, index) => {
                  const result = voteResults.get(item.id);
                  const isVoting = voting.has(item.id);
                  const totalVotes = result ? result.yes + result.no + result.abstain : 0;

                  return (
                    <div key={item.id} className="border border-slate-100 rounded-xl p-4">
                      <div className="flex items-center gap-2 mb-2">
                        <span className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-600 text-xs flex items-center justify-center font-bold flex-shrink-0">
                          {index + 1}
                        </span>
                        <p className="font-medium text-slate-900 text-sm">{item.title}</p>
                        {!item.requiresVote && (
                          <span className="text-xs bg-slate-100 text-slate-500 px-1.5 rounded">Informativo</span>
                        )}
                      </div>
                      {item.description && <p className="text-xs text-slate-500 mb-3 ml-7">{item.description}</p>}

                      {item.requiresVote && selectedAssembly.status === "OPEN" && isMorador && selectedAssembly.canVote && (
                        <div className="flex gap-2 ml-7 mb-3">
                          {(["YES", "NO", "ABSTAIN"] as const).map((vote) => (
                            <button
                              key={vote}
                              disabled={isVoting}
                              onClick={() => handleVote(selectedAssembly.id, item.id, vote)}
                              className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors disabled:opacity-50 ${
                                vote === "YES" ? "bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
                                  : vote === "NO" ? "bg-rose-50 text-rose-700 hover:bg-rose-100"
                                    : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                              }`}
                            >
                              {vote === "YES" ? "✓ Sim" : vote === "NO" ? "✗ Não" : "— Abstenção"}
                            </button>
                          ))}
                        </div>
                      )}

                      {item.requiresVote && isMorador && !selectedAssembly.canVote && !result && (
                        <div className="ml-7 mb-3 text-xs text-slate-500">
                          {VOTE_STATUS_LABELS[selectedAssembly.voteStatus ?? ""] ?? "Votação indisponível."}
                        </div>
                      )}

                      {result && (
                        <div className="ml-7 space-y-2">
                          <div className="flex items-center justify-between text-xs text-slate-500 mb-1">
                            <span>{totalVotes} voto(s)</span>
                            <span>Quórum: {result.quorumPct.toFixed(0)}% ({totalVotes}/{result.totalUnits})</span>
                          </div>
                          <VoteBar label="Sim" count={result.yes} total={totalVotes} color="bg-emerald-500" />
                          <VoteBar label="Não" count={result.no} total={totalVotes} color="bg-rose-500" />
                          <VoteBar label="Abstenção" count={result.abstain} total={totalVotes} color="bg-slate-400" />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </Modal>

      <Modal
        open={showAgendaModal}
        onClose={() => setShowAgendaModal(false)}
        title="Adicionar Item de Pauta"
        footer={
          <>
            <button onClick={() => setShowAgendaModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button>
            <button
              onClick={handleAddAgenda}
              disabled={saving || !agendaForm.title}
              className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50"
            >
              {saving ? "Salvando…" : "Adicionar"}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Título *</label>
            <input
              value={agendaForm.title}
              onChange={(e) => setAgendaForm({ ...agendaForm, title: e.target.value })}
              placeholder="Ex: Aprovação do orçamento 2026"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Descrição</label>
            <textarea
              value={agendaForm.description}
              onChange={(e) => setAgendaForm({ ...agendaForm, description: e.target.value })}
              rows={3}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            />
          </div>
          <label className="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={agendaForm.requiresVote}
              onChange={(e) => setAgendaForm({ ...agendaForm, requiresVote: e.target.checked })}
              className="w-4 h-4 rounded text-indigo-600"
            />
            <span className="text-sm text-slate-700">Requer votação das unidades</span>
          </label>
        </div>
      </Modal>
    </div>
  );
}

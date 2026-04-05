import { useState, useEffect, useCallback } from "react";
import api from "../lib/api";
import { useToast } from "../components/Toast";

type Request = {
  id: number;
  condominiumName: string;
  cnpj?: string;
  address?: string;
  requesterName: string;
  requesterEmail: string;
  requesterPhone?: string;
  requesterRole?: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  rejectionReason?: string;
  createdAt: string;
};

type TabStatus = "PENDING" | "APPROVED" | "REJECTED";

/**
 * Painel de solicitações de onboarding — visível apenas para SUPERUSER.
 * Rota: /app/onboarding
 */
export default function Onboarding() {
  const toast = useToast();
  const [tab, setTab] = useState<TabStatus>("PENDING");
  const [items, setItems] = useState<Request[]>([]);
  const [loading, setLoading] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);

  // Modal de rejeição
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [listRes, countRes] = await Promise.all([
        api.get<any>("/api/onboarding/requests", { params: { status: tab, page: 0, size: 50 } }),
        api.get<any>("/api/onboarding/requests/count"),
      ]);
      const data = listRes.data;
      setItems(Array.isArray(data?.content) ? data.content : Array.isArray(data) ? data : []);
      setPendingCount(countRes.data?.pending ?? 0);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  async function handleApprove(id: number) {
    if (!window.confirm("Aprovar esta solicitação? Isso criará o condomínio e um usuário ADMIN.")) return;
    setSaving(true);
    try {
      await api.post(`/api/onboarding/requests/${id}/approve`);
      toast.show({ type: "success", msg: "Solicitação aprovada! Condomínio criado com sucesso." });
      load();
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.message || "Erro ao aprovar solicitação.",
      });
    } finally {
      setSaving(false);
    }
  }

  async function handleReject() {
    if (!rejectId || !rejectReason.trim()) return;
    setSaving(true);
    try {
      await api.post(`/api/onboarding/requests/${rejectId}/reject`, { reason: rejectReason });
      toast.show({ type: "success", msg: "Solicitação rejeitada." });
      setRejectId(null);
      setRejectReason("");
      load();
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.message || "Erro ao rejeitar solicitação.",
      });
    } finally {
      setSaving(false);
    }
  }

  const statusBadge = (status: string) => {
    const map: Record<string, string> = {
      PENDING: "bg-yellow-100 text-yellow-700",
      APPROVED: "bg-green-100 text-green-700",
      REJECTED: "bg-red-100 text-red-700",
    };
    const label: Record<string, string> = {
      PENDING: "Pendente",
      APPROVED: "Aprovado",
      REJECTED: "Rejeitado",
    };
    return (
      <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${map[status] ?? ""}`}>
        {label[status] ?? status}
      </span>
    );
  };

  const tabs: { key: TabStatus; label: string }[] = [
    { key: "PENDING",  label: `Pendentes${pendingCount > 0 ? ` (${pendingCount})` : ""}` },
    { key: "APPROVED", label: "Aprovadas" },
    { key: "REJECTED", label: "Rejeitadas" },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">Solicitações de Cadastro</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Revise e gerencie as solicitações de novos condomínios.
          </p>
        </div>
        {pendingCount > 0 && (
          <span className="bg-yellow-100 text-yellow-700 text-sm font-semibold px-3 py-1 rounded-full">
            {pendingCount} pendente{pendingCount > 1 ? "s" : ""}
          </span>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-100">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px ${
              tab === t.key
                ? "border-blue-600 text-blue-600"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Lista */}
      {loading ? (
        <p className="text-sm text-slate-400 py-8 text-center">Carregando...</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-slate-400 py-8 text-center">
          Nenhuma solicitação {tab === "PENDING" ? "pendente" : tab === "APPROVED" ? "aprovada" : "rejeitada"}.
        </p>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div
              key={item.id}
              className="bg-white border border-slate-100 rounded-xl p-5 space-y-3"
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-semibold text-slate-800">{item.condominiumName}</h3>
                    {statusBadge(item.status)}
                  </div>
                  {item.cnpj && (
                    <p className="text-xs text-slate-400 mt-0.5">CNPJ: {item.cnpj}</p>
                  )}
                  {item.address && (
                    <p className="text-xs text-slate-400">{item.address}</p>
                  )}
                </div>
                <p className="text-xs text-slate-400 whitespace-nowrap">
                  {new Date(item.createdAt).toLocaleDateString("pt-BR")}
                </p>
              </div>

              <div className="border-t border-slate-50 pt-3 grid grid-cols-2 gap-2 text-sm">
                <div>
                  <span className="text-slate-400 text-xs">Solicitante</span>
                  <p className="font-medium text-slate-700">{item.requesterName}</p>
                </div>
                <div>
                  <span className="text-slate-400 text-xs">E-mail</span>
                  <p className="font-medium text-slate-700">{item.requesterEmail}</p>
                </div>
                {item.requesterPhone && (
                  <div>
                    <span className="text-slate-400 text-xs">Telefone</span>
                    <p className="text-slate-700">{item.requesterPhone}</p>
                  </div>
                )}
                {item.requesterRole && (
                  <div>
                    <span className="text-slate-400 text-xs">Função</span>
                    <p className="text-slate-700 capitalize">{item.requesterRole.toLowerCase()}</p>
                  </div>
                )}
              </div>

              {item.rejectionReason && (
                <div className="bg-red-50 rounded-lg px-3 py-2 text-sm text-red-700">
                  <span className="font-medium">Motivo:</span> {item.rejectionReason}
                </div>
              )}

              {item.status === "PENDING" && (
                <div className="flex gap-2 pt-1">
                  <button
                    onClick={() => handleApprove(item.id)}
                    disabled={saving}
                    className="px-4 py-1.5 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
                  >
                    Aprovar
                  </button>
                  <button
                    onClick={() => { setRejectId(item.id); setRejectReason(""); }}
                    disabled={saving}
                    className="px-4 py-1.5 bg-red-50 hover:bg-red-100 text-red-600 text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
                  >
                    Rejeitar
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Modal de rejeição */}
      {rejectId !== null && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl p-6 max-w-md w-full space-y-4">
            <h3 className="font-semibold text-slate-800">Rejeitar solicitação</h3>
            <p className="text-sm text-slate-500">
              Informe o motivo da rejeição. O solicitante será notificado (em breve).
            </p>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              rows={3}
              placeholder="Ex: Documentação incompleta. Por favor envie novamente com o CNPJ válido."
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => setRejectId(null)}
                className="px-4 py-2 text-sm text-slate-500 hover:text-slate-700"
              >
                Cancelar
              </button>
              <button
                onClick={handleReject}
                disabled={saving || !rejectReason.trim()}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
              >
                {saving ? "Rejeitando..." : "Confirmar rejeição"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

import { useEffect, useState } from "react";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";
import { getUser } from "../lib/auth";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type Category = { id: number; name: string; icon: string; color: string; sortOrder: number };
type Subcategory = { id: number; categoryId: number; name: string; slaHours: number };

type WorkOrder = {
  id: number;
  condominiumId: number;
  condominiumName?: string;
  categoryId: number;
  categoryName?: string;
  subcategoryId?: number;
  subcategoryName?: string;
  unitId?: number;
  title: string;
  description?: string;
  status: "OPEN" | "IN_PROGRESS" | "WAITING_PARTS" | "RESOLVED" | "CLOSED" | "CANCELLED";
  priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  slaDeadline?: string;
  createdAt: string;
};

type WorkOrderUpdate = {
  id: number;
  authorName?: string;
  content: string;
  newStatus?: string;
  createdAt: string;
};

type Condo = { id: number; name: string };

const STATUS_LABELS: Record<string, string> = {
  OPEN: "Aberta",
  IN_PROGRESS: "Em andamento",
  WAITING_PARTS: "Aguardando peças",
  RESOLVED: "Resolvida",
  CLOSED: "Fechada",
  CANCELLED: "Cancelada",
};

const STATUS_COLORS: Record<string, string> = {
  OPEN: "bg-amber-100 text-amber-700",
  IN_PROGRESS: "bg-blue-100 text-blue-700",
  WAITING_PARTS: "bg-purple-100 text-purple-700",
  RESOLVED: "bg-emerald-100 text-emerald-700",
  CLOSED: "bg-slate-100 text-slate-600",
  CANCELLED: "bg-rose-100 text-rose-700",
};

const PRIORITY_COLORS: Record<string, string> = {
  LOW: "text-slate-500",
  MEDIUM: "text-amber-600",
  HIGH: "text-orange-600",
  URGENT: "text-rose-600",
};

const PRIORITY_LABELS: Record<string, string> = {
  LOW: "Baixa",
  MEDIUM: "Média",
  HIGH: "Alta",
  URGENT: "Urgente",
};

function SlaIndicator({ deadline }: { deadline?: string }) {
  if (!deadline) return null;
  const diff = new Date(deadline).getTime() - Date.now();
  const hours = diff / 3_600_000;
  const color = hours < 0 ? "text-rose-600" : hours < 4 ? "text-amber-600" : "text-slate-500";
  const label = hours < 0
    ? `SLA: ${Math.round(-hours)}h atrasado`
    : `SLA: ${Math.round(hours)}h restantes`;
  return <span className={`text-xs font-medium ${color}`}>{label}</span>;
}

export default function WorkOrdersPage() {
  const toast = useToast();
  const currentUser = getUser();
  const isManager = ["SUPERUSER", "ADMIN", "SINDICO"].includes(currentUser?.role ?? "");
  const isWorker = ["ZELADOR", "PORTARIA"].includes(currentUser?.role ?? "");
  const isMorador = currentUser?.role === "MORADOR";
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);

  const [orders, setOrders] = useState<WorkOrder[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [condos, setCondos] = useState<Condo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState("");

  // Criação — passo a passo
  const [showCreate, setShowCreate] = useState(false);
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [selCategory, setSelCategory] = useState<Category | null>(null);
  const [subcategories, setSubcategories] = useState<Subcategory[]>([]);
  const [selSub, setSelSub] = useState<Subcategory | null>(null);
  const [descField, setDescField] = useState("");
  const [saving, setSaving] = useState(false);

  // Detalhes + timeline
  const [selectedOrder, setSelectedOrder] = useState<WorkOrder | null>(null);
  const [updates, setUpdates] = useState<WorkOrderUpdate[]>([]);
  const [showDetail, setShowDetail] = useState(false);
  const [comment, setComment] = useState("");
  const [newStatus, setNewStatus] = useState("");

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

  async function loadOrders() {
    try {
      setLoading(true);
      setError(null);
      const [ordersRes, catsRes] = await Promise.all([
        api.get("/api/work-orders", {
          params: {
            size: 30,
            status: filterStatus || undefined,
            condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined,
          },
        }),
        api.get("/api/work-order-categories"),
      ]);
      setOrders(ordersRes.data.content ?? []);
      setCategories(catsRes.data ?? []);
    } catch {
      const message = "Falha ao carregar ordens de servico";
      setError(message);
      toast.show({ type: "error", msg: message });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadOrders(); /* eslint-disable-next-line */ }, [filterStatus, selectedCondominiumId]);

  async function handleCategorySelect(cat: Category) {
    setSelCategory(cat);
      const res = await api.get(`/api/work-order-categories/${cat.id}/subcategories`);
    setSubcategories(res.data ?? []);
    setStep(2);
  }

  async function handleSubmit() {
    if (!selCategory || !descField.trim()) return;
    if (isSuperuser && !selectedCondominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para abrir a ordem de serviço." });
      return;
    }
    try {
      setSaving(true);
      await api.post("/api/work-orders", {
        condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined,
        categoryId: selCategory.id,
        subcategoryId: selSub?.id ?? undefined,
        title: selSub ? `${selCategory.name} - ${selSub.name}` : selCategory.name,
        description: descField.trim(),
        priority: "MEDIUM",
      });
      toast.show({ type: "success", msg: "OS aberta com sucesso!" });
      setShowCreate(false);
      resetCreate();
      loadOrders();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao criar OS" });
    } finally {
      setSaving(false);
    }
  }

  function resetCreate() {
    setStep(1); setSelCategory(null); setSelSub(null); setDescField(""); setSubcategories([]);
  }

  async function openDetail(order: WorkOrder) {
    setSelectedOrder(order);
    setNewStatus("");
    setComment("");
    try {
      const res = await api.get(`/api/work-orders/${order.id}/updates`);
      setUpdates(res.data ?? []);
    } catch {
      setUpdates([]);
    }
    setShowDetail(true);
  }

  async function handleUpdateStatus() {
    if (!selectedOrder || !newStatus) return;
    try {
      setSaving(true);
      await api.patch(`/api/work-orders/${selectedOrder.id}/status`, {
        status: newStatus,
        comment: comment || undefined,
      });
      toast.show({ type: "success", msg: "Status atualizado!" });
      setShowDetail(false);
      loadOrders();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao atualizar status" });
    } finally {
      setSaving(false);
    }
  }

  const getCategoryName = (order: WorkOrder) => order.categoryName ?? categories.find((c) => c.id === order.categoryId)?.name ?? `#${order.categoryId}`;

  return (
    <div className="p-6 max-w-5xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            Ordens de Serviço
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">Manutenções e chamados do condomínio</p>
        </div>
        <button
          onClick={() => { resetCreate(); setShowCreate(true); }}
          className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors shadow-sm"
        >
          + Abrir OS
        </button>
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

      {/* Filtro de status */}
      <div className="flex gap-2 mb-5 flex-wrap">
        {["", "OPEN", "IN_PROGRESS", "WAITING_PARTS", "RESOLVED", "CLOSED"].map((s) => (
          <button
            key={s}
            onClick={() => setFilterStatus(s)}
            className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
              filterStatus === s
                ? "bg-indigo-600 text-white"
                : "bg-white border border-slate-200 text-slate-600 hover:bg-slate-50"
            }`}
          >
            {s ? STATUS_LABELS[s] : "Todas"}
          </button>
        ))}
      </div>

      {/* Lista */}
      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <div key={i} className="bg-white rounded-xl border border-slate-100 h-20 animate-pulse" />)}
        </div>
      ) : error ? (
        <div className="bg-white rounded-xl border border-rose-200 p-10 text-center">
          <p className="text-sm font-medium text-rose-700">Nao foi possivel carregar as ordens de servico.</p>
          <p className="text-sm text-rose-500 mt-1">{error}</p>
        </div>
      ) : orders.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">
          Nenhuma ordem de serviço encontrada.
        </div>
      ) : (
        <div className="space-y-3">
          {orders.map((o) => (
            <div
              key={o.id}
              onClick={() => openDetail(o)}
              className="bg-white rounded-xl border border-slate-100 shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <span className="font-medium text-slate-900 text-sm truncate">{o.title}</span>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[o.status]}`}>
                      {STATUS_LABELS[o.status]}
                    </span>
                    <span className={`text-xs font-medium ${PRIORITY_COLORS[o.priority]}`}>
                      ● {PRIORITY_LABELS[o.priority]}
                    </span>
                  </div>
                  <div className="mt-1 flex items-center gap-3 flex-wrap text-xs text-slate-400">
                    <span>{getCategoryName(o)}</span>
                    {o.subcategoryName && <span>{o.subcategoryName}</span>}
                    <span>{o.condominiumName ?? `Condomínio #${o.condominiumId}`}</span>
                  </div>
                </div>
                <div className="flex-shrink-0 text-right">
                  <SlaIndicator deadline={o.slaDeadline} />
                  <p className="text-xs text-slate-400 mt-0.5">
                    {new Date(o.createdAt).toLocaleDateString("pt-BR")}
                  </p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal: Criar OS */}
      <Modal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        title={step === 1 ? "Selecione a Categoria" : step === 2 ? "Subcategoria" : "Descrição do Problema"}
        size="lg"
        footer={
          step === 3 ? (
            <>
              <button onClick={() => setStep(2)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">
                ← Voltar
              </button>
              <button
                onClick={handleSubmit}
                disabled={saving || !descField.trim()}
                className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium disabled:opacity-50"
              >
                {saving ? "Enviando…" : "Abrir OS"}
              </button>
            </>
          ) : undefined
        }
      >
        {step === 1 && (
          <div className="grid grid-cols-2 gap-2.5">
            {categories.map((cat) => (
              <button
                key={cat.id}
                onClick={() => handleCategorySelect(cat)}
                className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 hover:border-indigo-300 hover:bg-indigo-50/50 text-left transition-colors"
              >
                <div
                  className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 text-white text-sm font-bold"
                  style={{ backgroundColor: cat.color }}
                >
                  {cat.name.charAt(0)}
                </div>
                <span className="text-sm font-medium text-slate-700">{cat.name}</span>
              </button>
            ))}
          </div>
        )}

        {step === 2 && (
          <div>
            <button onClick={() => setStep(1)} className="text-xs text-slate-500 hover:text-slate-700 mb-3 flex items-center gap-1">
              ← {selCategory?.name}
            </button>
            <div className="space-y-2">
              {subcategories.map((sub) => (
                <button
                  key={sub.id}
                  onClick={() => { setSelSub(sub); setStep(3); }}
                  className="w-full flex items-center justify-between p-3 rounded-xl border border-slate-200 hover:border-indigo-300 hover:bg-indigo-50/50 text-left transition-colors"
                >
                  <span className="text-sm font-medium text-slate-700">{sub.name}</span>
                  <span className="text-xs text-slate-400">SLA: {sub.slaHours}h</span>
                </button>
              ))}
              <button
                onClick={() => { setSelSub(null); setStep(3); }}
                className="w-full p-3 rounded-xl border border-dashed border-slate-200 text-sm text-slate-500 hover:bg-slate-50 transition-colors"
              >
                Outro problema nesta categoria
              </button>
            </div>
          </div>
        )}

        {step === 3 && (
          <div>
            <div className="bg-slate-50 rounded-lg p-3 mb-4 text-sm text-slate-600">
              <span className="font-medium">{selCategory?.name}</span>
              {selSub && <> → {selSub.name}</>}
              {selSub && <span className="text-xs text-slate-400 ml-2">SLA: {selSub.slaHours}h</span>}
            </div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Descreva o problema *
            </label>
            <textarea
              value={descField}
              onChange={(e) => setDescField(e.target.value)}
              rows={5}
              placeholder="Descreva o problema com o máximo de detalhes possível…"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400 resize-none"
              autoFocus
            />
          </div>
        )}
      </Modal>

      {/* Modal: Detalhes e Timeline */}
      <Modal
        open={showDetail}
        onClose={() => setShowDetail(false)}
        title={selectedOrder ? `OS #${selectedOrder.id}` : ""}
        size="lg"
        footer={
          (isManager || isWorker) && selectedOrder && !["CLOSED", "CANCELLED"].includes(selectedOrder.status) ? (
            <>
              <select
                value={newStatus}
                onChange={(e) => setNewStatus(e.target.value)}
                className="border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-600 flex-1"
              >
                <option value="">Alterar status…</option>
                {["IN_PROGRESS", "WAITING_PARTS", "RESOLVED", "CLOSED", "CANCELLED"].map((s) => (
                  <option key={s} value={s}>{STATUS_LABELS[s]}</option>
                ))}
              </select>
              <button
                onClick={handleUpdateStatus}
                disabled={saving || !newStatus}
                className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium disabled:opacity-50"
              >
                {saving ? "Salvando…" : "Atualizar"}
              </button>
            </>
          ) : undefined
        }
      >
        {selectedOrder && (
          <div className="space-y-4">
            <div className="flex items-center gap-2 flex-wrap">
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[selectedOrder.status]}`}>
                {STATUS_LABELS[selectedOrder.status]}
              </span>
              <span className={`text-xs font-medium ${PRIORITY_COLORS[selectedOrder.priority]}`}>
                ● {PRIORITY_LABELS[selectedOrder.priority]}
              </span>
              <SlaIndicator deadline={selectedOrder.slaDeadline} />
            </div>
            <div>
              <p className="text-sm font-medium text-slate-900">{selectedOrder.title}</p>
              <div className="mt-1 flex items-center gap-3 flex-wrap text-xs text-slate-400">
                <span>{selectedOrder.condominiumName ?? `Condomínio #${selectedOrder.condominiumId}`}</span>
                <span>{selectedOrder.categoryName ?? getCategoryName(selectedOrder)}</span>
                {selectedOrder.subcategoryName && <span>{selectedOrder.subcategoryName}</span>}
              </div>
              {selectedOrder.description && (
                <p className="text-sm text-slate-600 mt-1">{selectedOrder.description}</p>
              )}
            </div>
            {(isManager || isWorker) && (
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Comentário</label>
                <textarea
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  rows={2}
                  placeholder="Adicione um comentário…"
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
                />
              </div>
            )}
            {updates.length > 0 && (
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Histórico</p>
                <div className="space-y-2 max-h-40 overflow-y-auto">
                  {updates.map((u) => (
                    <div key={u.id} className="flex gap-2.5">
                      <div className="w-1.5 h-1.5 rounded-full bg-indigo-400 mt-1.5 flex-shrink-0" />
                      <div>
                        <p className="text-xs text-slate-700">{u.content}</p>
                        <p className="text-xs text-slate-400">
                          {new Date(u.createdAt).toLocaleString("pt-BR")}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}

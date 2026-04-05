import { useEffect, useState } from "react";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";
import { getUser } from "../lib/auth";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type CommonArea = {
  id: number;
  condominiumId?: number;
  name: string;
  capacity?: number;
  rules?: string;
  maxHoursPerReservation: number;
  requiresApproval: boolean;
  allowedStartHour?: number;
  allowedEndHour?: number;
  reservationDescription?: string;
  reservationApprovalMode?: "AUTOMATIC" | "REQUIRE_APPROVAL";
  allowOverrideFromCondominiumDefault: boolean;
  active: boolean;
};

type Reservation = {
  id: number;
  condominiumId?: number;
  commonAreaId: number;
  unitId: number;
  startDatetime: string;
  endDatetime: string;
  title?: string;
  notes?: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED";
  createdAt: string;
};

type Condo = { id: number; name: string };
type UnitOption = { id: number; number?: string; block?: string; code?: string };
type ReservationPolicy = {
  id?: number;
  reservationPolicyMode: "FLEXIBLE_INTERVAL" | "FIXED_WINDOW";
  defaultMaxDurationHours: number;
  defaultStartHour: number;
  defaultEndHour: number;
  allDayReservationAllowed: boolean;
  reservationApprovalMode: "AUTOMATIC" | "REQUIRE_APPROVAL";
  reservationRules?: string;
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Pendente",
  APPROVED: "Aprovada",
  REJECTED: "Rejeitada",
  CANCELLED: "Cancelada",
  COMPLETED: "Concluída",
};

const STATUS_COLORS: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  APPROVED: "bg-emerald-100 text-emerald-700",
  REJECTED: "bg-rose-100 text-rose-700",
  CANCELLED: "bg-slate-100 text-slate-600",
  COMPLETED: "bg-indigo-100 text-indigo-700",
};

function fmtDate(iso: string) {
  return new Date(iso).toLocaleString("pt-BR", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

export default function ReservationsPage() {
  const toast = useToast();
  const currentUser = getUser();
  const isManager = ["SUPERUSER", "ADMIN", "SINDICO"].includes(currentUser?.role ?? "");
  const isMorador = currentUser?.role === "MORADOR";
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);

  const [areas, setAreas] = useState<CommonArea[]>([]);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [condos, setCondos] = useState<Condo[]>([]);
  const [units, setUnits] = useState<UnitOption[]>([]);
  const [selectedUnitId, setSelectedUnitId] = useState(
    currentUser?.unitId != null ? String(currentUser.unitId) : "",
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"reservations" | "areas">("reservations");

  // Modal reserva
  const [showModal, setShowModal] = useState(false);
  const [selectedArea, setSelectedArea] = useState<CommonArea | null>(null);
  const [startDt, setStartDt] = useState("");
  const [endDt, setEndDt] = useState("");
  const [titleField, setTitleField] = useState("");
  const [saving, setSaving] = useState(false);

  // Modal área
  const [showAreaModal, setShowAreaModal] = useState(false);
  const [showPolicyModal, setShowPolicyModal] = useState(false);
  const [editingArea, setEditingArea] = useState<CommonArea | null>(null);
  const [policy, setPolicy] = useState<ReservationPolicy | null>(null);
  const [policyForm, setPolicyForm] = useState({
    reservationPolicyMode: "FLEXIBLE_INTERVAL",
    defaultMaxDurationHours: "4",
    defaultStartHour: "8",
    defaultEndHour: "22",
    allDayReservationAllowed: false,
    reservationApprovalMode: "AUTOMATIC",
    reservationRules: "",
  });
  const [areaForm, setAreaForm] = useState({
    name: "",
    capacity: "",
    rules: "",
    maxHours: "4",
    requiresApproval: false,
    allowedStartHour: "",
    allowedEndHour: "",
    reservationDescription: "",
    reservationApprovalMode: "AUTOMATIC",
    allowOverrideFromCondominiumDefault: false,
  });

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

  useEffect(() => {
    const condoId = selectedCondominiumId ? Number(selectedCondominiumId) : undefined;
    if (!condoId) {
      setUnits([]);
      if (!currentUser?.unitId) setSelectedUnitId("");
      setPolicy(null);
      return;
    }
    api.get("/units", { params: { condominiumId: condoId, page: 0, size: 100 } })
      .then((res) => {
        const raw = res.data;
        const list: UnitOption[] = Array.isArray(raw.content)
          ? raw.content
          : Array.isArray(raw.items)
            ? raw.items
            : Array.isArray(raw)
              ? raw
              : [];
        setUnits(list);
      })
      .catch(() => setUnits([]));

    if (isManager) {
      api.get(`/condominiums/${condoId}`)
        .then((res) => {
          const nextPolicy: ReservationPolicy = res.data;
          setPolicy(nextPolicy);
          setPolicyForm({
            reservationPolicyMode: nextPolicy.reservationPolicyMode ?? "FLEXIBLE_INTERVAL",
            defaultMaxDurationHours: String(nextPolicy.defaultMaxDurationHours ?? 4),
            defaultStartHour: String(nextPolicy.defaultStartHour ?? 8),
            defaultEndHour: String(nextPolicy.defaultEndHour ?? 22),
            allDayReservationAllowed: Boolean(nextPolicy.allDayReservationAllowed),
            reservationApprovalMode: nextPolicy.reservationApprovalMode ?? "AUTOMATIC",
            reservationRules: nextPolicy.reservationRules ?? "",
          });
        })
        .catch(() => setPolicy(null));
    }
  }, [selectedCondominiumId, currentUser?.unitId]);

  function formatUnitLabel(unit: UnitOption) {
    const base = unit.number || unit.code || `#${unit.id}`;
    return unit.block ? `Unidade ${base} • Bloco ${unit.block}` : `Unidade ${base}`;
  }

  function describeAreaPolicy(area: CommonArea) {
    const maxHours = area.allowOverrideFromCondominiumDefault
      ? area.maxHoursPerReservation
      : policy?.defaultMaxDurationHours ?? area.maxHoursPerReservation;
    const startHour = area.allowOverrideFromCondominiumDefault
      ? area.allowedStartHour
      : policy?.defaultStartHour;
    const endHour = area.allowOverrideFromCondominiumDefault
      ? area.allowedEndHour
      : policy?.defaultEndHour;
    const approvalMode = area.allowOverrideFromCondominiumDefault
      ? area.reservationApprovalMode
      : policy?.reservationApprovalMode;

    if (policy?.allDayReservationAllowed) {
      return `Até ${maxHours}h • ${approvalMode === "REQUIRE_APPROVAL" ? "Sujeito a aprovação" : "Aprovação automática"}`;
    }
    if (startHour != null && endHour != null) {
      return `${String(startHour).padStart(2, "0")}:00 às ${String(endHour).padStart(2, "0")}:00 • Até ${maxHours}h`;
    }
    return `Até ${maxHours}h`;
  }

  async function loadAll() {
    try {
      setLoading(true);
      setError(null);
      const params = {
        size: 20,
        condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined,
      };
      const [areasRes, resvRes] = await Promise.all([
        api.get("/api/common-areas", { params: { ...params, size: 50 } }),
        api.get("/api/reservations", { params }),
      ]);
      setAreas(areasRes.data.content ?? []);
      setReservations(resvRes.data.content ?? []);
    } catch {
      const message = "Falha ao carregar dados";
      setError(message);
      toast.show({ type: "error", msg: message });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadAll(); /* eslint-disable-next-line */ }, [selectedCondominiumId]);

  async function handleCreateReservation() {
    if (!selectedArea || !startDt || !endDt) return;
    if (isSuperuser && !selectedCondominiumId && !selectedArea.condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para criar a reserva." });
      return;
    }
    if (!currentUser?.unitId && !selectedUnitId) {
      toast.show({ type: "error", msg: "Selecione a unidade que fará a reserva." });
      return;
    }
    try {
      setSaving(true);
      await api.post("/api/reservations", {
        condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : selectedArea.condominiumId,
        commonAreaId: selectedArea.id,
        unitId: selectedUnitId ? Number(selectedUnitId) : undefined,
        startDatetime: new Date(startDt).toISOString(),
        endDatetime: new Date(endDt).toISOString(),
        title: titleField || undefined,
      });
      toast.show({ type: "success", msg: "Reserva criada com sucesso!" });
      setShowModal(false);
      setStartDt(""); setEndDt(""); setTitleField(""); setSelectedArea(null);
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao criar reserva" });
    } finally {
      setSaving(false);
    }
  }

  async function handleAction(id: number, action: "approve" | "reject" | "cancel") {
    try {
      await api.patch(`/api/reservations/${id}/${action}`);
      toast.show({ type: "success", msg: "Operação realizada com sucesso!" });
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro na operação" });
    }
  }

  async function handleSaveArea() {
    try {
      setSaving(true);
      const payload = {
        name: areaForm.name,
        capacity: areaForm.capacity ? Number(areaForm.capacity) : null,
        rules: areaForm.rules || null,
        maxHoursPerReservation: Number(areaForm.maxHours),
        requiresApproval: areaForm.requiresApproval,
        allowedStartHour: areaForm.allowedStartHour ? Number(areaForm.allowedStartHour) : null,
        allowedEndHour: areaForm.allowedEndHour ? Number(areaForm.allowedEndHour) : null,
        reservationDescription: areaForm.reservationDescription || null,
        reservationApprovalMode: areaForm.reservationApprovalMode,
        allowOverrideFromCondominiumDefault: areaForm.allowOverrideFromCondominiumDefault,
      };
      if (editingArea) {
        await api.put(`/api/common-areas/${editingArea.id}`, { ...payload, active: true });
        toast.show({ type: "success", msg: "Área atualizada!" });
      } else {
        await api.post("/api/common-areas", {
          ...payload,
          condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined,
        });
        toast.show({ type: "success", msg: "Área criada!" });
      }
      setShowAreaModal(false);
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao salvar área" });
    } finally {
      setSaving(false);
    }
  }

  function openEditArea(area: CommonArea) {
    setEditingArea(area);
    setAreaForm({
      name: area.name,
      capacity: area.capacity?.toString() ?? "",
      rules: area.rules ?? "",
      maxHours: area.maxHoursPerReservation.toString(),
      requiresApproval: area.requiresApproval,
      allowedStartHour: area.allowedStartHour?.toString() ?? "",
      allowedEndHour: area.allowedEndHour?.toString() ?? "",
      reservationDescription: area.reservationDescription ?? "",
      reservationApprovalMode: area.reservationApprovalMode ?? (area.requiresApproval ? "REQUIRE_APPROVAL" : "AUTOMATIC"),
      allowOverrideFromCondominiumDefault: area.allowOverrideFromCondominiumDefault,
    });
    setShowAreaModal(true);
  }

  function openNewArea() {
    setEditingArea(null);
    setAreaForm({
      name: "",
      capacity: "",
      rules: "",
      maxHours: policy?.defaultMaxDurationHours?.toString() ?? "4",
      requiresApproval: policy?.reservationApprovalMode === "REQUIRE_APPROVAL",
      allowedStartHour: policy?.defaultStartHour?.toString() ?? "",
      allowedEndHour: policy?.defaultEndHour?.toString() ?? "",
      reservationDescription: "",
      reservationApprovalMode: policy?.reservationApprovalMode ?? "AUTOMATIC",
      allowOverrideFromCondominiumDefault: false,
    });
    setShowAreaModal(true);
  }

  async function handleSavePolicy() {
    if (!selectedCondominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para ajustar a política de reserva." });
      return;
    }
    try {
      setSaving(true);
      await api.put(`/condominiums/${selectedCondominiumId}`, {
        reservationPolicyMode: policyForm.reservationPolicyMode,
        defaultMaxDurationHours: Number(policyForm.defaultMaxDurationHours),
        defaultStartHour: Number(policyForm.defaultStartHour),
        defaultEndHour: Number(policyForm.defaultEndHour),
        allDayReservationAllowed: policyForm.allDayReservationAllowed,
        reservationApprovalMode: policyForm.reservationApprovalMode,
        reservationRules: policyForm.reservationRules || null,
      });
      toast.show({ type: "success", msg: "Política de reserva atualizada!" });
      setShowPolicyModal(false);
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao salvar a política de reserva" });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="p-6 max-w-5xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            Reservas
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">Áreas comuns e agendamentos</p>
        </div>
        <div className="flex items-center gap-2">
          {isManager && (
            <button
              onClick={() => setShowPolicyModal(true)}
              className="border border-slate-200 text-slate-700 hover:bg-slate-50 px-4 py-2 rounded-lg text-sm font-medium transition-colors"
            >
              Política de reserva
            </button>
          )}
          {isManager && (
            <button
              onClick={openNewArea}
              className="border border-slate-200 text-slate-700 hover:bg-slate-50 px-4 py-2 rounded-lg text-sm font-medium transition-colors"
            >
              + Nova área
            </button>
          )}
          <button
            onClick={() => setShowModal(true)}
            className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors shadow-sm"
          >
            + Nova reserva
          </button>
        </div>
      </div>

      {isSuperuser && (
        <div className="mb-5 max-w-sm">
          <label className="block text-sm font-medium text-slate-700 mb-1.5">Condomínio</label>
          <select
            value={selectedCondominiumId}
            onChange={(e) => {
              setSelectedCondominiumId(e.target.value);
              if (!currentUser?.unitId) setSelectedUnitId("");
            }}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
          >
            <option value="">Todos os condomínios</option>
            {condos.map((condo) => (
              <option key={condo.id} value={String(condo.id)}>{condo.name}</option>
            ))}
          </select>
        </div>
      )}

      {isManager && selectedCondominiumId && policy && (
        <div className="mb-5 bg-white rounded-xl border border-slate-100 shadow-sm p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 mb-1">Política padrão do condomínio</p>
          <div className="flex items-center gap-3 flex-wrap text-sm text-slate-600">
            <span>{policy.reservationPolicyMode === "FIXED_WINDOW" ? "Janela fixa" : "Intervalo flexível"}</span>
            <span>Máx. {policy.defaultMaxDurationHours}h</span>
            {policy.allDayReservationAllowed ? (
              <span>Uso ao longo do dia</span>
            ) : (
              <span>{String(policy.defaultStartHour).padStart(2, "0")}:00 às {String(policy.defaultEndHour).padStart(2, "0")}:00</span>
            )}
            <span>{policy.reservationApprovalMode === "REQUIRE_APPROVAL" ? "Aprovação manual" : "Aprovação automática"}</span>
          </div>
          {policy.reservationRules && <p className="text-xs text-slate-500 mt-2">{policy.reservationRules}</p>}
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 mb-5 bg-slate-100 rounded-lg p-1 w-fit">
        {(["reservations", "areas"] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
              activeTab === tab ? "bg-white shadow-sm text-slate-900" : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {tab === "reservations" ? "Reservas" : "Áreas Comuns"}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-white rounded-xl border border-slate-100 h-20 animate-pulse" />
          ))}
        </div>
      ) : error ? (
        <div className="bg-white rounded-xl border border-rose-200 p-10 text-center">
          <p className="text-sm font-medium text-rose-700">Nao foi possivel carregar as reservas.</p>
          <p className="text-sm text-rose-500 mt-1">{error}</p>
        </div>
      ) : activeTab === "reservations" ? (
        /* ── Lista de Reservas ── */
        <div className="space-y-3">
          {reservations.length === 0 ? (
            <div className="bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">
              Nenhuma reserva encontrada.
            </div>
          ) : reservations.map((r) => {
            const area = areas.find((a) => a.id === r.commonAreaId);
            return (
              <div key={r.id} className="bg-white rounded-xl border border-slate-100 shadow-sm p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-slate-900 text-sm">
                        {r.title || area?.name || `Área #${r.commonAreaId}`}
                      </span>
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[r.status]}`}>
                        {STATUS_LABELS[r.status]}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500">
                      {fmtDate(r.startDatetime)} → {fmtDate(r.endDatetime)}
                    </p>
                    {area && <p className="text-xs text-slate-400 mt-0.5">{area.name}</p>}
                  </div>
                  <div className="flex items-center gap-1.5 flex-shrink-0">
                    {isManager && r.status === "PENDING" && (
                      <>
                        <button
                          onClick={() => handleAction(r.id, "approve")}
                          className="text-xs px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 font-medium transition-colors"
                        >
                          Aprovar
                        </button>
                        <button
                          onClick={() => handleAction(r.id, "reject")}
                          className="text-xs px-2.5 py-1 rounded-lg bg-rose-50 text-rose-700 hover:bg-rose-100 font-medium transition-colors"
                        >
                          Rejeitar
                        </button>
                      </>
                    )}
                    {(r.status === "PENDING" || r.status === "APPROVED") && (
                      <button
                        onClick={() => handleAction(r.id, "cancel")}
                        className="text-xs px-2.5 py-1 rounded-lg bg-slate-50 text-slate-600 hover:bg-slate-100 font-medium transition-colors"
                      >
                        Cancelar
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        /* ── Lista de Áreas ── */
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {areas.length === 0 ? (
            <div className="col-span-2 bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">
              Nenhuma área comum cadastrada.
            </div>
          ) : areas.map((a) => (
            <div key={a.id} className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
              <div className="h-1 bg-indigo-500" />
              <div className="p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="font-semibold text-slate-900 text-sm">{a.name}</h3>
                    {a.capacity && <p className="text-xs text-slate-400">Capacidade: {a.capacity} pessoas</p>}
                    <p className="text-xs text-slate-400 mt-1">{describeAreaPolicy(a)}</p>
                  </div>
                  {isManager && (
                    <button
                      onClick={() => openEditArea(a)}
                      className="text-xs text-indigo-600 hover:text-indigo-700 font-medium"
                    >
                      Editar
                    </button>
                  )}
                </div>
                {a.rules && <p className="mt-2 text-xs text-slate-500 border-t border-slate-50 pt-2 line-clamp-2">{a.rules}</p>}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal: Nova Reserva */}
      <Modal
        open={showModal}
        onClose={() => setShowModal(false)}
        title="Nova Reserva"
        footer={
          <>
            <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              Cancelar
            </button>
            <button
              onClick={handleCreateReservation}
              disabled={saving || !selectedArea || !startDt || !endDt}
              className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50"
            >
              {saving ? "Salvando…" : "Criar Reserva"}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          {isSuperuser && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Condomínio</label>
              <select
                value={selectedCondominiumId}
                onChange={(e) => {
                  setSelectedCondominiumId(e.target.value);
                  setSelectedArea(null);
                  if (!currentUser?.unitId) setSelectedUnitId("");
                }}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              >
                <option value="">Selecione um condomínio…</option>
                {condos.map((condo) => (
                  <option key={condo.id} value={String(condo.id)}>{condo.name}</option>
                ))}
              </select>
            </div>
          )}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Área Comum</label>
            <select
              value={selectedArea?.id ?? ""}
              onChange={(e) => setSelectedArea(areas.find((a) => a.id === Number(e.target.value)) ?? null)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            >
              <option value="">Selecione uma área…</option>
              {areas.map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
          </div>
          {!isMorador && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Unidade</label>
              <select
                value={selectedUnitId}
                onChange={(e) => setSelectedUnitId(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              >
                <option value="">
                  {currentUser?.unitId ? "Usar unidade vinculada ao seu usuário" : "Selecione a unidade…"}
                </option>
                {units.map((unit) => (
                  <option key={unit.id} value={String(unit.id)}>{formatUnitLabel(unit)}</option>
                ))}
              </select>
            </div>
          )}
          {selectedArea && (
            <div className="bg-slate-50 rounded-lg p-3 text-xs text-slate-600">
              {describeAreaPolicy(selectedArea)}
              {selectedArea.reservationDescription && ` • ${selectedArea.reservationDescription}`}
            </div>
          )}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Título (opcional)</label>
            <input
              value={titleField}
              onChange={(e) => setTitleField(e.target.value)}
              placeholder="Ex: Churrasco de aniversário"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Início</label>
              <input
                type="datetime-local"
                value={startDt}
                onChange={(e) => setStartDt(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Fim</label>
              <input
                type="datetime-local"
                value={endDt}
                onChange={(e) => setEndDt(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              />
            </div>
          </div>
        </div>
      </Modal>

      {/* Modal: Área Comum */}
      <Modal
        open={showAreaModal}
        onClose={() => setShowAreaModal(false)}
        title={editingArea ? "Editar Área" : "Nova Área Comum"}
        footer={
          <>
            <button onClick={() => setShowAreaModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              Cancelar
            </button>
            <button
              onClick={handleSaveArea}
              disabled={saving || !areaForm.name}
              className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50"
            >
              {saving ? "Salvando…" : "Salvar"}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Nome *</label>
            <input
              value={areaForm.name}
              onChange={(e) => setAreaForm({ ...areaForm, name: e.target.value })}
              placeholder="Ex: Salão de festas, Churrasqueira…"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Capacidade</label>
              <input
                type="number"
                value={areaForm.capacity}
                onChange={(e) => setAreaForm({ ...areaForm, capacity: e.target.value })}
                placeholder="Pessoas"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Máx. horas/reserva</label>
              <input
                type="number"
                value={areaForm.maxHours}
                onChange={(e) => setAreaForm({ ...areaForm, maxHours: e.target.value })}
                min={1} max={24}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              />
            </div>
          </div>
          <label className="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={areaForm.allowOverrideFromCondominiumDefault}
              onChange={(e) => setAreaForm({ ...areaForm, allowOverrideFromCondominiumDefault: e.target.checked })}
              className="w-4 h-4 rounded text-indigo-600"
            />
            <span className="text-sm text-slate-700">Esta área sobrescreve a política padrão do condomínio</span>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Início permitido</label>
              <input
                type="number"
                value={areaForm.allowedStartHour}
                onChange={(e) => setAreaForm({ ...areaForm, allowedStartHour: e.target.value })}
                min={0}
                max={23}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Fim permitido</label>
              <input
                type="number"
                value={areaForm.allowedEndHour}
                onChange={(e) => setAreaForm({ ...areaForm, allowedEndHour: e.target.value })}
                min={1}
                max={23}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Descrição da regra da área</label>
            <textarea
              value={areaForm.reservationDescription}
              onChange={(e) => setAreaForm({ ...areaForm, reservationDescription: e.target.value })}
              rows={2}
              placeholder="Ex: Churrasqueira disponível das 12h às 22h no mesmo dia."
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400 resize-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Aprovação da área</label>
            <select
              value={areaForm.reservationApprovalMode}
              onChange={(e) => setAreaForm({ ...areaForm, reservationApprovalMode: e.target.value as "AUTOMATIC" | "REQUIRE_APPROVAL" })}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            >
              <option value="AUTOMATIC">Aprovação automática</option>
              <option value="REQUIRE_APPROVAL">Exigir aprovação</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Regras de uso</label>
            <textarea
              value={areaForm.rules}
              onChange={(e) => setAreaForm({ ...areaForm, rules: e.target.value })}
              rows={3}
              placeholder="Descreva as regras de uso da área…"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400 resize-none"
            />
          </div>
          <label className="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={areaForm.requiresApproval}
              onChange={(e) => setAreaForm({ ...areaForm, requiresApproval: e.target.checked })}
              className="w-4 h-4 rounded text-indigo-600"
            />
            <span className="text-sm text-slate-700">Requer aprovação do síndico/administrador</span>
          </label>
        </div>
      </Modal>

      <Modal
        open={showPolicyModal}
        onClose={() => setShowPolicyModal(false)}
        title="Política de reserva do condomínio"
        footer={
          <>
            <button onClick={() => setShowPolicyModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              Cancelar
            </button>
            <button
              onClick={handleSavePolicy}
              disabled={saving || !selectedCondominiumId}
              className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50"
            >
              {saving ? "Salvando…" : "Salvar política"}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Modo</label>
              <select
                value={policyForm.reservationPolicyMode}
                onChange={(e) => setPolicyForm({ ...policyForm, reservationPolicyMode: e.target.value as "FLEXIBLE_INTERVAL" | "FIXED_WINDOW" })}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              >
                <option value="FLEXIBLE_INTERVAL">Intervalo flexível</option>
                <option value="FIXED_WINDOW">Janela fixa</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Máx. horas padrão</label>
              <input
                type="number"
                min={1}
                max={24}
                value={policyForm.defaultMaxDurationHours}
                onChange={(e) => setPolicyForm({ ...policyForm, defaultMaxDurationHours: e.target.value })}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Início padrão</label>
              <input
                type="number"
                min={0}
                max={23}
                value={policyForm.defaultStartHour}
                onChange={(e) => setPolicyForm({ ...policyForm, defaultStartHour: e.target.value })}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Fim padrão</label>
              <input
                type="number"
                min={1}
                max={23}
                value={policyForm.defaultEndHour}
                onChange={(e) => setPolicyForm({ ...policyForm, defaultEndHour: e.target.value })}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              />
            </div>
          </div>
          <label className="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={policyForm.allDayReservationAllowed}
              onChange={(e) => setPolicyForm({ ...policyForm, allDayReservationAllowed: e.target.checked })}
              className="w-4 h-4 rounded text-indigo-600"
            />
            <span className="text-sm text-slate-700">Permitir uso ao longo do dia, sem limitar por janela horária</span>
          </label>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Aprovação padrão</label>
            <select
              value={policyForm.reservationApprovalMode}
              onChange={(e) => setPolicyForm({ ...policyForm, reservationApprovalMode: e.target.value as "AUTOMATIC" | "REQUIRE_APPROVAL" })}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            >
              <option value="AUTOMATIC">Aprovação automática</option>
              <option value="REQUIRE_APPROVAL">Exigir aprovação</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Notas da política</label>
            <textarea
              value={policyForm.reservationRules}
              onChange={(e) => setPolicyForm({ ...policyForm, reservationRules: e.target.value })}
              rows={3}
              placeholder="Ex: churrasqueira das 12h às 22h; quadra com sessões de 2h."
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none"
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}

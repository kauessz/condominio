import { useEffect, useMemo, useState } from "react";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";
import { getUser } from "../lib/auth";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type Condo = { id: number; name: string };
type UnitLite = { id: number; number: string | number; block?: string | null };
type ParkingSpot = { id: number; condominiumId: number; code: string; description?: string; active: boolean };
type ParkingDraw = { id: number; condominiumId: number; name: string; registrationOpenAt: string; registrationCloseAt: string; validFrom: string; validUntil: string; status: "OPEN" | "CLOSED" | "EXECUTED" | "CANCELLED"; executedAt?: string };
type Registration = { id: number; drawId: number; condominiumId: number; unitId: number; unitLabel?: string; residentId?: number | null; residentName?: string | null; registeredAt: string; hasActiveAssignment: boolean };
type Assignment = { id: number; condominiumId: number; spotId: number; spotCode?: string; spotDescription?: string | null; unitId: number; unitLabel?: string; residentName?: string | null; drawId?: number | null; drawName?: string | null; validFrom: string; validUntil: string; status: string };
type ParkingPolicy = { parkingPolicyMode?: "FIXED" | "DRAW"; parkingDrawFrequency?: "MONTHLY" | "QUARTERLY" | "SEMIANNUAL" | "YEARLY" | "CUSTOM"; drawIntervalMonths?: number | null; allowManualAssignments?: boolean; allowResidentRegistration?: boolean; maxVehiclesPerUnit?: number; parkingRules?: string | null };

const DRAW_STATUS: Record<string, { label: string; color: string }> = {
  OPEN: { label: "Inscrições abertas", color: "bg-emerald-100 text-emerald-700" },
  CLOSED: { label: "Inscrições encerradas", color: "bg-amber-100 text-amber-700" },
  EXECUTED: { label: "Executado", color: "bg-indigo-100 text-indigo-700" },
  CANCELLED: { label: "Cancelado", color: "bg-rose-100 text-rose-700" },
};

function unitLabel(unit: UnitLite) {
  return `Unidade ${unit.number}${unit.block ? ` • Bloco ${unit.block}` : ""}`;
}

export default function ParkingPage() {
  const toast = useToast();
  const currentUser = getUser();
  const isManager = ["SUPERUSER", "ADMIN", "SINDICO"].includes(currentUser?.role ?? "");
  const isMorador = currentUser?.role === "MORADOR";
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);

  const [spots, setSpots] = useState<ParkingSpot[]>([]);
  const [draws, setDraws] = useState<ParkingDraw[]>([]);
  const [myAssignment, setMyAssignment] = useState<(Assignment & { spotCode?: string }) | null>(null);
  const [allAssignments, setAllAssignments] = useState<Assignment[]>([]);
  const [condos, setCondos] = useState<Condo[]>([]);
  const [units, setUnits] = useState<UnitLite[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"draws" | "spots" | "assignments">("draws");
  const [policy, setPolicy] = useState<ParkingPolicy>({ parkingPolicyMode: "DRAW", parkingDrawFrequency: "QUARTERLY", drawIntervalMonths: null, allowManualAssignments: true, allowResidentRegistration: true, maxVehiclesPerUnit: 1, parkingRules: "" });
  const [showPolicyModal, setShowPolicyModal] = useState(false);
  const [showDrawModal, setShowDrawModal] = useState(false);
  const [drawForm, setDrawForm] = useState({ name: "", regOpen: "", regClose: "", validFrom: "", validUntil: "" });
  const [showSpotModal, setShowSpotModal] = useState(false);
  const [spotForm, setSpotForm] = useState({ code: "", description: "" });
  const [editingSpot, setEditingSpot] = useState<ParkingSpot | null>(null);
  const [showAssignmentModal, setShowAssignmentModal] = useState(false);
  const [editingAssignment, setEditingAssignment] = useState<Assignment | null>(null);
  const [assignmentForm, setAssignmentForm] = useState({ spotId: "", unitId: "", validFrom: "", validUntil: "" });
  const [saving, setSaving] = useState(false);
  const [registrations, setRegistrations] = useState<Map<number, Registration[]>>(new Map());
  const condominiumId = isSuperuser ? (selectedCondominiumId ? Number(selectedCondominiumId) : undefined) : (currentUser?.condominiumId ? Number(currentUser.condominiumId) : undefined);

  useEffect(() => {
    if (!isSuperuser) return;
    api.get("/condominiums", { params: { pageSize: 100 } })
      .then((res) => {
        const raw = res.data;
        const list: Condo[] = Array.isArray(raw.content) ? raw.content : Array.isArray(raw.items) ? raw.items : Array.isArray(raw) ? raw : [];
        setCondos(list);
      })
      .catch(() => setCondos([]));
  }, [isSuperuser]);

  const condoNameById = useMemo(() => new Map(condos.map((condo) => [condo.id, condo.name])), [condos]);
  const isDrawMode = policy.parkingPolicyMode !== "FIXED";
  const selectedCondoName = condominiumId ? condoNameById.get(condominiumId) : null;
  const policyDescription = isDrawMode
    ? (policy.parkingDrawFrequency === "CUSTOM" && policy.drawIntervalMonths ? `Sorteio a cada ${policy.drawIntervalMonths} mes(es)` : policy.parkingDrawFrequency === "MONTHLY" ? "Sorteio mensal" : policy.parkingDrawFrequency === "SEMIANNUAL" ? "Sorteio semestral" : policy.parkingDrawFrequency === "YEARLY" ? "Sorteio anual" : "Sorteio trimestral")
    : "Política de vagas fixas/manuais";
  const availableSpots = useMemo(() => spots.filter((spot) => spot.active), [spots]);

  async function loadAll() {
    try {
      setLoading(true);
      setError(null);
      const [spotsRes, drawsRes] = await Promise.all([
        api.get("/api/parking/spots", { params: { size: 100, condominiumId } }),
        api.get("/api/parking/draws", { params: { size: 20, condominiumId } }),
      ]);
      const loadedSpots: ParkingSpot[] = spotsRes.data.content ?? [];
      setSpots(loadedSpots);
      setDraws(drawsRes.data.content ?? []);

      if (condominiumId) {
        const [condoRes, unitsRes] = await Promise.all([
          api.get(`/condominiums/${condominiumId}`),
          api.get("/units", { params: { condominiumId, condoId: condominiumId, page: 0, pageSize: 500, sortBy: "number", sortDir: "asc" } }),
        ]);
        setPolicy({
          parkingPolicyMode: condoRes.data.parkingPolicyMode ?? "DRAW",
          parkingDrawFrequency: condoRes.data.parkingDrawFrequency ?? "QUARTERLY",
          drawIntervalMonths: condoRes.data.drawIntervalMonths ?? null,
          allowManualAssignments: condoRes.data.allowManualAssignments ?? true,
          allowResidentRegistration: condoRes.data.allowResidentRegistration ?? true,
          maxVehiclesPerUnit: condoRes.data.maxVehiclesPerUnit ?? 1,
          parkingRules: condoRes.data.parkingRules ?? "",
        });
        const rawUnits = unitsRes.data;
        const unitItems = Array.isArray(rawUnits.content) ? rawUnits.content : Array.isArray(rawUnits.items) ? rawUnits.items : [];
        setUnits(unitItems.map((unit: any) => ({ id: unit.id, number: unit.number, block: unit.block ?? null })));
      } else {
        setUnits([]);
      }

      if (isMorador) {
        const myRes = await api.get("/api/parking/my-assignment", { params: { condominiumId } });
        const assignment = myRes.data.assignment;
        const spot = assignment ? loadedSpots.find((item) => item.id === assignment.spotId) : null;
        setMyAssignment(assignment ? { ...assignment, spotCode: spot?.code ?? assignment.spotCode } : null);
      } else if (isManager) {
        const assignRes = await api.get("/api/parking/assignments", { params: { condominiumId } });
        setAllAssignments(Array.isArray(assignRes.data) ? assignRes.data : []);
      }
    } catch {
      const message = "Falha ao carregar dados de vagas";
      setError(message);
      toast.show({ type: "error", msg: message });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadAll(); /* eslint-disable-next-line */ }, [selectedCondominiumId, currentUser?.condominiumId]);

  async function loadRegistrations(drawId: number) {
    if (registrations.has(drawId)) return;
    try {
      const res = await api.get(`/api/parking/draws/${drawId}/registrations`);
      setRegistrations((prev) => new Map(prev).set(drawId, Array.isArray(res.data) ? res.data : []));
    } catch {
      toast.show({ type: "error", msg: "Erro ao carregar inscrições do sorteio" });
    }
  }

  async function handleRegister(drawId: number) {
    try {
      await api.post(`/api/parking/draws/${drawId}/register`);
      toast.show({ type: "success", msg: "Inscrição realizada!" });
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao se inscrever" });
    }
  }

  async function handleExecuteDraw(drawId: number) {
    if (!confirm("Confirmar execução do sorteio? Esta ação não pode ser desfeita.")) return;
    try {
      await api.post(`/api/parking/draws/${drawId}/execute`);
      toast.show({ type: "success", msg: "Sorteio executado com sucesso!" });
      loadAll();
      setRegistrations((prev) => {
        const map = new Map(prev);
        map.delete(drawId);
        return map;
      });
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao executar sorteio" });
    }
  }

  async function handleCreateDraw() {
    if (isSuperuser && !condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para criar o sorteio." });
      return;
    }
    try {
      setSaving(true);
      await api.post("/api/parking/draws", {
        condominiumId,
        name: drawForm.name,
        registrationOpenAt: new Date(drawForm.regOpen).toISOString(),
        registrationCloseAt: new Date(drawForm.regClose).toISOString(),
        validFrom: drawForm.validFrom,
        validUntil: drawForm.validUntil,
      });
      toast.show({ type: "success", msg: "Sorteio criado!" });
      setShowDrawModal(false);
      setDrawForm({ name: "", regOpen: "", regClose: "", validFrom: "", validUntil: "" });
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao criar sorteio" });
    } finally {
      setSaving(false);
    }
  }

  async function handleSaveSpot() {
    if (isSuperuser && !condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para criar a vaga." });
      return;
    }
    try {
      setSaving(true);
      if (editingSpot) {
        await api.put(`/api/parking/spots/${editingSpot.id}`, { ...spotForm, active: true });
        toast.show({ type: "success", msg: "Vaga atualizada!" });
      } else {
        await api.post("/api/parking/spots", { ...spotForm, condominiumId });
        toast.show({ type: "success", msg: "Vaga criada!" });
      }
      setShowSpotModal(false);
      setSpotForm({ code: "", description: "" });
      setEditingSpot(null);
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao salvar vaga" });
    } finally {
      setSaving(false);
    }
  }

  async function handleSavePolicy() {
    if (!condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para salvar a política." });
      return;
    }
    try {
      setSaving(true);
      await api.put(`/condominiums/${condominiumId}`, policy);
      toast.show({ type: "success", msg: "Política de vagas atualizada!" });
      setShowPolicyModal(false);
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao salvar política de vagas" });
    } finally {
      setSaving(false);
    }
  }

  function openAssignmentModal(assignment?: Assignment) {
    if (isSuperuser && !condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para gerenciar atribuições." });
      return;
    }
    if (policy.allowManualAssignments === false) {
      toast.show({ type: "error", msg: "A política deste condomínio não permite atribuições manuais." });
      return;
    }
    setEditingAssignment(assignment ?? null);
    setAssignmentForm({
      spotId: assignment?.spotId ? String(assignment.spotId) : "",
      unitId: assignment?.unitId ? String(assignment.unitId) : "",
      validFrom: assignment?.validFrom ?? "",
      validUntil: assignment?.validUntil ?? "",
    });
    setShowAssignmentModal(true);
  }

  async function handleSaveAssignment() {
    if (isSuperuser && !condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para gerenciar atribuições." });
      return;
    }
    try {
      setSaving(true);
      const payload = {
        condominiumId,
        spotId: Number(assignmentForm.spotId),
        unitId: Number(assignmentForm.unitId),
        validFrom: assignmentForm.validFrom,
        validUntil: assignmentForm.validUntil,
      };
      if (editingAssignment) {
        await api.patch(`/api/parking/assignments/${editingAssignment.id}`, payload);
        toast.show({ type: "success", msg: "Atribuição atualizada!" });
      } else {
        await api.post("/api/parking/assignments", payload);
        toast.show({ type: "success", msg: "Atribuição criada!" });
      }
      setShowAssignmentModal(false);
      setEditingAssignment(null);
      setAssignmentForm({ spotId: "", unitId: "", validFrom: "", validUntil: "" });
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao salvar atribuição" });
    } finally {
      setSaving(false);
    }
  }

  async function handleCancelAssignment(id: number) {
    if (!confirm("Cancelar esta atribuição de vaga?")) return;
    try {
      await api.delete(`/api/parking/assignments/${id}`);
      toast.show({ type: "success", msg: "Atribuição cancelada." });
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao cancelar atribuição" });
    }
  }

  return (
    <div className="p-6 max-w-5xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>Vagas de Estacionamento</h1>
          <p className="text-sm text-slate-500 mt-0.5">{policyDescription}</p>
          {isSuperuser && selectedCondoName && <p className="text-xs text-slate-400 mt-1">Condomínio atual: {selectedCondoName}</p>}
        </div>
        {isManager && (
          <div className="flex gap-2">
            <button onClick={() => setShowPolicyModal(true)} disabled={isSuperuser && !condominiumId} className="border border-slate-200 text-slate-700 hover:bg-slate-50 px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50">Política de vagas</button>
            <button onClick={() => { setEditingSpot(null); setSpotForm({ code: "", description: "" }); setShowSpotModal(true); }} disabled={isSuperuser && !condominiumId} className="border border-slate-200 text-slate-700 hover:bg-slate-50 px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50">+ Nova vaga</button>
            {isDrawMode && <button onClick={() => setShowDrawModal(true)} disabled={isSuperuser && !condominiumId} className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors shadow-sm disabled:opacity-50">+ Novo sorteio</button>}
          </div>
        )}
      </div>

      {isSuperuser && (
        <div className="mb-5 max-w-sm">
          <label className="block text-sm font-medium text-slate-700 mb-1.5">Condomínio</label>
          <select value={selectedCondominiumId} onChange={(e) => setSelectedCondominiumId(e.target.value)} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm">
            <option value="">Todos os condomínios</option>
            {condos.map((condo) => <option key={condo.id} value={String(condo.id)}>{condo.name}</option>)}
          </select>
        </div>
      )}

      {isMorador && myAssignment && (
        <div className="mb-5 bg-indigo-50 border border-indigo-100 rounded-2xl p-5">
          <p className="text-xs font-semibold text-indigo-500 uppercase tracking-wide mb-1">Sua vaga atual</p>
          <p className="text-3xl font-bold text-indigo-700" style={{ fontFamily: "var(--font-display)" }}>{myAssignment.spotCode}</p>
          <p className="text-sm text-indigo-500 mt-0.5">Válida até {new Date(myAssignment.validUntil).toLocaleDateString("pt-BR")}</p>
        </div>
      )}

      {isMorador && !myAssignment && !loading && (
        <div className="mb-5 bg-slate-50 border border-slate-200 rounded-2xl p-5 text-center text-slate-500 text-sm">
          {isDrawMode ? "Você não possui vaga atribuída no momento. Acompanhe os sorteios abaixo." : "Você não possui vaga atribuída no momento."}
        </div>
      )}

      {isManager && (
        <div className="flex gap-1 mb-5 bg-slate-100 rounded-lg p-1 w-fit">
          {(["draws", "spots", "assignments"] as const).map((tab) => (
            <button key={tab} onClick={() => setActiveTab(tab)} className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${activeTab === tab ? "bg-white shadow-sm text-slate-900" : "text-slate-500 hover:text-slate-700"}`}>
              {tab === "draws" ? "Sorteios" : tab === "spots" ? "Vagas" : "Atribuições"}
            </button>
          ))}
        </div>
      )}

      {loading ? (
        <div className="space-y-3">{[1, 2].map((i) => <div key={i} className="bg-white rounded-xl border border-slate-100 h-20 animate-pulse" />)}</div>
      ) : error ? (
        <div className="bg-white rounded-xl border border-rose-200 p-10 text-center">
          <p className="text-sm font-medium text-rose-700">Não foi possível carregar as vagas.</p>
          <p className="text-sm text-rose-500 mt-1">{error}</p>
        </div>
      ) : (
        <>
          {isDrawMode && (isMorador || activeTab === "draws") && (
            <div className="space-y-3">
              {draws.length === 0 ? (
                <div className="bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">Nenhum sorteio cadastrado.</div>
              ) : draws.map((draw) => {
                const info = DRAW_STATUS[draw.status];
                const drawRegistrations = registrations.get(draw.id) ?? [];
                return (
                  <div key={draw.id} className="bg-white rounded-xl border border-slate-100 shadow-sm p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className="font-medium text-slate-900 text-sm">{draw.name}</span>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${info.color}`}>{info.label}</span>
                        </div>
                        {isSuperuser && !condominiumId && <p className="text-xs text-slate-400 mb-1">{condoNameById.get(draw.condominiumId) ?? `Condomínio #${draw.condominiumId}`}</p>}
                        <p className="text-xs text-slate-500">Inscrições: {new Date(draw.registrationOpenAt).toLocaleDateString("pt-BR")} → {new Date(draw.registrationCloseAt).toLocaleDateString("pt-BR")}</p>
                        <p className="text-xs text-slate-400">Vigência: {draw.validFrom} → {draw.validUntil}</p>
                      </div>
                      <div className="flex gap-1.5 flex-shrink-0">
                        {draw.status === "OPEN" && isMorador && policy.allowResidentRegistration !== false && <button onClick={() => handleRegister(draw.id)} className="text-xs px-3 py-1.5 rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 font-medium">Me inscrever</button>}
                        {isManager && draw.status === "OPEN" && <button onClick={() => loadRegistrations(draw.id)} className="text-xs px-3 py-1.5 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50">Ver inscrições</button>}
                        {isManager && (draw.status === "OPEN" || draw.status === "CLOSED") && <button onClick={() => handleExecuteDraw(draw.id)} className="text-xs px-3 py-1.5 rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 font-medium">Executar sorteio</button>}
                      </div>
                    </div>
                    {registrations.has(draw.id) && (
                      <div className="mt-3 pt-3 border-t border-slate-100 space-y-2">
                        <p className="text-xs text-slate-500">{drawRegistrations.length} inscrição(ões)</p>
                        {drawRegistrations.length === 0 ? (
                          <p className="text-xs text-slate-400">Nenhuma unidade inscrita.</p>
                        ) : drawRegistrations.map((registration) => (
                          <div key={registration.id} className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
                            <div>
                              <p className="text-sm font-medium text-slate-800">{registration.unitLabel ?? `Unidade #${registration.unitId}`}</p>
                              <p className="text-xs text-slate-500">{registration.residentName || "Morador não identificado"} • {new Date(registration.registeredAt).toLocaleString("pt-BR")}</p>
                            </div>
                            {registration.hasActiveAssignment && (
                              <span className="text-[11px] px-2 py-1 rounded-full bg-amber-100 text-amber-700 font-medium">Já possui vaga ativa</span>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {isManager && activeTab === "spots" && (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
              {spots.map((spot) => (
                <div key={spot.id} className="bg-white rounded-xl border border-slate-100 shadow-sm p-4 flex items-center justify-between">
                  <div>
                    <p className="font-bold text-xl text-slate-800" style={{ fontFamily: "var(--font-display)" }}>{spot.code}</p>
                    {spot.description && <p className="text-xs text-slate-400">{spot.description}</p>}
                    {isSuperuser && !condominiumId && <p className="text-xs text-slate-400">{condoNameById.get(spot.condominiumId) ?? `Condomínio #${spot.condominiumId}`}</p>}
                    {!spot.active && <span className="text-xs text-rose-500">Inativa</span>}
                  </div>
                  <button onClick={() => { setEditingSpot(spot); setSpotForm({ code: spot.code, description: spot.description ?? "" }); setShowSpotModal(true); }} className="text-xs text-indigo-600 hover:text-indigo-700">Editar</button>
                </div>
              ))}
            </div>
          )}

          {isManager && activeTab === "assignments" && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-semibold text-slate-800">Atribuições de vagas</h2>
                  <p className="text-xs text-slate-500 mt-1">Gerencie atribuições manuais com vaga, unidade e vigência.</p>
                </div>
                <button
                  type="button"
                  onClick={() => openAssignmentModal()}
                  disabled={(isSuperuser && !condominiumId) || policy.allowManualAssignments === false}
                  className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50"
                >
                  + Nova atribuição
                </button>
              </div>

              {policy.allowManualAssignments === false && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                  A política atual deste condomínio não permite atribuições manuais.
                </div>
              )}

              {allAssignments.length === 0 ? (
                <div className="bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">Nenhuma atribuição ativa.</div>
              ) : allAssignments.map((assignment) => (
                <div key={assignment.id} className="bg-white rounded-xl border border-slate-100 p-4 flex items-start justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-slate-800">{assignment.spotCode ?? `#${assignment.spotId}`}</span>
                      <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">{assignment.status}</span>
                      {assignment.drawName && <span className="text-xs px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-600 font-medium">Origem: {assignment.drawName}</span>}
                    </div>
                    <p className="text-sm text-slate-600 mt-1">{assignment.unitLabel ?? `Unidade #${assignment.unitId}`}{assignment.residentName ? ` • ${assignment.residentName}` : ""}</p>
                    {assignment.spotDescription && <p className="text-xs text-slate-400 mt-1">{assignment.spotDescription}</p>}
                    <p className="text-xs text-slate-400 mt-1">Vigência: {new Date(`${assignment.validFrom}T00:00:00`).toLocaleDateString("pt-BR")} → {new Date(`${assignment.validUntil}T00:00:00`).toLocaleDateString("pt-BR")}</p>
                    {isSuperuser && !condominiumId && <p className="text-xs text-slate-400 mt-1">{condoNameById.get(assignment.condominiumId) ?? `Condomínio #${assignment.condominiumId}`}</p>}
                  </div>
                  <div className="flex items-center gap-2">
                    <button type="button" onClick={() => openAssignmentModal(assignment)} className="text-xs px-3 py-1.5 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50">Editar</button>
                    <button type="button" onClick={() => handleCancelAssignment(assignment.id)} className="text-xs px-3 py-1.5 rounded-lg border border-rose-200 text-rose-700 hover:bg-rose-50">Revogar</button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      <Modal open={showDrawModal} onClose={() => setShowDrawModal(false)} title="Novo Sorteio" footer={<><button onClick={() => setShowDrawModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button><button onClick={handleCreateDraw} disabled={saving || !drawForm.name || (isSuperuser && !condominiumId)} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50">{saving ? "Salvando…" : "Criar Sorteio"}</button></>}>
        <div className="space-y-4">
          {isSuperuser && !condominiumId && <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">Selecione um condomínio para criar o sorteio.</div>}
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Nome do Sorteio *</label><input value={drawForm.name} onChange={(e) => setDrawForm({ ...drawForm, name: e.target.value })} placeholder="Ex: Sorteio Q1 2026" className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20" /></div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Abertura inscrições</label><input type="datetime-local" value={drawForm.regOpen} onChange={(e) => setDrawForm({ ...drawForm, regOpen: e.target.value })} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20" /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Encerramento inscrições</label><input type="datetime-local" value={drawForm.regClose} onChange={(e) => setDrawForm({ ...drawForm, regClose: e.target.value })} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20" /></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Início vigência</label><input type="date" value={drawForm.validFrom} onChange={(e) => setDrawForm({ ...drawForm, validFrom: e.target.value })} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20" /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Fim vigência</label><input type="date" value={drawForm.validUntil} onChange={(e) => setDrawForm({ ...drawForm, validUntil: e.target.value })} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20" /></div>
          </div>
        </div>
      </Modal>

      <Modal open={showPolicyModal} onClose={() => setShowPolicyModal(false)} title="Política de Vagas" footer={<><button onClick={() => setShowPolicyModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button><button onClick={handleSavePolicy} disabled={saving || (isSuperuser && !condominiumId)} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50">{saving ? "Salvando..." : "Salvar política"}</button></>}>
        <div className="space-y-4">
          {isSuperuser && !condominiumId && <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">Selecione um condomínio para editar a política de vagas.</div>}
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Modo</label><select value={policy.parkingPolicyMode} onChange={(e) => setPolicy((state) => ({ ...state, parkingPolicyMode: e.target.value as ParkingPolicy["parkingPolicyMode"] }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"><option value="DRAW">Sorteio</option><option value="FIXED">Vaga fixa/manual</option></select></div>
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Frequência</label><select value={policy.parkingDrawFrequency} onChange={(e) => setPolicy((state) => ({ ...state, parkingDrawFrequency: e.target.value as ParkingPolicy["parkingDrawFrequency"] }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" disabled={!isDrawMode}><option value="MONTHLY">Mensal</option><option value="QUARTERLY">Trimestral</option><option value="SEMIANNUAL">Semestral</option><option value="YEARLY">Anual</option><option value="CUSTOM">Personalizado</option></select></div>
          {policy.parkingDrawFrequency === "CUSTOM" && <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Intervalo em meses</label><input type="number" min={1} value={policy.drawIntervalMonths ?? ""} onChange={(e) => setPolicy((state) => ({ ...state, drawIntervalMonths: e.target.value ? Number(e.target.value) : null }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" /></div>}
          <div className="grid grid-cols-2 gap-3">
            <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={policy.allowManualAssignments !== false} onChange={(e) => setPolicy((state) => ({ ...state, allowManualAssignments: e.target.checked }))} />Permitir atribuições manuais</label>
            <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={policy.allowResidentRegistration !== false} onChange={(e) => setPolicy((state) => ({ ...state, allowResidentRegistration: e.target.checked }))} />Permitir inscrição de moradores</label>
          </div>
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Máximo de veículos por unidade</label><input type="number" min={1} value={policy.maxVehiclesPerUnit ?? 1} onChange={(e) => setPolicy((state) => ({ ...state, maxVehiclesPerUnit: Number(e.target.value || 1) }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" /></div>
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Regras</label><textarea value={policy.parkingRules ?? ""} onChange={(e) => setPolicy((state) => ({ ...state, parkingRules: e.target.value }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm min-h-24" /></div>
        </div>
      </Modal>

      <Modal open={showSpotModal} onClose={() => setShowSpotModal(false)} title={editingSpot ? "Editar Vaga" : "Nova Vaga"} footer={<><button onClick={() => setShowSpotModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button><button onClick={handleSaveSpot} disabled={saving || !spotForm.code || (isSuperuser && !condominiumId)} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50">{saving ? "Salvando…" : "Salvar"}</button></>}>
        <div className="space-y-4">
          {isSuperuser && !condominiumId && <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">Selecione um condomínio para criar a vaga.</div>}
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Código da Vaga *</label><input value={spotForm.code} onChange={(e) => setSpotForm({ ...spotForm, code: e.target.value })} placeholder="Ex: A12, B-03" className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20" /></div>
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Descrição</label><input value={spotForm.description} onChange={(e) => setSpotForm({ ...spotForm, description: e.target.value })} placeholder="Ex: Subsolo nível 1" className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20" /></div>
        </div>
      </Modal>

      <Modal open={showAssignmentModal} onClose={() => setShowAssignmentModal(false)} title={editingAssignment ? "Editar atribuição" : "Nova atribuição"} footer={<><button onClick={() => setShowAssignmentModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button><button onClick={handleSaveAssignment} disabled={saving || !assignmentForm.spotId || !assignmentForm.unitId || !assignmentForm.validFrom || !assignmentForm.validUntil} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50">{saving ? "Salvando…" : editingAssignment ? "Atualizar" : "Criar atribuição"}</button></>}>
        <div className="space-y-4">
          {policy.allowManualAssignments === false && <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">Este condomínio não permite atribuições manuais.</div>}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Vaga</label>
            <select value={assignmentForm.spotId} onChange={(e) => setAssignmentForm((current) => ({ ...current, spotId: e.target.value }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm">
              <option value="">Selecione uma vaga…</option>
              {availableSpots.map((spot) => <option key={spot.id} value={String(spot.id)}>{spot.code}{spot.description ? ` • ${spot.description}` : ""}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Unidade</label>
            <select value={assignmentForm.unitId} onChange={(e) => setAssignmentForm((current) => ({ ...current, unitId: e.target.value }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm">
              <option value="">Selecione uma unidade…</option>
              {units.map((unit) => <option key={unit.id} value={String(unit.id)}>{unitLabel(unit)}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Início da vigência</label><input type="date" value={assignmentForm.validFrom} onChange={(e) => setAssignmentForm((current) => ({ ...current, validFrom: e.target.value }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Fim da vigência</label><input type="date" value={assignmentForm.validUntil} onChange={(e) => setAssignmentForm((current) => ({ ...current, validUntil: e.target.value }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" /></div>
          </div>
        </div>
      </Modal>
    </div>
  );
}

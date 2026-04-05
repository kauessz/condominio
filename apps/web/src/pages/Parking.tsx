import { useEffect, useMemo, useState } from "react";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";
import { getUser } from "../lib/auth";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type Condo={id:number;name:string};
type ParkingSpot={id:number;condominiumId:number;code:string;description?:string;active:boolean};
type ParkingDraw={id:number;condominiumId:number;name:string;registrationOpenAt:string;registrationCloseAt:string;validFrom:string;validUntil:string;status:"OPEN"|"CLOSED"|"EXECUTED"|"CANCELLED";executedAt?:string};
type Registration={id:number;drawId:number;condominiumId:number;unitId:number;registeredAt:string};
type Assignment={id:number;condominiumId:number;spotId:number;unitId:number;validFrom:string;validUntil:string;status:string};
type ParkingPolicy={parkingPolicyMode?:"FIXED"|"DRAW";parkingDrawFrequency?:"MONTHLY"|"QUARTERLY"|"SEMIANNUAL"|"YEARLY"|"CUSTOM";drawIntervalMonths?:number|null;allowManualAssignments?:boolean;allowResidentRegistration?:boolean;maxVehiclesPerUnit?:number;parkingRules?:string|null};

const DRAW_STATUS:Record<string,{label:string;color:string}>={
  OPEN:{label:"Inscrições abertas",color:"bg-emerald-100 text-emerald-700"},
  CLOSED:{label:"Inscrições encerradas",color:"bg-amber-100 text-amber-700"},
  EXECUTED:{label:"Executado",color:"bg-indigo-100 text-indigo-700"},
  CANCELLED:{label:"Cancelado",color:"bg-rose-100 text-rose-700"},
};

export default function ParkingPage(){
  const toast=useToast();
  const currentUser=getUser();
  const isManager=["SUPERUSER","ADMIN","SINDICO"].includes(currentUser?.role??"");
  const isMorador=currentUser?.role==="MORADOR";
  const {selectedCondominiumId,setSelectedCondominiumId,isSuperuser}=useSuperadminCondominiumFilter(currentUser);
  const [spots,setSpots]=useState<ParkingSpot[]>([]);
  const [draws,setDraws]=useState<ParkingDraw[]>([]);
  const [myAssignment,setMyAssignment]=useState<(Assignment&{spotCode?:string})|null>(null);
  const [allAssignments,setAllAssignments]=useState<(Assignment&{spotCode?:string})[]>([]);
  const [condos,setCondos]=useState<Condo[]>([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState<string|null>(null);
  const [activeTab,setActiveTab]=useState<"draws"|"spots"|"assignments">("draws");
  const [policy,setPolicy]=useState<ParkingPolicy>({parkingPolicyMode:"DRAW",parkingDrawFrequency:"QUARTERLY",drawIntervalMonths:null,allowManualAssignments:true,allowResidentRegistration:true,maxVehiclesPerUnit:1,parkingRules:""});
  const [showPolicyModal,setShowPolicyModal]=useState(false);
  const [showDrawModal,setShowDrawModal]=useState(false);
  const [drawForm,setDrawForm]=useState({name:"",regOpen:"",regClose:"",validFrom:"",validUntil:""});
  const [showSpotModal,setShowSpotModal]=useState(false);
  const [spotForm,setSpotForm]=useState({code:"",description:""});
  const [editingSpot,setEditingSpot]=useState<ParkingSpot|null>(null);
  const [saving,setSaving]=useState(false);
  const [registrations,setRegistrations]=useState<Map<number,Registration[]>>(new Map());
  const condominiumId=isSuperuser?(selectedCondominiumId?Number(selectedCondominiumId):undefined):(currentUser?.condominiumId?Number(currentUser.condominiumId):undefined);

  useEffect(()=>{if(!isSuperuser)return;api.get("/condominiums",{params:{pageSize:100}}).then((res)=>{const raw=res.data;const list:Array<Condo>=Array.isArray(raw.content)?raw.content:Array.isArray(raw.items)?raw.items:Array.isArray(raw)?raw:[];setCondos(list);}).catch(()=>setCondos([]));},[isSuperuser]);
  const condoNameById=useMemo(()=>new Map(condos.map((condo)=>[condo.id,condo.name])),[condos]);

  async function loadAll(){
    try{
      setLoading(true);setError(null);
      const [spotsRes,drawsRes]=await Promise.all([
        api.get("/api/parking/spots",{params:{size:100,condominiumId}}),
        api.get("/api/parking/draws",{params:{size:20,condominiumId}})
      ]);
      const loadedSpots:ParkingSpot[]=spotsRes.data.content??[];
      setSpots(loadedSpots);setDraws(drawsRes.data.content??[]);
      if(condominiumId){
        const condoRes=await api.get(`/condominiums/${condominiumId}`);
        setPolicy({
          parkingPolicyMode:condoRes.data.parkingPolicyMode??"DRAW",
          parkingDrawFrequency:condoRes.data.parkingDrawFrequency??"QUARTERLY",
          drawIntervalMonths:condoRes.data.drawIntervalMonths??null,
          allowManualAssignments:condoRes.data.allowManualAssignments??true,
          allowResidentRegistration:condoRes.data.allowResidentRegistration??true,
          maxVehiclesPerUnit:condoRes.data.maxVehiclesPerUnit??1,
          parkingRules:condoRes.data.parkingRules??"",
        });
      }
      if(isMorador){
        const myRes=await api.get("/api/parking/my-assignment",{params:{condominiumId}});
        const assignment=myRes.data.assignment;
        if(assignment){
          const spot=loadedSpots.find((item)=>item.id===assignment.spotId);
          setMyAssignment({...assignment,spotCode:spot?.code});
        }else setMyAssignment(null);
      }else if(isManager){
        const assignRes=await api.get("/api/parking/assignments",{params:{condominiumId}});
        setAllAssignments((assignRes.data??[]).map((assignment:Assignment)=>({...assignment,spotCode:loadedSpots.find((item)=>item.id===assignment.spotId)?.code})));
      }
    }catch{
      const message="Falha ao carregar dados de vagas";
      setError(message);toast.show({type:"error",msg:message});
    }finally{setLoading(false);}
  }

  useEffect(()=>{loadAll();/* eslint-disable-next-line */},[selectedCondominiumId,currentUser?.condominiumId]);

  async function loadRegistrations(drawId:number){if(registrations.has(drawId))return;try{const res=await api.get(`/api/parking/draws/${drawId}/registrations`);setRegistrations((prev)=>new Map(prev).set(drawId,res.data??[]));}catch{/* noop */}}
  async function handleRegister(drawId:number){try{await api.post(`/api/parking/draws/${drawId}/register`);toast.show({type:"success",msg:"Inscrição realizada!"});loadAll();}catch(err:any){toast.show({type:"error",msg:err?.response?.data?.message||"Erro ao se inscrever"});}}
  async function handleExecuteDraw(drawId:number){if(!confirm("Confirmar execução do sorteio? Esta ação não pode ser desfeita."))return;try{await api.post(`/api/parking/draws/${drawId}/execute`);toast.show({type:"success",msg:"Sorteio executado com sucesso!"});loadAll();}catch(err:any){toast.show({type:"error",msg:err?.response?.data?.message||"Erro ao executar sorteio"});}}
  async function handleCreateDraw(){if(isSuperuser&&!condominiumId){toast.show({type:"error",msg:"Selecione um condomínio para criar o sorteio."});return;}try{setSaving(true);await api.post("/api/parking/draws",{condominiumId,name:drawForm.name,registrationOpenAt:new Date(drawForm.regOpen).toISOString(),registrationCloseAt:new Date(drawForm.regClose).toISOString(),validFrom:drawForm.validFrom,validUntil:drawForm.validUntil});toast.show({type:"success",msg:"Sorteio criado!"});setShowDrawModal(false);setDrawForm({name:"",regOpen:"",regClose:"",validFrom:"",validUntil:""});loadAll();}catch(err:any){toast.show({type:"error",msg:err?.response?.data?.message||"Erro ao criar sorteio"});}finally{setSaving(false);}}
  async function handleSaveSpot(){if(isSuperuser&&!condominiumId){toast.show({type:"error",msg:"Selecione um condomínio para criar a vaga."});return;}try{setSaving(true);if(editingSpot){await api.put(`/api/parking/spots/${editingSpot.id}`,{...spotForm,active:true});toast.show({type:"success",msg:"Vaga atualizada!"});}else{await api.post("/api/parking/spots",{...spotForm,condominiumId});toast.show({type:"success",msg:"Vaga criada!"});}setShowSpotModal(false);setSpotForm({code:"",description:""});setEditingSpot(null);loadAll();}catch(err:any){toast.show({type:"error",msg:err?.response?.data?.message||"Erro ao salvar vaga"});}finally{setSaving(false);}}
  async function handleSavePolicy(){if(!condominiumId){toast.show({type:"error",msg:"Selecione um condomínio para salvar a política."});return;}try{setSaving(true);await api.put(`/condominiums/${condominiumId}`,policy);toast.show({type:"success",msg:"Politica de vagas atualizada!"});setShowPolicyModal(false);loadAll();}catch(err:any){toast.show({type:"error",msg:err?.response?.data?.message||"Erro ao salvar politica de vagas"});}finally{setSaving(false);}}

  const isDrawMode=policy.parkingPolicyMode!=="FIXED";
  const selectedCondoName=condominiumId?condoNameById.get(condominiumId):null;
  const policyDescription=isDrawMode?(policy.parkingDrawFrequency==="CUSTOM"&&policy.drawIntervalMonths?`Sorteio a cada ${policy.drawIntervalMonths} mes(es)`:policy.parkingDrawFrequency==="MONTHLY"?"Sorteio mensal":policy.parkingDrawFrequency==="SEMIANNUAL"?"Sorteio semestral":policy.parkingDrawFrequency==="YEARLY"?"Sorteio anual":"Sorteio trimestral"):"Politica de vagas fixas/manuais";

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
            <button onClick={() => setShowPolicyModal(true)} disabled={isSuperuser && !condominiumId} className="border border-slate-200 text-slate-700 hover:bg-slate-50 px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50">Politica de vagas</button>
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
          <p className="text-sm font-medium text-rose-700">Nao foi possivel carregar as vagas.</p>
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
                    {registrations.has(draw.id) && <div className="mt-3 pt-3 border-t border-slate-100"><p className="text-xs text-slate-500">{registrations.get(draw.id)!.length} inscrição(ões)</p></div>}
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
            <div className="space-y-2">
              {allAssignments.length === 0 ? (
                <div className="bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">Nenhuma atribuição ativa.</div>
              ) : allAssignments.map((assignment) => (
                <div key={assignment.id} className="bg-white rounded-xl border border-slate-100 p-3 flex items-center justify-between">
                  <div>
                    <span className="font-bold text-slate-800">{assignment.spotCode ?? `#${assignment.spotId}`}</span>
                    <span className="text-slate-400 text-xs mx-2">→</span>
                    <span className="text-sm text-slate-600">Unidade #{assignment.unitId}</span>
                    {isSuperuser && !condominiumId && <span className="text-xs text-slate-400 block mt-1">{condoNameById.get(assignment.condominiumId) ?? `Condomínio #${assignment.condominiumId}`}</span>}
                  </div>
                  <span className="text-xs text-slate-400">até {new Date(assignment.validUntil).toLocaleDateString("pt-BR")}</span>
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

      <Modal open={showPolicyModal} onClose={() => setShowPolicyModal(false)} title="Politica de Vagas" footer={<><button onClick={() => setShowPolicyModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button><button onClick={handleSavePolicy} disabled={saving || (isSuperuser && !condominiumId)} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50">{saving ? "Salvando..." : "Salvar politica"}</button></>}>
        <div className="space-y-4">
          {isSuperuser && !condominiumId && <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">Selecione um condomínio para editar a política de vagas.</div>}
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Modo</label><select value={policy.parkingPolicyMode} onChange={(e) => setPolicy((state) => ({ ...state, parkingPolicyMode: e.target.value as ParkingPolicy["parkingPolicyMode"] }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"><option value="DRAW">Sorteio</option><option value="FIXED">Vaga fixa/manual</option></select></div>
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Frequencia</label><select value={policy.parkingDrawFrequency} onChange={(e) => setPolicy((state) => ({ ...state, parkingDrawFrequency: e.target.value as ParkingPolicy["parkingDrawFrequency"] }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" disabled={!isDrawMode}><option value="MONTHLY">Mensal</option><option value="QUARTERLY">Trimestral</option><option value="SEMIANNUAL">Semestral</option><option value="YEARLY">Anual</option><option value="CUSTOM">Personalizado</option></select></div>
          {policy.parkingDrawFrequency === "CUSTOM" && <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Intervalo em meses</label><input type="number" min={1} value={policy.drawIntervalMonths ?? ""} onChange={(e) => setPolicy((state) => ({ ...state, drawIntervalMonths: e.target.value ? Number(e.target.value) : null }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" /></div>}
          <div className="grid grid-cols-2 gap-3">
            <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={policy.allowManualAssignments !== false} onChange={(e) => setPolicy((state) => ({ ...state, allowManualAssignments: e.target.checked }))} />Permitir atribuicoes manuais</label>
            <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={policy.allowResidentRegistration !== false} onChange={(e) => setPolicy((state) => ({ ...state, allowResidentRegistration: e.target.checked }))} />Permitir inscricao de moradores</label>
          </div>
          <div><label className="block text-sm font-medium text-slate-700 mb-1.5">Maximo de veiculos por unidade</label><input type="number" min={1} value={policy.maxVehiclesPerUnit ?? 1} onChange={(e) => setPolicy((state) => ({ ...state, maxVehiclesPerUnit: Number(e.target.value || 1) }))} className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm" /></div>
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
    </div>
  );
}

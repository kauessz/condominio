import { useCallback, useEffect, useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
import api from "../lib/api";
import { canAccessModule, getUser, getRoleLabel } from "../lib/auth";
import { auditModuleOptions, getAuditPresentation, type AuditItem } from "../lib/audit";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";
import { useToast } from "../components/Toast";

type Condo = { id: number; name: string };

const MODULE_BADGE: Record<string, string> = {
  VISITORS: "bg-amber-50 text-amber-700",
  RESERVATIONS: "bg-blue-50 text-blue-700",
  ASSEMBLIES: "bg-emerald-50 text-emerald-700",
  PARKING: "bg-indigo-50 text-indigo-700",
  FINANCIAL: "bg-cyan-50 text-cyan-700",
  CONDOMINIUMS: "bg-violet-50 text-violet-700",
  USERS: "bg-slate-100 text-slate-700",
  SYSTEM: "bg-slate-100 text-slate-600",
};

export default function Audit() {
  const toast = useToast();
  const currentUser = getUser();
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);

  const [items, setItems] = useState<AuditItem[]>([]);
  const [condos, setCondos] = useState<Condo[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [moduleFilter, setModuleFilter] = useState("");
  const [actorFilter, setActorFilter] = useState("");
  const [query, setQuery] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const pageSize = 20;
  const condominiumId = isSuperuser ? selectedCondominiumId : String(currentUser?.condominiumId ?? "");

  const canView = canAccessModule("audit");

  useEffect(() => {
    if (!isSuperuser) {
      return;
    }
    api.get("/condominiums", { params: { pageSize: 100 } })
      .then((response) => {
        const raw = response.data;
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

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setLoadError(null);
      const response = await api.get("/audit", {
        params: {
          page,
          size: pageSize,
          module: moduleFilter || undefined,
          actor: actorFilter || undefined,
          q: query || undefined,
          condominiumId: condominiumId ? Number(condominiumId) : undefined,
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
        },
      });
      const raw = response.data;
      const content: AuditItem[] = Array.isArray(raw.content) ? raw.content : [];
      setItems(content);
      setTotalPages(Math.max(1, raw.totalPages ?? 1));
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.response?.data?.error || "Falha ao carregar auditoria.";
      setLoadError(message);
      setItems([]);
      toast.show({ type: "error", msg: message });
    } finally {
      setLoading(false);
    }
  }, [actorFilter, condominiumId, from, moduleFilter, page, pageSize, query, to, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectedCondoName = useMemo(() => {
    if (!condominiumId) {
      return "Todos os condomínios";
    }
    return condos.find((condo) => String(condo.id) === condominiumId)?.name ?? `Condomínio #${condominiumId}`;
  }, [condominiumId, condos]);

  if (!canView) {
    return <Navigate to="/app/dashboard" replace />;
  }

  return (
    <div className="p-6 max-w-6xl">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
          Auditoria
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Linha do tempo operacional para Visitantes, Reservas, Assembleias, Vagas e Financeiro.
          {!isSuperuser && currentUser?.condominiumId ? ` Escopo atual: ${selectedCondoName}.` : ""}
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4 mb-5">
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
          {isSuperuser && (
            <label className="text-sm text-slate-600">
              Condomínio
              <select
                value={selectedCondominiumId}
                onChange={(event) => {
                  setSelectedCondominiumId(event.target.value);
                  setPage(0);
                }}
                className="mt-1 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              >
                <option value="">Todos os condomínios</option>
                {condos.map((condo) => (
                  <option key={condo.id} value={String(condo.id)}>{condo.name}</option>
                ))}
              </select>
            </label>
          )}

          <label className="text-sm text-slate-600">
            Módulo
            <select
              value={moduleFilter}
              onChange={(event) => {
                setModuleFilter(event.target.value);
                setPage(0);
              }}
              className="mt-1 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            >
              {auditModuleOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>

          <label className="text-sm text-slate-600">
            Usuário
            <input
              value={actorFilter}
              onChange={(event) => {
                setActorFilter(event.target.value);
                setPage(0);
              }}
              placeholder="Nome ou e-mail"
              className="mt-1 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            />
          </label>

          <label className="text-sm text-slate-600">
            De
            <input
              type="date"
              value={from}
              onChange={(event) => {
                setFrom(event.target.value);
                setPage(0);
              }}
              className="mt-1 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            />
          </label>

          <label className="text-sm text-slate-600">
            Até
            <input
              type="date"
              value={to}
              onChange={(event) => {
                setTo(event.target.value);
                setPage(0);
              }}
              className="mt-1 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            />
          </label>
        </div>

        <div className="mt-3 flex flex-col md:flex-row gap-3">
          <input
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            placeholder="Buscar por descrição, entidade ou ação"
            className="border border-slate-200 rounded-lg px-3 py-2 text-sm flex-1"
          />
          <button
            type="button"
            onClick={() => {
              setModuleFilter("");
              setActorFilter("");
              setQuery("");
              setFrom("");
              setTo("");
              if (isSuperuser) {
                setSelectedCondominiumId("");
              }
              setPage(0);
            }}
            className="border border-slate-200 rounded-lg px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 transition-colors"
          >
            Limpar filtros
          </button>
        </div>
      </div>

      {loading ? (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-8 animate-pulse h-72" />
      ) : loadError ? (
        <div className="bg-white rounded-2xl border border-rose-100 shadow-sm p-8 text-center">
          <h2 className="text-sm font-semibold text-slate-800">Não foi possível carregar a auditoria</h2>
          <p className="text-xs text-slate-500 mt-1">{loadError}</p>
          <button
            type="button"
            onClick={() => void load()}
            className="mt-4 border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-slate-50 transition-colors"
          >
            Tentar novamente
          </button>
        </div>
      ) : items.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-10 text-center">
          <h2 className="text-sm font-semibold text-slate-800">Nenhum evento encontrado</h2>
          <p className="text-xs text-slate-500 mt-1">Ajuste os filtros ou aguarde novas ações operacionais.</p>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 text-left border-b border-slate-100">
                <th className="px-4 py-3 text-xs uppercase tracking-wide text-slate-500 font-medium">Data/Hora</th>
                <th className="px-4 py-3 text-xs uppercase tracking-wide text-slate-500 font-medium">Módulo</th>
                <th className="px-4 py-3 text-xs uppercase tracking-wide text-slate-500 font-medium">Ação</th>
                <th className="px-4 py-3 text-xs uppercase tracking-wide text-slate-500 font-medium">Descrição</th>
                <th className="px-4 py-3 text-xs uppercase tracking-wide text-slate-500 font-medium">Usuário</th>
                <th className="px-4 py-3 text-xs uppercase tracking-wide text-slate-500 font-medium">Condomínio</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {items.map((item) => {
                const presentation = getAuditPresentation(item);
                return (
                  <tr key={item.id} className="hover:bg-slate-50/70 transition-colors align-top">
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">
                      {new Date(item.createdAt).toLocaleString("pt-BR")}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${MODULE_BADGE[item.module] ?? "bg-slate-100 text-slate-700"}`}>
                        {presentation.moduleLabel}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-xs font-medium">{presentation.actionLabel}</td>
                    <td className="px-4 py-3 text-slate-700 min-w-[320px]">
                      <p className="font-medium text-sm">{presentation.title}</p>
                      <p className="text-xs text-slate-400 mt-1">
                        {presentation.context || `${presentation.moduleLabel} • ${presentation.actionLabel}`}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      <p className="text-sm font-medium text-slate-700">{item.actorName || item.actorEmail || "Sistema"}</p>
                      <p className="text-xs text-slate-400">
                        {item.actorRole ? getRoleLabel(item.actorRole) : "Sem role"}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-slate-600 text-sm">
                      {item.condominiumName || (item.condominiumId != null ? `Condomínio #${item.condominiumId}` : "Global")}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="mt-5 flex items-center gap-3">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-50 transition-colors"
          >
            ← Anterior
          </button>
          <span className="text-sm text-slate-500">Página {page + 1} de {totalPages}</span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-50 transition-colors"
          >
            Próxima →
          </button>
        </div>
      )}
    </div>
  );
}

import { useEffect, useMemo, useState } from "react";
import api from "../lib/api";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";
import { getUser } from "../lib/auth";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type FinancialConfig = {
  id: number;
  monthlyFee: number;
  dueDay: number;
  lateFeePct: number;
  interestPct: number;
  pixKey?: string;
  pixKeyType?: string;
};

type Invoice = {
  id: number;
  condominiumId: number;
  condominiumName?: string;
  unitId: number;
  unitLabel?: string;
  referenceMonth: string;
  chargeType: "CONDOMINIO" | "REFORMA" | "EXTRA" | "FUNDO_RESERVA" | "MULTA" | "OUTROS";
  title: string;
  description?: string;
  amount: number;
  dueDate: string;
  paidAt?: string;
  paidAmount?: number;
  paymentMethod?: string;
  paymentNotes?: string;
  status: "PENDING" | "PAID" | "OVERDUE" | "CANCELLED" | "WAIVED";
  createdAt?: string;
};

type Summary = {
  totalInvoices: number;
  totalAmount: number;
  paidAmount: number;
  pendingAmount: number;
  overdueAmount: number;
  delinquencyPct: number;
};

type Condo = {
  id: number;
  name: string;
};

type UnitOption = {
  id: number;
  number?: string;
  block?: string | null;
  label: string;
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Pendente",
  PAID: "Pago",
  OVERDUE: "Vencido",
  CANCELLED: "Cancelado",
  WAIVED: "Dispensado",
};

const STATUS_COLORS: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  PAID: "bg-emerald-100 text-emerald-700",
  OVERDUE: "bg-rose-100 text-rose-700",
  CANCELLED: "bg-slate-100 text-slate-500",
  WAIVED: "bg-blue-100 text-blue-600",
};

const CHARGE_TYPE_LABELS: Record<string, string> = {
  CONDOMINIO: "Mensalidade ordinária",
  REFORMA: "Rateio de reforma",
  EXTRA: "Taxa extraordinária",
  FUNDO_RESERVA: "Fundo de reserva",
  MULTA: "Multa",
  OUTROS: "Cobrança avulsa",
};

const EMPTY_SUMMARY: Summary = {
  totalInvoices: 0,
  totalAmount: 0,
  paidAmount: 0,
  pendingAmount: 0,
  overdueAmount: 0,
  delinquencyPct: 0,
};

function formatCurrency(value?: number) {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}

function formatDate(value?: string) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString("pt-BR");
}

export default function FinancialPage() {
  const toast = useToast();
  const currentUser = getUser();
  const isManager = ["SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO"].includes(currentUser?.role ?? "");
  const isMorador = currentUser?.role === "MORADOR";
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);

  const [config, setConfig] = useState<FinancialConfig | null>(null);
  const [summary, setSummary] = useState<Summary>(EMPTY_SUMMARY);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [condos, setCondos] = useState<Condo[]>([]);
  const [unitOptions, setUnitOptions] = useState<UnitOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"invoices" | "config" | "launch">("invoices");
  const [filterStatus, setFilterStatus] = useState("");

  const [configForm, setConfigForm] = useState({
    monthlyFee: "",
    dueDay: "10",
    lateFeePct: "2",
    interestPct: "1",
    pixKey: "",
    pixKeyType: "CPF",
  });
  const [launchForm, setLaunchForm] = useState({
    criterion: "MONTHLY",
    appliesTo: "ALL_UNITS",
    chargeType: "CONDOMINIO",
    title: "",
    description: "",
    amount: "",
    referenceMonth: new Date().toISOString().slice(0, 7),
    dueDate: "",
    targetUnitId: "",
    targetUnitIds: [] as string[],
    targetBlocks: [] as string[],
  });

  const [savingConfig, setSavingConfig] = useState(false);
  const [launching, setLaunching] = useState(false);
  const [savingPay, setSavingPay] = useState(false);
  const [showPayModal, setShowPayModal] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);
  const [invoiceDetail, setInvoiceDetail] = useState<Invoice | null>(null);
  const [payForm, setPayForm] = useState({ paidAmount: "", paymentMethod: "PIX", notes: "" });

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
    if (!isManager) {
      setUnitOptions([]);
      return;
    }
    const condominiumId = isSuperuser
      ? (selectedCondominiumId ? Number(selectedCondominiumId) : null)
      : (currentUser?.condominiumId ? Number(currentUser.condominiumId) : null);
    if (!condominiumId) {
      setUnitOptions([]);
      return;
    }
    api.get("/units", {
      params: {
        condominiumId,
        condoId: condominiumId,
        page: 0,
        pageSize: 200,
      },
    })
      .then((res) => {
        const content = res.data?.content ?? res.data?.items ?? [];
        const units = (content as any[]).map((unit) => ({
          id: unit.id,
          number: unit.number,
          block: unit.block,
          label: `Unidade ${unit.number ?? unit.code ?? unit.id}${unit.block ? ` • Bloco ${unit.block}` : ""}`,
        }));
        setUnitOptions(units);
      })
      .catch(() => setUnitOptions([]));
  }, [currentUser?.condominiumId, isManager, isSuperuser, selectedCondominiumId]);

  function normalizeSummary(data: Partial<Summary> | null | undefined): Summary {
    return {
      totalInvoices: Number(data?.totalInvoices ?? 0),
      totalAmount: Number(data?.totalAmount ?? 0),
      paidAmount: Number(data?.paidAmount ?? 0),
      pendingAmount: Number(data?.pendingAmount ?? 0),
      overdueAmount: Number(data?.overdueAmount ?? 0),
      delinquencyPct: Number(data?.delinquencyPct ?? 0),
    };
  }

  async function loadAll() {
    try {
      setLoading(true);
      setError(null);
      const condominiumId = selectedCondominiumId ? Number(selectedCondominiumId) : undefined;
      const [invRes, cfgRes, sumRes] = await Promise.all([
        api.get("/api/financial/invoices", {
          params: { size: 30, status: filterStatus || undefined, condominiumId },
        }),
        api.get("/api/financial/config", {
          params: { condominiumId },
        }).catch(() => ({ data: { config: null } })),
        isManager
          ? api.get("/api/financial/summary", { params: { condominiumId } }).catch(() => ({ data: EMPTY_SUMMARY }))
          : Promise.resolve({ data: EMPTY_SUMMARY }),
      ]);

      const nextConfig = cfgRes.data.config ?? null;
      setInvoices(invRes.data.content ?? []);
      setConfig(nextConfig);
      setSummary(normalizeSummary(sumRes.data));

      if (nextConfig) {
        setConfigForm({
          monthlyFee: nextConfig.monthlyFee?.toString() ?? "",
          dueDay: nextConfig.dueDay?.toString() ?? "10",
          lateFeePct: nextConfig.lateFeePct?.toString() ?? "2",
          interestPct: nextConfig.interestPct?.toString() ?? "1",
          pixKey: nextConfig.pixKey ?? "",
          pixKeyType: nextConfig.pixKeyType ?? "CPF",
        });
      } else {
        setConfigForm({
          monthlyFee: "",
          dueDay: "10",
          lateFeePct: "2",
          interestPct: "1",
          pixKey: "",
          pixKeyType: "CPF",
        });
      }
    } catch {
      const message = "Falha ao carregar dados financeiros";
      setError(message);
      toast.show({ type: "error", msg: message });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadAll(); /* eslint-disable-next-line */ }, [filterStatus, selectedCondominiumId]);

  async function handleSaveConfig() {
    if (isSuperuser && !selectedCondominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para editar a configuração financeira." });
      return;
    }
    try {
      setSavingConfig(true);
      await api.put("/api/financial/config", {
        monthlyFee: parseFloat(configForm.monthlyFee),
        dueDay: parseInt(configForm.dueDay, 10),
        lateFeePct: parseFloat(configForm.lateFeePct),
        interestPct: parseFloat(configForm.interestPct),
        pixKey: configForm.pixKey || null,
        pixKeyType: configForm.pixKeyType || null,
      }, {
        params: { condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined },
      });
      toast.show({ type: "success", msg: "Configuração salva!" });
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao salvar configuração" });
    } finally {
      setSavingConfig(false);
    }
  }

  async function handleLaunchCharges() {
    if (isSuperuser && !selectedCondominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para lançar a cobrança." });
      return;
    }
    try {
      setLaunching(true);
      const response = await api.post("/api/financial/invoices/launch", {
        criterion: launchForm.criterion,
        appliesTo: launchForm.appliesTo,
        chargeType: launchForm.chargeType,
        title: launchForm.title || null,
        description: launchForm.description || null,
        amount: parseFloat(launchForm.amount),
        referenceMonth: launchForm.referenceMonth || null,
        dueDate: launchForm.dueDate || null,
        targetUnitId: launchForm.targetUnitId ? Number(launchForm.targetUnitId) : null,
        targetUnitIds: launchForm.targetUnitIds.map((value) => Number(value)),
        targetBlocks: launchForm.targetBlocks,
      }, {
        params: { condominiumId: selectedCondominiumId ? Number(selectedCondominiumId) : undefined },
      });
      const createdCount = response.data.createdCount ?? 0;
      const skippedCount = response.data.skippedCount ?? 0;
      toast.show({
        type: "success",
        msg: `Cobrança lançada. ${createdCount} fatura(s) criada(s)${skippedCount ? `, ${skippedCount} já existia(m)` : ""}.`,
      });
      setLaunchForm((prev) => ({
        ...prev,
        title: "",
        description: "",
        amount: "",
        targetUnitId: "",
        targetUnitIds: [],
        targetBlocks: [],
      }));
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao lançar cobranças" });
    } finally {
      setLaunching(false);
    }
  }

  async function handleRegisterPayment() {
    if (!selectedInvoice) return;
    try {
      setSavingPay(true);
      await api.patch(`/api/financial/invoices/${selectedInvoice.id}/pay`, {
        paidAmount: payForm.paidAmount ? parseFloat(payForm.paidAmount) : null,
        paymentMethod: payForm.paymentMethod,
        notes: payForm.notes || null,
      });
      toast.show({ type: "success", msg: "Pagamento registrado!" });
      setShowPayModal(false);
      loadAll();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao registrar pagamento" });
    } finally {
      setSavingPay(false);
    }
  }

  function openPaymentModal(invoice: Invoice) {
    setSelectedInvoice(invoice);
    setPayForm({ paidAmount: invoice.amount.toString(), paymentMethod: "PIX", notes: "" });
    setShowPayModal(true);
  }

  async function handleOpenDetail(invoice: Invoice) {
    try {
      const res = await api.get(`/api/financial/invoices/${invoice.id}`);
      setInvoiceDetail(res.data);
      setShowDetailModal(true);
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || "Erro ao carregar detalhes da cobrança" });
    }
  }

  const kpis = useMemo(() => ([
    { label: "Faturas", value: String(summary.totalInvoices), icon: "📄" },
    { label: "Total cobrado", value: formatCurrency(summary.totalAmount), icon: "💰" },
    { label: "Recebido", value: formatCurrency(summary.paidAmount), icon: "✅" },
    { label: "Em aberto", value: formatCurrency(summary.pendingAmount + summary.overdueAmount), icon: "🧾" },
    { label: "Vencido", value: formatCurrency(summary.overdueAmount), icon: "⏰" },
    { label: "Inadimplência", value: `${summary.delinquencyPct.toFixed(1)}%`, icon: "⚠️", red: summary.delinquencyPct > 20 },
  ]), [summary]);

  const availableBlocks = useMemo(
    () => Array.from(new Set(unitOptions.map((unit) => unit.block).filter(Boolean))) as string[],
    [unitOptions]
  );

  const launchScopeLabel = launchForm.appliesTo === "SINGLE_UNIT"
    ? "Lançar cobrança para a unidade selecionada"
    : launchForm.appliesTo === "SPECIFIC_UNITS"
      ? "Lançar cobrança para as unidades selecionadas"
      : launchForm.appliesTo === "SPECIFIC_BLOCKS"
        ? "Lançar cobrança para os blocos selecionados"
        : "Lançar cobrança para todas as unidades";

  const launchScopeInvalid =
    (launchForm.appliesTo === "SINGLE_UNIT" && !launchForm.targetUnitId) ||
    (launchForm.appliesTo === "SPECIFIC_UNITS" && launchForm.targetUnitIds.length === 0) ||
    (launchForm.appliesTo === "SPECIFIC_BLOCKS" && launchForm.targetBlocks.length === 0);

  return (
    <div className="p-6 max-w-6xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            Financeiro
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            {isMorador ? "Suas cobranças" : "Gestão financeira do condomínio"}
          </p>
        </div>
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

      {isManager && (
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3 mb-6">
          {kpis.map((metric) => (
            <div key={metric.label} className="bg-white rounded-xl border border-slate-100 shadow-sm p-4">
              <p className="text-lg mb-1">{metric.icon}</p>
              <p className={`text-xl font-bold ${metric.red ? "text-rose-600" : "text-slate-900"}`} style={{ fontFamily: "var(--font-display)" }}>
                {metric.value}
              </p>
              <p className="text-xs text-slate-500">{metric.label}</p>
            </div>
          ))}
        </div>
      )}

      {isMorador && config?.pixKey && (
        <div className="mb-5 bg-indigo-50 border border-indigo-100 rounded-xl p-4">
          <p className="text-xs font-semibold text-indigo-500 mb-1">Chave Pix do condomínio</p>
          <p className="font-mono text-sm text-indigo-800">{config.pixKey}</p>
          <p className="text-xs text-indigo-400">{config.pixKeyType}</p>
        </div>
      )}

      {isManager && (
        <div className="flex gap-1 mb-5 bg-slate-100 rounded-lg p-1 w-fit">
          {([
            ["invoices", "Cobranças"],
            ["launch", "Nova cobrança"],
            ["config", "Configuração"],
          ] as const).map(([tab, label]) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
                activeTab === tab ? "bg-white shadow-sm text-slate-900" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      {loading ? (
        <div className="space-y-3">{[1, 2, 3].map((i) => <div key={i} className="bg-white rounded-xl border border-slate-100 h-16 animate-pulse" />)}</div>
      ) : error ? (
        <div className="bg-white rounded-xl border border-rose-200 p-10 text-center">
          <p className="text-sm font-medium text-rose-700">Nao foi possivel carregar o financeiro.</p>
          <p className="text-sm text-rose-500 mt-1">{error}</p>
        </div>
      ) : (
        <>
          {(isMorador || activeTab === "invoices") && (
            <>
              <div className="flex gap-2 mb-4 flex-wrap">
                {["", "PENDING", "PAID", "OVERDUE"].map((status) => (
                  <button
                    key={status}
                    onClick={() => setFilterStatus(status)}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                      filterStatus === status ? "bg-indigo-600 text-white" : "bg-white border border-slate-200 text-slate-600 hover:bg-slate-50"
                    }`}
                  >
                    {status ? STATUS_LABELS[status] : "Todas"}
                  </button>
                ))}
              </div>

              <div className="space-y-3">
                {invoices.length === 0 ? (
                  <div className="bg-white rounded-xl border border-slate-100 p-10 text-center text-slate-500 text-sm">
                    Nenhuma cobrança encontrada.
                  </div>
                ) : invoices.map((invoice) => (
                  <article
                    key={invoice.id}
                    onClick={() => handleOpenDetail(invoice)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        handleOpenDetail(invoice);
                      }
                    }}
                    role="button"
                    tabIndex={0}
                    className="w-full text-left bg-white rounded-xl border border-slate-100 shadow-sm p-4 hover:shadow-md transition-shadow"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2 flex-wrap mb-1">
                          <span className="font-medium text-slate-900 text-sm">
                            {invoice.title || CHARGE_TYPE_LABELS[invoice.chargeType]}
                          </span>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[invoice.status]}`}>
                            {STATUS_LABELS[invoice.status]}
                          </span>
                          <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">
                            {CHARGE_TYPE_LABELS[invoice.chargeType]}
                          </span>
                        </div>
                        <p className="text-xs text-slate-500">
                          Competência {invoice.referenceMonth} • Vencimento {formatDate(invoice.dueDate)}
                        </p>
                        <div className="mt-1 flex items-center gap-3 flex-wrap text-xs text-slate-400">
                          {!isMorador && invoice.unitLabel && <span>{invoice.unitLabel}</span>}
                          {!isMorador && invoice.condominiumName && <span>{invoice.condominiumName}</span>}
                          <span>Ver detalhes</span>
                        </div>
                      </div>
                      <div className="text-right flex-shrink-0">
                        <p className="font-bold text-slate-900">{formatCurrency(invoice.amount)}</p>
                        {isManager && (invoice.status === "PENDING" || invoice.status === "OVERDUE") && (
                          <button
                            type="button"
                            onClick={(event) => {
                              event.stopPropagation();
                              openPaymentModal(invoice);
                            }}
                            onKeyDown={(event) => {
                              event.stopPropagation();
                            }}
                            className="text-xs text-indigo-600 hover:text-indigo-700 font-medium mt-0.5"
                          >
                            Registrar pagamento
                          </button>
                        )}
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            </>
          )}

          {isManager && activeTab === "launch" && (
            <div className="bg-white rounded-xl border border-slate-100 shadow-sm p-6">
              <h3 className="text-sm font-semibold text-slate-900 mb-5">Nova cobrança</h3>
              {isSuperuser && !selectedCondominiumId && (
                <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                  Selecione um condomínio para lançar a cobrança.
                </div>
              )}
              <div className="grid grid-cols-2 gap-4 mb-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Critério</label>
                  <select
                    value={launchForm.criterion}
                    onChange={(e) => setLaunchForm({ ...launchForm, criterion: e.target.value })}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  >
                    <option value="MONTHLY">Mensal</option>
                    <option value="ONE_TIME">Cobrança única</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Tipo</label>
                  <select
                    value={launchForm.chargeType}
                    onChange={(e) => setLaunchForm({ ...launchForm, chargeType: e.target.value })}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  >
                    {Object.entries(CHARGE_TYPE_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="mb-4">
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Aplicar para</label>
                <select
                  value={launchForm.appliesTo}
                  onChange={(e) => setLaunchForm({
                    ...launchForm,
                    appliesTo: e.target.value,
                    targetUnitId: "",
                    targetUnitIds: [],
                    targetBlocks: [],
                  })}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                >
                  <option value="ALL_UNITS">Todas as unidades</option>
                  <option value="SINGLE_UNIT">Uma única unidade</option>
                  <option value="SPECIFIC_UNITS">Unidades específicas</option>
                  <option value="SPECIFIC_BLOCKS">Blocos específicos</option>
                </select>
                <p className="text-xs text-slate-400 mt-1">
                  O backend valida se a unidade ou bloco pertence ao condomínio selecionado.
                </p>
              </div>
              <div className="grid grid-cols-2 gap-4 mb-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Valor por unidade</label>
                  <input
                    value={launchForm.amount}
                    onChange={(e) => setLaunchForm({ ...launchForm, amount: e.target.value })}
                    type="number"
                    step="0.01"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Vencimento</label>
                  <input
                    value={launchForm.dueDate}
                    onChange={(e) => setLaunchForm({ ...launchForm, dueDate: e.target.value })}
                    type="date"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4 mb-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Competência</label>
                  <input
                    value={launchForm.referenceMonth}
                    onChange={(e) => setLaunchForm({ ...launchForm, referenceMonth: e.target.value })}
                    type="month"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Título</label>
                  <input
                    value={launchForm.title}
                    onChange={(e) => setLaunchForm({ ...launchForm, title: e.target.value })}
                    placeholder="Ex: Fundo de reforma da fachada"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
              </div>
              {launchForm.appliesTo === "SINGLE_UNIT" && (
                <div className="mb-4">
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Unidade de destino</label>
                  <select
                    value={launchForm.targetUnitId}
                    onChange={(e) => setLaunchForm({ ...launchForm, targetUnitId: e.target.value })}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  >
                    <option value="">Selecione a unidade...</option>
                    {unitOptions.map((unit) => (
                      <option key={unit.id} value={String(unit.id)}>{unit.label}</option>
                    ))}
                  </select>
                </div>
              )}
              {launchForm.appliesTo === "SPECIFIC_UNITS" && (
                <div className="mb-4">
                  <p className="block text-sm font-medium text-slate-700 mb-1.5">Unidades específicas</p>
                  <div className="grid grid-cols-2 gap-2 rounded-xl border border-slate-200 p-3 max-h-56 overflow-auto">
                    {unitOptions.map((unit) => {
                      const checked = launchForm.targetUnitIds.includes(String(unit.id));
                      return (
                        <label key={unit.id} className="flex items-center gap-2 text-sm text-slate-700">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={(e) => setLaunchForm({
                              ...launchForm,
                              targetUnitIds: e.target.checked
                                ? [...launchForm.targetUnitIds, String(unit.id)]
                                : launchForm.targetUnitIds.filter((value) => value !== String(unit.id)),
                            })}
                          />
                          {unit.label}
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}
              {launchForm.appliesTo === "SPECIFIC_BLOCKS" && (
                <div className="mb-4">
                  <p className="block text-sm font-medium text-slate-700 mb-1.5">Blocos específicos</p>
                  <div className="grid grid-cols-2 gap-2 rounded-xl border border-slate-200 p-3">
                    {availableBlocks.length === 0 ? (
                      <p className="text-sm text-slate-500">Nenhum bloco cadastrado para o condomínio selecionado.</p>
                    ) : availableBlocks.map((block) => {
                      const checked = launchForm.targetBlocks.includes(block);
                      return (
                        <label key={block} className="flex items-center gap-2 text-sm text-slate-700">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={(e) => setLaunchForm({
                              ...launchForm,
                              targetBlocks: e.target.checked
                                ? [...launchForm.targetBlocks, block]
                                : launchForm.targetBlocks.filter((value) => value !== block),
                            })}
                          />
                          Bloco {block}
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}
              <div className="mb-5">
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Descrição</label>
                <textarea
                  value={launchForm.description}
                  onChange={(e) => setLaunchForm({ ...launchForm, description: e.target.value })}
                  rows={3}
                  placeholder="Explique o motivo da cobrança para aparecer no detalhe do morador."
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none"
                />
              </div>
              <button
                onClick={handleLaunchCharges}
                disabled={launching || !launchForm.amount || launchScopeInvalid || (isSuperuser && !selectedCondominiumId)}
                className="bg-indigo-600 hover:bg-indigo-700 text-white px-5 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
              >
                {launching ? "Lançando…" : launchScopeLabel}
              </button>
            </div>
          )}

          {isManager && activeTab === "config" && (
            <div className="bg-white rounded-xl border border-slate-100 shadow-sm p-6">
              <h3 className="text-sm font-semibold text-slate-900 mb-5">Configurações Financeiras</h3>
              {isSuperuser && !selectedCondominiumId && (
                <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                  Selecione um condomínio para visualizar ou editar a configuração financeira.
                </div>
              )}
              <div className="grid grid-cols-2 gap-4 mb-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Taxa mensal (R$)</label>
                  <input
                    value={configForm.monthlyFee}
                    onChange={(e) => setConfigForm({ ...configForm, monthlyFee: e.target.value })}
                    type="number"
                    step="0.01"
                    placeholder="0.00"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Dia de vencimento</label>
                  <input
                    value={configForm.dueDay}
                    onChange={(e) => setConfigForm({ ...configForm, dueDay: e.target.value })}
                    type="number"
                    min={1}
                    max={28}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Multa por atraso (%)</label>
                  <input
                    value={configForm.lateFeePct}
                    onChange={(e) => setConfigForm({ ...configForm, lateFeePct: e.target.value })}
                    type="number"
                    step="0.01"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Juros mensal (%)</label>
                  <input
                    value={configForm.interestPct}
                    onChange={(e) => setConfigForm({ ...configForm, interestPct: e.target.value })}
                    type="number"
                    step="0.01"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
              </div>
              <div className="grid grid-cols-3 gap-4 mb-5">
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Chave Pix</label>
                  <input
                    value={configForm.pixKey}
                    onChange={(e) => setConfigForm({ ...configForm, pixKey: e.target.value })}
                    placeholder="Ex: cnpj@condominio.com.br"
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Tipo de Chave</label>
                  <select
                    value={configForm.pixKeyType}
                    onChange={(e) => setConfigForm({ ...configForm, pixKeyType: e.target.value })}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                  >
                    {["CPF", "CNPJ", "EMAIL", "PHONE", "EVP"].map((type) => (
                      <option key={type} value={type}>{type}</option>
                    ))}
                  </select>
                </div>
              </div>
              <button
                onClick={handleSaveConfig}
                disabled={savingConfig || (isSuperuser && !selectedCondominiumId)}
                className="bg-indigo-600 hover:bg-indigo-700 text-white px-5 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
              >
                {savingConfig ? "Salvando…" : "Salvar configuração"}
              </button>
            </div>
          )}
        </>
      )}

      <Modal
        open={showPayModal}
        onClose={() => setShowPayModal(false)}
        title="Registrar Pagamento"
        footer={
          <>
            <button onClick={() => setShowPayModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg">Cancelar</button>
            <button
              onClick={handleRegisterPayment}
              disabled={savingPay}
              className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg font-medium disabled:opacity-50"
            >
              {savingPay ? "Salvando…" : "Confirmar Pagamento"}
            </button>
          </>
        }
      >
        {selectedInvoice && (
          <div className="space-y-4">
            <div className="bg-slate-50 rounded-lg p-3 text-sm text-slate-600">
              <span className="font-medium">{selectedInvoice.title}</span> — {formatCurrency(selectedInvoice.amount)}
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Valor pago (R$)</label>
              <input
                value={payForm.paidAmount}
                onChange={(e) => setPayForm({ ...payForm, paidAmount: e.target.value })}
                type="number"
                step="0.01"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Forma de pagamento</label>
              <select
                value={payForm.paymentMethod}
                onChange={(e) => setPayForm({ ...payForm, paymentMethod: e.target.value })}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              >
                {["PIX", "BOLETO", "TRANSFER", "CASH", "OTHER"].map((method) => (
                  <option key={method} value={method}>{method}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Observação</label>
              <input
                value={payForm.notes}
                onChange={(e) => setPayForm({ ...payForm, notes: e.target.value })}
                placeholder="Ex: Comprovante recebido via WhatsApp"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              />
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={showDetailModal}
        onClose={() => setShowDetailModal(false)}
        title={invoiceDetail?.title ?? "Detalhes da cobrança"}
        size="lg"
        footer={
          isMorador ? (
            <>
              <button className="px-4 py-2 text-sm border border-slate-200 text-slate-700 rounded-lg cursor-not-allowed opacity-70">
                Pagar com Pix em breve
              </button>
              <button className="px-4 py-2 text-sm bg-slate-900 text-white rounded-lg cursor-not-allowed opacity-70">
                Pagar com boleto em breve
              </button>
            </>
          ) : undefined
        }
      >
        {invoiceDetail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2 flex-wrap">
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[invoiceDetail.status]}`}>
                {STATUS_LABELS[invoiceDetail.status]}
              </span>
              <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">
                {CHARGE_TYPE_LABELS[invoiceDetail.chargeType]}
              </span>
            </div>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-slate-400">Condomínio</p>
                <p className="text-slate-900">{invoiceDetail.condominiumName ?? "—"}</p>
              </div>
              <div>
                <p className="text-slate-400">Unidade</p>
                <p className="text-slate-900">{invoiceDetail.unitLabel ?? `Unidade #${invoiceDetail.unitId}`}</p>
              </div>
              <div>
                <p className="text-slate-400">Competência</p>
                <p className="text-slate-900">{invoiceDetail.referenceMonth}</p>
              </div>
              <div>
                <p className="text-slate-400">Vencimento</p>
                <p className="text-slate-900">{formatDate(invoiceDetail.dueDate)}</p>
              </div>
              <div>
                <p className="text-slate-400">Valor</p>
                <p className="text-slate-900">{formatCurrency(invoiceDetail.amount)}</p>
              </div>
              <div>
                <p className="text-slate-400">Status</p>
                <p className="text-slate-900">{STATUS_LABELS[invoiceDetail.status]}</p>
              </div>
            </div>
            {invoiceDetail.description && (
              <div>
                <p className="text-sm font-medium text-slate-700 mb-1">Descrição</p>
                <p className="text-sm text-slate-600">{invoiceDetail.description}</p>
              </div>
            )}
            {(invoiceDetail.paymentMethod || invoiceDetail.paidAt || invoiceDetail.paymentNotes) && (
              <div className="rounded-xl bg-slate-50 border border-slate-100 p-4">
                <p className="text-sm font-medium text-slate-700 mb-2">Histórico</p>
                <div className="space-y-1 text-sm text-slate-600">
                  {invoiceDetail.paymentMethod && <p>Pagamento registrado via {invoiceDetail.paymentMethod}</p>}
                  {invoiceDetail.paidAt && <p>Pago em {new Date(invoiceDetail.paidAt).toLocaleString("pt-BR")}</p>}
                  {invoiceDetail.paymentNotes && <p>Observações: {invoiceDetail.paymentNotes}</p>}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}

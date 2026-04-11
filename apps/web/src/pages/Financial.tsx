import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import api from "../lib/api";
import { getUser } from "../lib/auth";
import Modal from "../components/Modal";
import { useToast } from "../components/Toast";
import { useSuperadminCondominiumFilter } from "../hooks/useSuperadminCondominiumFilter";

type FinancialConfig = {
  id?: number;
  monthlyFee?: number;
  dueDay?: number;
  lateFeePct?: number;
  interestPct?: number;
  pixKey?: string;
  pixKeyType?: string;
  defaultBillingType?: "PIX" | "BOLETO" | "PIX_AND_BOLETO" | "UNDEFINED";
  notificationEmailEnabled?: boolean;
  notificationWhatsappEnabled?: boolean;
  asaasEnabled?: boolean;
  asaasWebhookToken?: string;
};

type InvoiceStatus =
  | "DRAFT"
  | "PENDING"
  | "EXTERNAL_CREATED"
  | "AWAITING_PAYMENT"
  | "PARTIALLY_PAID"
  | "PAID"
  | "OVERDUE"
  | "CANCELLED"
  | "FAILED"
  | "WAIVED";

type Invoice = {
  id: number;
  condominiumId: number;
  condominiumName?: string;
  unitId: number;
  unitLabel?: string;
  residentName?: string;
  referenceMonth: string;
  chargeType: string;
  title?: string;
  description?: string;
  amount: number;
  dueDate: string;
  status: InvoiceStatus;
  paidAt?: string;
  paidAmount?: number;
  paymentMethod?: string;
  externalProvider?: string;
  externalChargeId?: string;
  externalStatus?: string;
  billingType?: string;
  boletoUrl?: string;
  invoiceUrl?: string;
  pixCopyPaste?: string;
  pixQrCode?: string;
  pixExpiresAt?: string;
  lastWebhookAt?: string;
  lastNotificationAt?: string;
  lastNotificationType?: string;
  createdAt?: string;
};

type InvoiceEvent = {
  id: number;
  type: string;
  source?: string;
  description: string;
  createdAt: string;
};

type InvoiceNotification = {
  id: number;
  type: string;
  channel: string;
  status: string;
  recipientName?: string;
  recipientEmail?: string;
  message: string;
  createdAt: string;
  sentAt?: string;
};

type InvoiceDetail = Invoice & {
  paymentNotes?: string;
  externalInvoiceNumber?: string;
  apportionmentMode?: string;
  apportionmentGroup?: string;
  registeredBy?: number;
  events?: InvoiceEvent[];
  notifications?: InvoiceNotification[];
};

type FinancialStatusBreakdown = {
  status: string;
  totalInvoices: number;
  totalAmount: number;
};

type FinancialBlockDelinquency = {
  block: string;
  overdueInvoices: number;
  overdueAmount: number;
  openAmount: number;
};

type FinancialPeriodSummary = {
  referenceMonth: string;
  totalInvoices: number;
  totalAmount: number;
  paidAmount: number;
  overdueAmount: number;
};

type Summary = {
  totalInvoices: number;
  totalAmount: number;
  paidAmount: number;
  pendingAmount: number;
  overdueAmount: number;
  delinquencyPct: number;
  totalsByStatus: FinancialStatusBreakdown[];
  delinquencyByBlock: FinancialBlockDelinquency[];
  totalsByReferenceMonth: FinancialPeriodSummary[];
};

type PageData<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type Condo = {
  id: number;
  name: string;
};

type UnitOption = {
  id: number;
  label: string;
  block?: string | null;
};

type Tab = "invoices" | "launch" | "config";

const EMPTY_SUMMARY: Summary = {
  totalInvoices: 0,
  totalAmount: 0,
  paidAmount: 0,
  pendingAmount: 0,
  overdueAmount: 0,
  delinquencyPct: 0,
  totalsByStatus: [],
  delinquencyByBlock: [],
  totalsByReferenceMonth: [],
};

const STATUS_LABELS: Record<string, string> = {
  DRAFT: "Rascunho",
  PENDING: "Pendente",
  EXTERNAL_CREATED: "Cobrança criada",
  AWAITING_PAYMENT: "Aguardando pagamento",
  PARTIALLY_PAID: "Pagamento parcial",
  PAID: "Pago",
  OVERDUE: "Vencido",
  CANCELLED: "Cancelado",
  FAILED: "Falhou",
  WAIVED: "Dispensado",
};

const STATUS_COLORS: Record<string, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  PENDING: "bg-amber-100 text-amber-700",
  EXTERNAL_CREATED: "bg-sky-100 text-sky-700",
  AWAITING_PAYMENT: "bg-indigo-100 text-indigo-700",
  PARTIALLY_PAID: "bg-orange-100 text-orange-700",
  PAID: "bg-emerald-100 text-emerald-700",
  OVERDUE: "bg-rose-100 text-rose-700",
  CANCELLED: "bg-slate-100 text-slate-500",
  FAILED: "bg-rose-100 text-rose-700",
  WAIVED: "bg-blue-100 text-blue-700",
};

const CHARGE_TYPE_LABELS: Record<string, string> = {
  CONDOMINIO: "Mensalidade ordinária",
  REFORMA: "Rateio de reforma",
  EXTRA: "Taxa extraordinária",
  FUNDO_RESERVA: "Fundo de reserva",
  MULTA: "Multa",
  OUTROS: "Cobrança avulsa",
};

const BILLING_TYPE_LABELS: Record<string, string> = {
  PIX: "Pix",
  BOLETO: "Boleto",
  PIX_AND_BOLETO: "Pix e boleto",
  UNDEFINED: "A definir",
};

function formatCurrency(value?: number | null) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(value ?? 0));
}

function formatDate(value?: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString("pt-BR");
}

function formatDateTime(value?: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleString("pt-BR");
}

function resolveApiErrorMessage(err: any, fallback: string) {
  const data = err?.response?.data;
  if (typeof data?.message === "string" && data.message.trim()) return data.message;
  if (typeof data?.detail === "string" && data.detail.trim()) return data.detail;
  if (typeof data?.error === "string" && data.error.trim()) return data.error;
  if (typeof data === "string" && data.trim()) return data;
  return fallback;
}

function resolveStatusLabel(status?: string) {
  return STATUS_LABELS[status ?? ""] ?? status ?? "—";
}

function resolveStatusColor(status?: string) {
  return STATUS_COLORS[status ?? ""] ?? "bg-slate-100 text-slate-700";
}

function resolveBillingTypeLabel(type?: string) {
  return BILLING_TYPE_LABELS[type ?? ""] ?? type ?? "—";
}

function getPaidAmount(invoice?: Pick<Invoice, "paidAmount"> | null) {
  return Number(invoice?.paidAmount ?? 0);
}

function getRemainingAmount(invoice?: Pick<Invoice, "amount" | "paidAmount"> | null) {
  return Math.max(0, Number(invoice?.amount ?? 0) - getPaidAmount(invoice));
}

function canRegisterManualPayment(status?: string) {
  return ["PENDING", "AWAITING_PAYMENT", "OVERDUE", "FAILED", "EXTERNAL_CREATED", "PARTIALLY_PAID"].includes(status ?? "");
}

function canResolveManually(status?: string) {
  return ["PENDING", "AWAITING_PAYMENT", "OVERDUE", "FAILED", "EXTERNAL_CREATED"].includes(status ?? "");
}

function getPixImageSrc(encoded?: string) {
  if (!encoded) return null;
  return encoded.startsWith("data:") ? encoded : `data:image/png;base64,${encoded}`;
}

function normalizeSummary(raw: any): Summary {
  return {
    totalInvoices: Number(raw?.totalInvoices ?? 0),
    totalAmount: Number(raw?.totalAmount ?? 0),
    paidAmount: Number(raw?.paidAmount ?? 0),
    pendingAmount: Number(raw?.pendingAmount ?? 0),
    overdueAmount: Number(raw?.overdueAmount ?? 0),
    delinquencyPct: Number(raw?.delinquencyPct ?? 0),
    totalsByStatus: Array.isArray(raw?.totalsByStatus)
      ? raw.totalsByStatus.map((item: any) => ({
          status: item.status ?? "",
          totalInvoices: Number(item.totalInvoices ?? 0),
          totalAmount: Number(item.totalAmount ?? 0),
        }))
      : [],
    delinquencyByBlock: Array.isArray(raw?.delinquencyByBlock)
      ? raw.delinquencyByBlock.map((item: any) => ({
          block: item.block ?? "Sem bloco",
          overdueInvoices: Number(item.overdueInvoices ?? 0),
          overdueAmount: Number(item.overdueAmount ?? 0),
          openAmount: Number(item.openAmount ?? 0),
        }))
      : [],
    totalsByReferenceMonth: Array.isArray(raw?.totalsByReferenceMonth)
      ? raw.totalsByReferenceMonth.map((item: any) => ({
          referenceMonth: item.referenceMonth ?? "",
          totalInvoices: Number(item.totalInvoices ?? 0),
          totalAmount: Number(item.totalAmount ?? 0),
          paidAmount: Number(item.paidAmount ?? 0),
          overdueAmount: Number(item.overdueAmount ?? 0),
        }))
      : [],
  };
}

function copyToClipboard(text: string) {
  if (navigator?.clipboard?.writeText) {
    return navigator.clipboard.writeText(text);
  }
  return Promise.reject(new Error("clipboard_unavailable"));
}

function parsePositiveInt(value: string | null, fallback: number) {
  const parsed = Number(value ?? fallback);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function PageButton({
  disabled,
  onClick,
  children,
}: {
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {children}
    </button>
  );
}

export default function FinancialPage() {
  const toast = useToast();
  const currentUser = getUser();
  const isMorador = currentUser?.role === "MORADOR";
  const isManager = ["SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO"].includes(currentUser?.role ?? "");
  const { selectedCondominiumId, setSelectedCondominiumId, isSuperuser } = useSuperadminCondominiumFilter(currentUser);

  const [searchParams, setSearchParams] = useSearchParams();
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [activeTab, setActiveTab] = useState<Tab>("invoices");
  const [showFilters, setShowFilters] = useState(true);
  const [loading, setLoading] = useState(true);
  const [tableLoading, setTableLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [condos, setCondos] = useState<Condo[]>([]);
  const [unitOptions, setUnitOptions] = useState<UnitOption[]>([]);
  const [config, setConfig] = useState<FinancialConfig | null>(null);
  const [summary, setSummary] = useState<Summary>(EMPTY_SUMMARY);
  const [invoicePage, setInvoicePage] = useState<PageData<Invoice>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
  });

  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);
  const [invoiceDetail, setInvoiceDetail] = useState<InvoiceDetail | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showPayModal, setShowPayModal] = useState(false);
  const [showPixModal, setShowPixModal] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);
  const [launching, setLaunching] = useState(false);
  const [savingPay, setSavingPay] = useState(false);
  const [creatingExternalCharge, setCreatingExternalCharge] = useState(false);

  const [searchInput, setSearchInput] = useState(() => searchParams.get("q") ?? "");
  const [configForm, setConfigForm] = useState({
    monthlyFee: "",
    dueDay: "10",
    lateFeePct: "2",
    interestPct: "1",
    pixKey: "",
    pixKeyType: "CPF",
    defaultBillingType: "BOLETO",
    notificationEmailEnabled: true,
    notificationWhatsappEnabled: false,
    asaasEnabled: false,
    asaasWebhookToken: "",
  });
  const [launchForm, setLaunchForm] = useState({
    criterion: "MONTHLY",
    appliesTo: "ALL_UNITS",
    chargeType: "CONDOMINIO",
    amountMode: "PER_UNIT",
    billingType: "BOLETO",
    title: "",
    description: "",
    amount: "",
    referenceMonth: new Date().toISOString().slice(0, 7),
    dueDate: "",
    targetUnitId: "",
    targetUnitIds: [] as string[],
    targetBlocks: [] as string[],
  });
  const [payForm, setPayForm] = useState({ paidAmount: "", paymentMethod: "PIX", notes: "" });

  const condominiumId = useMemo(() => {
    if (isSuperuser) {
      return selectedCondominiumId ? Number(selectedCondominiumId) : undefined;
    }
    return currentUser?.condominiumId ? Number(currentUser.condominiumId) : undefined;
  }, [currentUser?.condominiumId, isSuperuser, selectedCondominiumId]);

  const filterStatus = searchParams.get("status") ?? "";
  const filterChargeType = searchParams.get("chargeType") ?? "";
  const filterReferenceMonthFrom = searchParams.get("refFrom") ?? "";
  const filterReferenceMonthTo = searchParams.get("refTo") ?? "";
  const filterDueDateFrom = searchParams.get("dueFrom") ?? "";
  const filterDueDateTo = searchParams.get("dueTo") ?? "";
  const query = searchParams.get("q") ?? "";
  const currentPage = parsePositiveInt(searchParams.get("page"), 0);
  const pageSize = parsePositiveInt(searchParams.get("size"), 20) || 20;
  const sortBy = searchParams.get("sortBy") ?? "dueDate";
  const direction = (searchParams.get("direction") ?? "DESC").toUpperCase() === "ASC" ? "ASC" : "DESC";

  const availableBlocks = useMemo(
    () => Array.from(new Set(unitOptions.map((unit) => unit.block).filter(Boolean))) as string[],
    [unitOptions],
  );

  useEffect(() => {
    setSearchInput(query);
  }, [query]);

  const patchParams = useCallback((updates: Record<string, string | number | null | undefined>, resetPage = false) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      Object.entries(updates).forEach(([key, value]) => {
        const normalized = value == null ? "" : String(value).trim();
        if (!normalized) next.delete(key);
        else next.set(key, normalized);
      });
      if (resetPage) next.set("page", "0");
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      patchParams({ q: searchInput }, true);
    }, 400);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [patchParams, searchInput]);

  useEffect(() => {
    if (!isSuperuser) return;
    api.get("/condominiums", { params: { pageSize: 100 } })
      .then((res) => {
        const raw = res.data;
        const list: Condo[] = Array.isArray(raw?.content)
          ? raw.content
          : Array.isArray(raw?.items)
            ? raw.items
            : Array.isArray(raw)
              ? raw
              : [];
        setCondos(list);
      })
      .catch(() => setCondos([]));
  }, [isSuperuser]);

  useEffect(() => {
    if (!isManager || !condominiumId) {
      setUnitOptions([]);
      return;
    }
    api.get("/units", {
      params: { condominiumId, condoId: condominiumId, page: 0, pageSize: 200 },
    })
      .then((res) => {
        const raw = res.data;
        const content = Array.isArray(raw?.content)
          ? raw.content
          : Array.isArray(raw?.items)
            ? raw.items
            : Array.isArray(raw)
              ? raw
              : [];
        setUnitOptions(content.map((unit: any) => ({
          id: unit.id,
          block: unit.block,
          label: `Unidade ${unit.number ?? unit.code ?? unit.id}${unit.block ? ` • Bloco ${unit.block}` : ""}`,
        })));
      })
      .catch(() => setUnitOptions([]));
  }, [condominiumId, isManager]);

  const loadConfigAndSummary = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [configResponse, summaryResponse] = await Promise.all([
        api.get("/api/financial/config", { params: { condominiumId } }).catch(() => ({ data: { config: null } })),
        isManager
          ? api.get("/api/financial/summary", {
              params: {
                condominiumId,
                referenceMonthFrom: filterReferenceMonthFrom || undefined,
                referenceMonthTo: filterReferenceMonthTo || undefined,
              },
            }).catch(() => ({ data: EMPTY_SUMMARY }))
          : Promise.resolve({ data: EMPTY_SUMMARY }),
      ]);
      const nextConfig = configResponse.data?.config ?? null;
      setConfig(nextConfig);
      setSummary(normalizeSummary(summaryResponse.data));
      setConfigForm({
        monthlyFee: nextConfig?.monthlyFee?.toString() ?? "",
        dueDay: nextConfig?.dueDay?.toString() ?? "10",
        lateFeePct: nextConfig?.lateFeePct?.toString() ?? "2",
        interestPct: nextConfig?.interestPct?.toString() ?? "1",
        pixKey: nextConfig?.pixKey ?? "",
        pixKeyType: nextConfig?.pixKeyType ?? "CPF",
        defaultBillingType: nextConfig?.defaultBillingType ?? "BOLETO",
        notificationEmailEnabled: nextConfig?.notificationEmailEnabled ?? true,
        notificationWhatsappEnabled: nextConfig?.notificationWhatsappEnabled ?? false,
        asaasEnabled: nextConfig?.asaasEnabled ?? false,
        asaasWebhookToken: nextConfig?.asaasWebhookToken ?? "",
      });
    } catch (err: any) {
      const message = resolveApiErrorMessage(err, "Falha ao carregar os dados financeiros.");
      setError(message);
      toast.show({ type: "error", msg: message });
    } finally {
      setLoading(false);
    }
  }, [condominiumId, filterReferenceMonthFrom, filterReferenceMonthTo, isManager, toast]);

  const loadInvoices = useCallback(async () => {
    try {
      setTableLoading(true);
      const params: Record<string, unknown> = {
        page: currentPage,
        size: pageSize,
        sortBy,
        direction,
      };
      if (condominiumId) params.condominiumId = condominiumId;
      if (filterStatus) params.status = filterStatus;
      if (filterChargeType) params.chargeType = filterChargeType;
      if (filterReferenceMonthFrom) params.referenceMonthFrom = filterReferenceMonthFrom;
      if (filterReferenceMonthTo) params.referenceMonthTo = filterReferenceMonthTo;
      if (filterDueDateFrom) params.dueDateFrom = filterDueDateFrom;
      if (filterDueDateTo) params.dueDateTo = filterDueDateTo;
      if (query) params.q = query;
      const response = await api.get("/api/financial/invoices/search", { params });
      setInvoicePage({
        content: response.data?.content ?? [],
        totalElements: Number(response.data?.totalElements ?? 0),
        totalPages: Number(response.data?.totalPages ?? 0),
        number: Number(response.data?.number ?? 0),
        size: Number(response.data?.size ?? pageSize),
      });
    } catch (err: any) {
      setInvoicePage({ content: [], totalElements: 0, totalPages: 0, number: currentPage, size: pageSize });
      toast.show({ type: "error", msg: resolveApiErrorMessage(err, "Erro ao listar cobranças") });
    } finally {
      setTableLoading(false);
    }
  }, [
    condominiumId,
    currentPage,
    direction,
    filterChargeType,
    filterDueDateFrom,
    filterDueDateTo,
    filterReferenceMonthFrom,
    filterReferenceMonthTo,
    filterStatus,
    pageSize,
    query,
    sortBy,
    toast,
  ]);

  useEffect(() => {
    loadConfigAndSummary();
  }, [loadConfigAndSummary]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  async function handleSaveConfig() {
    if (isSuperuser && !condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para editar a configuração financeira." });
      return;
    }
    try {
      setSavingConfig(true);
      await api.put("/api/financial/config", {
        monthlyFee: Number(configForm.monthlyFee || 0),
        dueDay: Number(configForm.dueDay || 10),
        lateFeePct: Number(configForm.lateFeePct || 0),
        interestPct: Number(configForm.interestPct || 0),
        pixKey: configForm.pixKey || null,
        pixKeyType: configForm.pixKeyType || null,
        defaultBillingType: configForm.defaultBillingType,
        notificationEmailEnabled: configForm.notificationEmailEnabled,
        notificationWhatsappEnabled: configForm.notificationWhatsappEnabled,
        asaasEnabled: configForm.asaasEnabled,
        asaasWebhookToken: configForm.asaasWebhookToken || null,
      }, { params: { condominiumId } });
      toast.show({ type: "success", msg: "Configuração salva com sucesso." });
      loadConfigAndSummary();
    } catch (err: any) {
      toast.show({ type: "error", msg: resolveApiErrorMessage(err, "Erro ao salvar configuração") });
    } finally {
      setSavingConfig(false);
    }
  }

  async function handleLaunchCharges() {
    if (isSuperuser && !condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para lançar a cobrança." });
      return;
    }
    try {
      setLaunching(true);
      const response = await api.post("/api/financial/invoices/launch", {
        criterion: launchForm.criterion,
        appliesTo: launchForm.appliesTo,
        chargeType: launchForm.chargeType,
        amountMode: launchForm.amountMode,
        billingType: launchForm.billingType,
        title: launchForm.title || null,
        description: launchForm.description || null,
        amount: Number(launchForm.amount || 0),
        referenceMonth: launchForm.referenceMonth || null,
        dueDate: launchForm.dueDate || null,
        targetUnitId: launchForm.targetUnitId ? Number(launchForm.targetUnitId) : null,
        targetUnitIds: launchForm.targetUnitIds.map((value) => Number(value)),
        targetBlocks: launchForm.targetBlocks,
      }, { params: { condominiumId } });

      const createdCount = Number(response.data?.createdCount ?? 0);
      const skippedCount = Number(response.data?.skippedCount ?? 0);
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
      await Promise.all([loadInvoices(), loadConfigAndSummary()]);
      setActiveTab("invoices");
    } catch (err: any) {
      toast.show({ type: "error", msg: resolveApiErrorMessage(err, "Erro ao lançar cobranças") });
    } finally {
      setLaunching(false);
    }
  }

  async function openDetail(invoice: Invoice) {
    try {
      const response = await api.get(`/api/financial/invoices/${invoice.id}`);
      setSelectedInvoice(invoice);
      setInvoiceDetail(response.data);
      setShowDetailModal(true);
    } catch (err: any) {
      toast.show({ type: "error", msg: resolveApiErrorMessage(err, "Erro ao carregar os detalhes da cobrança") });
    }
  }

  function openPayModal(invoice: Invoice | InvoiceDetail) {
    setSelectedInvoice(invoice);
    setPayForm({
      paidAmount: getRemainingAmount(invoice).toFixed(2),
      paymentMethod: invoice.paymentMethod ?? "PIX",
      notes: "",
    });
    setShowPayModal(true);
  }

  async function handleRegisterPayment() {
    if (!selectedInvoice) return;
    try {
      setSavingPay(true);
      await api.patch(`/api/financial/invoices/${selectedInvoice.id}/pay`, {
        paidAmount: Number(payForm.paidAmount || 0),
        paymentMethod: payForm.paymentMethod,
        notes: payForm.notes || null,
      });
      toast.show({ type: "success", msg: "Pagamento registrado com sucesso." });
      setShowPayModal(false);
      if (showDetailModal) await openDetail(selectedInvoice);
      await Promise.all([loadInvoices(), loadConfigAndSummary()]);
    } catch (err: any) {
      toast.show({ type: "error", msg: resolveApiErrorMessage(err, "Erro ao registrar pagamento") });
    } finally {
      setSavingPay(false);
    }
  }

  async function handleManualStatusAction(invoice: Invoice | InvoiceDetail, action: "cancel" | "waive") {
    const promptText = action === "cancel" ? "Motivo do cancelamento (opcional)" : "Motivo da dispensa (opcional)";
    const reason = window.prompt(promptText) ?? "";
    try {
      await api.patch(`/api/financial/invoices/${invoice.id}/${action}`, { reason: reason.trim() || null });
      toast.show({
        type: "success",
        msg: action === "cancel" ? "Cobrança cancelada com sucesso." : "Cobrança dispensada com sucesso.",
      });
      if (showDetailModal) await openDetail(invoice as Invoice);
      await Promise.all([loadInvoices(), loadConfigAndSummary()]);
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: resolveApiErrorMessage(err, action === "cancel" ? "Erro ao cancelar cobrança" : "Erro ao dispensar cobrança"),
      });
    }
  }

  async function handleCreateExternalCharge(invoice: Invoice | InvoiceDetail) {
    try {
      setCreatingExternalCharge(true);
      const response = await api.post(`/api/financial/invoices/${invoice.id}/external-charge`, {
        billingType: invoice.billingType ?? configForm.defaultBillingType,
      });
      setInvoiceDetail(response.data);
      toast.show({ type: "success", msg: "Cobrança externa criada com sucesso." });
      await Promise.all([loadInvoices(), loadConfigAndSummary()]);
    } catch (err: any) {
      toast.show({ type: "error", msg: resolveApiErrorMessage(err, "Erro ao criar cobrança externa") });
    } finally {
      setCreatingExternalCharge(false);
    }
  }

  async function handleCopyPix(text?: string) {
    if (!text) return;
    try {
      await copyToClipboard(text);
      toast.show({ type: "success", msg: "Código Pix copiado." });
    } catch {
      toast.show({ type: "error", msg: "Não foi possível copiar o Pix automaticamente." });
    }
  }

  const launchScopeInvalid =
    (launchForm.appliesTo === "SINGLE_UNIT" && !launchForm.targetUnitId) ||
    (launchForm.appliesTo === "SPECIFIC_UNITS" && launchForm.targetUnitIds.length === 0) ||
    (launchForm.appliesTo === "SPECIFIC_BLOCKS" && launchForm.targetBlocks.length === 0);

  const previewUnitCount = useMemo(() => {
    if (launchForm.appliesTo === "SINGLE_UNIT") return launchForm.targetUnitId ? 1 : 0;
    if (launchForm.appliesTo === "SPECIFIC_UNITS") return launchForm.targetUnitIds.length;
    if (launchForm.appliesTo === "SPECIFIC_BLOCKS") {
      return unitOptions.filter((unit) => unit.block && launchForm.targetBlocks.includes(unit.block)).length;
    }
    return unitOptions.length;
  }, [launchForm.appliesTo, launchForm.targetBlocks, launchForm.targetUnitId, launchForm.targetUnitIds, unitOptions]);

  const previewAmountPerUnit = useMemo(() => {
    const rawAmount = Number(launchForm.amount);
    if (!rawAmount || previewUnitCount <= 0) return null;
    return launchForm.amountMode === "TOTAL" ? rawAmount / previewUnitCount : rawAmount;
  }, [launchForm.amount, launchForm.amountMode, previewUnitCount]);

  const kpis = useMemo(() => ([
    { label: "Faturas", value: String(summary.totalInvoices) },
    { label: "Total cobrado", value: formatCurrency(summary.totalAmount) },
    { label: "Recebido", value: formatCurrency(summary.paidAmount) },
    { label: "Em aberto", value: formatCurrency(summary.pendingAmount + summary.overdueAmount) },
    { label: "Vencido", value: formatCurrency(summary.overdueAmount) },
    { label: "Inadimplência", value: `${summary.delinquencyPct.toFixed(1)}%` },
  ]), [summary]);

  if (isMorador) {
    return <Navigate to="/app/my-invoices" replace />;
  }

  return (
    <div className="max-w-7xl p-6">
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            Financeiro
          </h1>
          <p className="mt-1 text-sm text-slate-500">Operação financeira com filtros server-side, histórico e ações administrativas seguras.</p>
        </div>
      </div>

      {isSuperuser && (
        <div className="mb-5 max-w-sm">
          <label className="mb-1.5 block text-sm font-medium text-slate-700">Condomínio</label>
          <select
            value={selectedCondominiumId}
            onChange={(event) => setSelectedCondominiumId(event.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
          >
            <option value="">Todos os condomínios</option>
            {condos.map((condo) => (
              <option key={condo.id} value={String(condo.id)}>{condo.name}</option>
            ))}
          </select>
        </div>
      )}

      {isManager && (
        <div className="mb-6 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
          {kpis.map((metric) => (
            <div key={metric.label} className="rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
              <p className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>{metric.value}</p>
              <p className="mt-1 text-xs text-slate-500">{metric.label}</p>
            </div>
          ))}
        </div>
      )}

      {isManager && (summary.delinquencyByBlock.length > 0 || summary.totalsByReferenceMonth.length > 0) && (
        <div className="mb-6 grid grid-cols-1 gap-4 xl:grid-cols-2">
          <div className="rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
            <p className="mb-3 text-sm font-semibold text-slate-800">Inadimplência por bloco</p>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={summary.delinquencyByBlock}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="block" tick={{ fontSize: 12 }} />
                <YAxis tickFormatter={(value) => formatCurrency(Number(value)).replace(",00", "")} tick={{ fontSize: 12 }} width={90} />
                <Tooltip formatter={(value: number) => formatCurrency(value)} />
                <Legend />
                <Bar dataKey="overdueAmount" name="Vencido" fill="#f43f5e" radius={[6, 6, 0, 0]} />
                <Bar dataKey="openAmount" name="Em aberto" fill="#6366f1" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
            <p className="mb-3 text-sm font-semibold text-slate-800">Evolução mensal</p>
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={summary.totalsByReferenceMonth}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="referenceMonth" tick={{ fontSize: 12 }} />
                <YAxis tickFormatter={(value) => formatCurrency(Number(value)).replace(",00", "")} tick={{ fontSize: 12 }} width={90} />
                <Tooltip formatter={(value: number) => formatCurrency(value)} />
                <Legend />
                <Line type="monotone" dataKey="totalAmount" name="Cobrado" stroke="#6366f1" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="paidAmount" name="Recebido" stroke="#10b981" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="overdueAmount" name="Vencido" stroke="#f43f5e" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {isManager && (
        <div className="mb-5 flex w-fit gap-1 rounded-lg bg-slate-100 p-1">
          {([
            ["invoices", "Cobranças"],
            ["launch", "Nova cobrança"],
            ["config", "Configuração"],
          ] as const).map(([tab, label]) => (
            <button
              key={tab}
              type="button"
              onClick={() => setActiveTab(tab)}
              className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
                activeTab === tab ? "bg-white text-slate-900 shadow-sm" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((item) => (
            <div key={item} className="h-20 animate-pulse rounded-xl border border-slate-100 bg-white" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-xl border border-rose-200 bg-white p-10 text-center">
          <p className="text-sm font-medium text-rose-700">Não foi possível carregar o financeiro.</p>
          <p className="mt-1 text-sm text-rose-500">{error}</p>
        </div>
      ) : (
        <>
          {activeTab === "invoices" && (
            <>
              <div className="mb-4 rounded-xl border border-slate-100 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                  <div>
                    <p className="text-sm font-semibold text-slate-900">Filtros</p>
                    <p className="text-xs text-slate-500">Busca e paginação rodam no backend.</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setShowFilters((value) => !value)}
                    className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-600"
                  >
                    {showFilters ? "Ocultar" : "Mostrar"}
                  </button>
                </div>

                {showFilters && (
                  <div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-4">
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Texto livre</span>
                      <input
                        value={searchInput}
                        onChange={(event) => setSearchInput(event.target.value)}
                        placeholder="Título, morador, unidade"
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Status</span>
                      <select
                        value={filterStatus}
                        onChange={(event) => patchParams({ status: event.target.value }, true)}
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      >
                        <option value="">Todos</option>
                        {Object.entries(STATUS_LABELS).map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Tipo de cobrança</span>
                      <select
                        value={filterChargeType}
                        onChange={(event) => patchParams({ chargeType: event.target.value }, true)}
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      >
                        <option value="">Todos</option>
                        {Object.entries(CHARGE_TYPE_LABELS).map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Ordenação</span>
                      <select
                        value={`${sortBy}:${direction}`}
                        onChange={(event) => {
                          const [nextSortBy, nextDirection] = event.target.value.split(":");
                          patchParams({ sortBy: nextSortBy, direction: nextDirection }, true);
                        }}
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      >
                        <option value="dueDate:DESC">Vencimento mais recente primeiro</option>
                        <option value="dueDate:ASC">Vencimento mais próximo primeiro</option>
                        <option value="createdAt:DESC">Criação mais recente</option>
                        <option value="createdAt:ASC">Criação mais antiga</option>
                        <option value="amount:DESC">Maior valor</option>
                        <option value="amount:ASC">Menor valor</option>
                      </select>
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Competência inicial</span>
                      <input
                        type="month"
                        value={filterReferenceMonthFrom}
                        onChange={(event) => patchParams({ refFrom: event.target.value }, true)}
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Competência final</span>
                      <input
                        type="month"
                        value={filterReferenceMonthTo}
                        onChange={(event) => patchParams({ refTo: event.target.value }, true)}
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Vencimento inicial</span>
                      <input
                        type="date"
                        value={filterDueDateFrom}
                        onChange={(event) => patchParams({ dueFrom: event.target.value }, true)}
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Vencimento final</span>
                      <input
                        type="date"
                        value={filterDueDateTo}
                        onChange={(event) => patchParams({ dueTo: event.target.value }, true)}
                        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                      />
                    </label>
                  </div>
                )}
              </div>

              <div className="mb-4 flex items-center justify-between rounded-xl border border-slate-100 bg-white px-4 py-3 text-sm shadow-sm">
                <p className="text-slate-600">
                  Exibindo <span className="font-semibold text-slate-900">{invoicePage.content.length}</span> cobranças nesta página
                  de um total de <span className="font-semibold text-slate-900">{invoicePage.totalElements}</span>.
                </p>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-500">Itens por página</span>
                  <select
                    value={pageSize}
                    onChange={(event) => patchParams({ size: event.target.value }, true)}
                    className="rounded-lg border border-slate-200 px-2 py-1 text-sm"
                  >
                    {[10, 20, 50, 100].map((size) => (
                      <option key={size} value={size}>{size}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="overflow-hidden rounded-xl border border-slate-100 bg-white shadow-sm">
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-slate-100">
                    <thead className="bg-slate-50">
                      <tr className="text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <th className="px-4 py-3">Unidade</th>
                        <th className="px-4 py-3">Morador</th>
                        <th className="px-4 py-3">Mês ref.</th>
                        <th className="px-4 py-3">Vencimento</th>
                        <th className="px-4 py-3">Valor</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3">Ações</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 text-sm">
                      {tableLoading ? (
                        <tr><td className="px-4 py-8 text-center text-slate-500" colSpan={7}>Carregando cobranças...</td></tr>
                      ) : invoicePage.content.length === 0 ? (
                        <tr><td className="px-4 py-8 text-center text-slate-500" colSpan={7}>Nenhuma cobrança encontrada.</td></tr>
                      ) : invoicePage.content.map((invoice) => (
                        <tr key={invoice.id} className="align-top">
                          <td className="px-4 py-3">
                            <div className="font-medium text-slate-900">{invoice.unitLabel ?? `Unidade ${invoice.unitId}`}</div>
                            <div className="text-xs text-slate-500">{invoice.title || CHARGE_TYPE_LABELS[invoice.chargeType] || invoice.chargeType}</div>
                          </td>
                          <td className="px-4 py-3 text-slate-600">{invoice.residentName || "—"}</td>
                          <td className="px-4 py-3 text-slate-600">{invoice.referenceMonth || "—"}</td>
                          <td className="px-4 py-3 text-slate-600">{formatDate(invoice.dueDate)}</td>
                          <td className="px-4 py-3">
                            <div className="font-medium text-slate-900">{formatCurrency(invoice.amount)}</div>
                            {getPaidAmount(invoice) > 0 && <div className="text-xs text-slate-500">Pago: {formatCurrency(invoice.paidAmount)}</div>}
                          </td>
                          <td className="px-4 py-3">
                            <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${resolveStatusColor(invoice.status)}`}>
                              {resolveStatusLabel(invoice.status)}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex flex-wrap gap-2">
                              <button type="button" onClick={() => openDetail(invoice)} className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-700">
                                Detalhes
                              </button>
                              {invoice.boletoUrl && (
                                <a href={invoice.boletoUrl} target="_blank" rel="noreferrer" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-700">
                                  Boleto
                                </a>
                              )}
                              {(invoice.pixCopyPaste || invoice.invoiceUrl) && (
                                <button
                                  type="button"
                                  onClick={() => {
                                    setSelectedInvoice(invoice);
                                    setShowPixModal(true);
                                  }}
                                  className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-1.5 text-xs font-medium text-sky-700"
                                >
                                  Pix
                                </button>
                              )}
                              {isManager && canRegisterManualPayment(invoice.status) && (
                                <button type="button" onClick={() => openPayModal(invoice)} className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-700">
                                  Registrar pagamento
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="mt-4 flex items-center justify-between">
                <p className="text-sm text-slate-500">Página {invoicePage.number + 1} de {Math.max(1, invoicePage.totalPages || 1)}</p>
                <div className="flex gap-2">
                  <PageButton disabled={currentPage <= 0} onClick={() => patchParams({ page: currentPage - 1 })}>Anterior</PageButton>
                  <PageButton
                    disabled={invoicePage.totalPages === 0 || currentPage >= invoicePage.totalPages - 1}
                    onClick={() => patchParams({ page: currentPage + 1 })}
                  >
                    Próxima
                  </PageButton>
                </div>
              </div>
            </>
          )}
          {isManager && activeTab === "launch" && (
            <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
              <h2 className="mb-4 text-base font-semibold text-slate-900">Nova cobrança</h2>
              <div className="grid gap-4 md:grid-cols-2">
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Critério</span>
                  <select value={launchForm.criterion} onChange={(e) => setLaunchForm((p) => ({ ...p, criterion: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                    <option value="MONTHLY">Cobrança recorrente</option>
                    <option value="ONE_TIME">Cobrança única</option>
                  </select>
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Aplicar para</span>
                  <select value={launchForm.appliesTo} onChange={(e) => setLaunchForm((p) => ({ ...p, appliesTo: e.target.value, targetUnitId: "", targetUnitIds: [], targetBlocks: [] }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                    <option value="ALL_UNITS">Todas as unidades</option>
                    <option value="SINGLE_UNIT">Uma única unidade</option>
                    <option value="SPECIFIC_UNITS">Unidades específicas</option>
                    <option value="SPECIFIC_BLOCKS">Blocos específicos</option>
                  </select>
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Tipo</span>
                  <select value={launchForm.chargeType} onChange={(e) => setLaunchForm((p) => ({ ...p, chargeType: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                    {Object.entries(CHARGE_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                  </select>
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Forma principal de cobrança</span>
                  <select value={launchForm.billingType} onChange={(e) => setLaunchForm((p) => ({ ...p, billingType: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                    {Object.entries(BILLING_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                  </select>
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Modo do valor</span>
                  <select value={launchForm.amountMode} onChange={(e) => setLaunchForm((p) => ({ ...p, amountMode: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                    <option value="PER_UNIT">Valor por unidade</option>
                    <option value="TOTAL">Valor total com rateio</option>
                  </select>
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Valor</span>
                  <input type="number" min="0" step="0.01" value={launchForm.amount} onChange={(e) => setLaunchForm((p) => ({ ...p, amount: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" />
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Competência</span>
                  <input type="month" value={launchForm.referenceMonth} onChange={(e) => setLaunchForm((p) => ({ ...p, referenceMonth: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" />
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-600">Vencimento</span>
                  <input type="date" value={launchForm.dueDate} onChange={(e) => setLaunchForm((p) => ({ ...p, dueDate: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" />
                </label>
                <label className="block md:col-span-2">
                  <span className="mb-1 block text-sm text-slate-600">Título</span>
                  <input value={launchForm.title} onChange={(e) => setLaunchForm((p) => ({ ...p, title: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" placeholder="Ex.: Multa por barulho" />
                </label>
                <label className="block md:col-span-2">
                  <span className="mb-1 block text-sm text-slate-600">Descrição</span>
                  <textarea value={launchForm.description} onChange={(e) => setLaunchForm((p) => ({ ...p, description: e.target.value }))} className="min-h-28 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" />
                </label>
                {launchForm.appliesTo === "SINGLE_UNIT" && (
                  <label className="block md:col-span-2">
                    <span className="mb-1 block text-sm text-slate-600">Unidade</span>
                    <select value={launchForm.targetUnitId} onChange={(e) => setLaunchForm((p) => ({ ...p, targetUnitId: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                      <option value="">Selecione</option>
                      {unitOptions.map((unit) => <option key={unit.id} value={String(unit.id)}>{unit.label}</option>)}
                    </select>
                  </label>
                )}
                {launchForm.appliesTo === "SPECIFIC_UNITS" && (
                  <label className="block md:col-span-2">
                    <span className="mb-1 block text-sm text-slate-600">Unidades</span>
                    <select multiple value={launchForm.targetUnitIds} onChange={(e) => setLaunchForm((p) => ({ ...p, targetUnitIds: Array.from(e.target.selectedOptions).map((option) => option.value) }))} className="min-h-36 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                      {unitOptions.map((unit) => <option key={unit.id} value={String(unit.id)}>{unit.label}</option>)}
                    </select>
                  </label>
                )}
                {launchForm.appliesTo === "SPECIFIC_BLOCKS" && (
                  <label className="block md:col-span-2">
                    <span className="mb-1 block text-sm text-slate-600">Blocos</span>
                    <select multiple value={launchForm.targetBlocks} onChange={(e) => setLaunchForm((p) => ({ ...p, targetBlocks: Array.from(e.target.selectedOptions).map((option) => option.value) }))} className="min-h-36 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">
                      {availableBlocks.map((block) => <option key={block} value={block}>{block}</option>)}
                    </select>
                  </label>
                )}
              </div>
              <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm text-slate-600">
                <p className="font-medium text-slate-900">Prévia do lançamento</p>
                <div className="mt-2 grid gap-2 md:grid-cols-2">
                  <p>Unidades afetadas: <span className="font-semibold text-slate-900">{previewUnitCount}</span></p>
                  <p>Forma de cobrança: <span className="font-semibold text-slate-900">{resolveBillingTypeLabel(launchForm.billingType)}</span></p>
                  <p>Valor informado: <span className="font-semibold text-slate-900">{formatCurrency(Number(launchForm.amount || 0))}</span></p>
                  <p>Valor estimado por unidade: <span className="font-semibold text-slate-900">{previewAmountPerUnit == null ? "—" : formatCurrency(previewAmountPerUnit)}</span></p>
                </div>
              </div>
              <div className="mt-4 flex justify-end">
                <button type="button" disabled={launching || !launchForm.amount || !launchForm.referenceMonth || !launchForm.dueDate || launchScopeInvalid} onClick={handleLaunchCharges} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50">
                  {launching ? "Lançando..." : "Lançar cobrança"}
                </button>
              </div>
            </div>
          )}
          {isManager && activeTab === "config" && (
            <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
              <h2 className="mb-4 text-base font-semibold text-slate-900">Configuração financeira</h2>
              <div className="grid gap-4 md:grid-cols-2">
                <label className="block"><span className="mb-1 block text-sm text-slate-600">Mensalidade padrão</span><input type="number" min="0" step="0.01" value={configForm.monthlyFee} onChange={(e) => setConfigForm((p) => ({ ...p, monthlyFee: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
                <label className="block"><span className="mb-1 block text-sm text-slate-600">Dia de vencimento</span><input type="number" min="1" max="28" value={configForm.dueDay} onChange={(e) => setConfigForm((p) => ({ ...p, dueDay: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
                <label className="block"><span className="mb-1 block text-sm text-slate-600">Multa (%)</span><input type="number" min="0" step="0.01" value={configForm.lateFeePct} onChange={(e) => setConfigForm((p) => ({ ...p, lateFeePct: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
                <label className="block"><span className="mb-1 block text-sm text-slate-600">Juros (%)</span><input type="number" min="0" step="0.01" value={configForm.interestPct} onChange={(e) => setConfigForm((p) => ({ ...p, interestPct: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
                <label className="block"><span className="mb-1 block text-sm text-slate-600">Chave Pix</span><input value={configForm.pixKey} onChange={(e) => setConfigForm((p) => ({ ...p, pixKey: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
                <label className="block"><span className="mb-1 block text-sm text-slate-600">Tipo da chave Pix</span><select value={configForm.pixKeyType} onChange={(e) => setConfigForm((p) => ({ ...p, pixKeyType: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"><option value="CPF">CPF</option><option value="EMAIL">Email</option><option value="PHONE">Telefone</option><option value="EVP">Aleatória</option></select></label>
                <label className="block md:col-span-2"><span className="mb-1 block text-sm text-slate-600">Forma padrão de cobrança</span><select value={configForm.defaultBillingType} onChange={(e) => setConfigForm((p) => ({ ...p, defaultBillingType: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm">{Object.entries(BILLING_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
              </div>
              <div className="mt-4 space-y-3 rounded-xl border border-slate-100 bg-slate-50 p-4">
                <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={configForm.notificationEmailEnabled} onChange={(e) => setConfigForm((p) => ({ ...p, notificationEmailEnabled: e.target.checked }))} />Habilitar notificações por e-mail</label>
                <label className="flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={configForm.notificationWhatsappEnabled} onChange={(e) => setConfigForm((p) => ({ ...p, notificationWhatsappEnabled: e.target.checked }))} />Habilitar notificações por WhatsApp</label>
                <label className="flex items-center gap-2 text-sm text-slate-700"><input aria-label="Habilitar Asaas neste condomínio" type="checkbox" checked={configForm.asaasEnabled} onChange={(e) => setConfigForm((p) => ({ ...p, asaasEnabled: e.target.checked }))} />Habilitar Asaas neste condomínio</label>
                {configForm.asaasEnabled && (
                  <label className="block"><span className="mb-1 block text-sm text-slate-600">Token do Webhook Asaas</span><input placeholder="Informe o token configurado no Asaas" value={configForm.asaasWebhookToken} onChange={(e) => setConfigForm((p) => ({ ...p, asaasWebhookToken: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
                )}
              </div>
              <div className="mt-4 flex justify-end">
                <button type="button" onClick={handleSaveConfig} disabled={savingConfig} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50">
                  {savingConfig ? "Salvando..." : "Salvar configuração"}
                </button>
              </div>
            </div>
          )}
        </>
      )}

      <Modal open={showPayModal} onClose={() => setShowPayModal(false)} title="Registrar pagamento" footer={<><button type="button" onClick={() => setShowPayModal(false)} className="rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-700">Cancelar</button><button type="button" onClick={handleRegisterPayment} disabled={savingPay} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50">{savingPay ? "Salvando..." : "Registrar"}</button></>}>
        <div className="space-y-4">
          <div className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm text-slate-600">
            <p className="font-medium text-slate-900">{selectedInvoice?.title || "Cobrança"}</p>
            <p>Saldo restante: <span className="font-semibold text-slate-900">{formatCurrency(getRemainingAmount(selectedInvoice))}</span></p>
          </div>
          <label className="block"><span className="mb-1 block text-sm text-slate-600">Valor pago</span><input type="number" min="0" step="0.01" value={payForm.paidAmount} onChange={(e) => setPayForm((p) => ({ ...p, paidAmount: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
          <label className="block"><span className="mb-1 block text-sm text-slate-600">Método</span><select value={payForm.paymentMethod} onChange={(e) => setPayForm((p) => ({ ...p, paymentMethod: e.target.value }))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"><option value="PIX">Pix</option><option value="BOLETO">Boleto</option><option value="TRANSFER">Transferência</option><option value="CASH">Dinheiro</option><option value="OTHER">Outro</option></select></label>
          <label className="block"><span className="mb-1 block text-sm text-slate-600">Observações</span><textarea value={payForm.notes} onChange={(e) => setPayForm((p) => ({ ...p, notes: e.target.value }))} className="min-h-24 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm" /></label>
        </div>
      </Modal>

      <Modal open={showPixModal} onClose={() => setShowPixModal(false)} title="Cobrança Pix" size="lg" footer={<>{selectedInvoice?.pixCopyPaste && <button type="button" onClick={() => void handleCopyPix(selectedInvoice.pixCopyPaste)} className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-2 text-sm font-medium text-sky-700">Copiar Pix</button>}{selectedInvoice?.invoiceUrl && <a href={selectedInvoice.invoiceUrl} target="_blank" rel="noreferrer" className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white">Abrir checkout</a>}</>}>
        <div className="grid gap-5 md:grid-cols-[220px,1fr]">
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
            {getPixImageSrc(selectedInvoice?.pixQrCode) ? <img src={getPixImageSrc(selectedInvoice?.pixQrCode) ?? ""} alt="QR Code Pix" className="mx-auto h-44 w-44 rounded-lg bg-white p-3" /> : <div className="flex h-44 items-center justify-center rounded-lg border border-dashed border-slate-200 bg-white text-sm text-slate-400">QR Code indisponível</div>}
          </div>
          <div className="space-y-3">
            <div><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Código copia e cola</p><div className="mt-1 rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-xs text-slate-700">{selectedInvoice?.pixCopyPaste || "Pix não disponível"}</div></div>
            <div className="grid gap-3 md:grid-cols-2">
              <div className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm text-slate-600"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Valor</p><p className="mt-1 font-medium text-slate-900">{formatCurrency(selectedInvoice?.amount)}</p></div>
              <div className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm text-slate-600"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Vencimento</p><p className="mt-1 font-medium text-slate-900">{formatDate(selectedInvoice?.dueDate)}</p></div>
            </div>
          </div>
        </div>
      </Modal>

      <Modal open={showDetailModal} onClose={() => setShowDetailModal(false)} title="Detalhes da cobrança" size="lg" footer={<div className="flex flex-wrap justify-end gap-2">{invoiceDetail?.boletoUrl && <a href={invoiceDetail.boletoUrl} target="_blank" rel="noreferrer" className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-sm font-medium text-amber-700">Ver boleto</a>}{invoiceDetail?.pixCopyPaste && <button type="button" onClick={() => { setSelectedInvoice(invoiceDetail); setShowPixModal(true); }} className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-2 text-sm font-medium text-sky-700">Ver Pix</button>}{isManager && invoiceDetail && canRegisterManualPayment(invoiceDetail.status) && <button type="button" onClick={() => openPayModal(invoiceDetail)} className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-medium text-emerald-700">Registrar pagamento</button>}{isManager && invoiceDetail && canResolveManually(invoiceDetail.status) && <><button type="button" onClick={() => void handleManualStatusAction(invoiceDetail, "cancel")} className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-medium text-rose-700">Cancelar</button><button type="button" onClick={() => void handleManualStatusAction(invoiceDetail, "waive")} className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-medium text-blue-700">Dispensar</button></>}{isManager && invoiceDetail && !invoiceDetail.externalChargeId && (config?.asaasEnabled ? <button type="button" onClick={() => void handleCreateExternalCharge(invoiceDetail)} disabled={creatingExternalCharge} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50">{creatingExternalCharge ? "Criando..." : "Criar cobrança externa"}</button> : <span className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-2 text-sm text-slate-500">Cobrança externa não configurada para este condomínio</span>)}</div>}>
        {invoiceDetail ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${resolveStatusColor(invoiceDetail.status)}`}>{resolveStatusLabel(invoiceDetail.status)}</span>
              <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">{CHARGE_TYPE_LABELS[invoiceDetail.chargeType] || invoiceDetail.chargeType}</span>
              <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">{resolveBillingTypeLabel(invoiceDetail.billingType)}</span>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
                <p className="text-lg font-semibold text-slate-900">{invoiceDetail.title || CHARGE_TYPE_LABELS[invoiceDetail.chargeType]}</p>
                <p className="mt-1 text-sm text-slate-600">{invoiceDetail.description || "Sem descrição."}</p>
                <div className="mt-4 grid gap-3 text-sm text-slate-600 md:grid-cols-2">
                  <div><span className="font-medium text-slate-900">Condomínio:</span> {invoiceDetail.condominiumName || `#${invoiceDetail.condominiumId}`}</div>
                  <div><span className="font-medium text-slate-900">Unidade:</span> {invoiceDetail.unitLabel || `#${invoiceDetail.unitId}`}</div>
                  <div><span className="font-medium text-slate-900">Competência:</span> {invoiceDetail.referenceMonth || "—"}</div>
                  <div><span className="font-medium text-slate-900">Vencimento:</span> {formatDate(invoiceDetail.dueDate)}</div>
                  <div><span className="font-medium text-slate-900">Valor:</span> {formatCurrency(invoiceDetail.amount)}</div>
                  <div><span className="font-medium text-slate-900">Pago:</span> {formatCurrency(invoiceDetail.paidAmount)}</div>
                  <div><span className="font-medium text-slate-900">Saldo:</span> {formatCurrency(getRemainingAmount(invoiceDetail))}</div>
                  <div><span className="font-medium text-slate-900">Pago em:</span> {formatDateTime(invoiceDetail.paidAt)}</div>
                </div>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm text-slate-600">
                <p className="font-semibold text-slate-900">Cobrança externa</p>
                <div className="mt-3 space-y-2">
                  <p><span className="font-medium text-slate-900">Provider:</span> {invoiceDetail.externalProvider || "Manual"}</p>
                  <p><span className="font-medium text-slate-900">Charge ID:</span> {invoiceDetail.externalChargeId || "—"}</p>
                  <p><span className="font-medium text-slate-900">Status externo:</span> {invoiceDetail.externalStatus || "—"}</p>
                  <p><span className="font-medium text-slate-900">Webhook:</span> {formatDateTime(invoiceDetail.lastWebhookAt)}</p>
                  <p><span className="font-medium text-slate-900">Última notificação:</span> {formatDateTime(invoiceDetail.lastNotificationAt)}</p>
                  {!config?.asaasEnabled && <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-amber-800">Cobrança externa não configurada para este condomínio. O fluxo manual continua disponível.</div>}
                </div>
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <p className="mb-2 text-sm font-semibold text-slate-900">Timeline de eventos</p>
                <div className="space-y-2">
                  {invoiceDetail.events?.length ? invoiceDetail.events.map((event) => <div key={event.id} className="rounded-lg border border-slate-100 bg-white p-3 text-sm"><div className="flex items-center justify-between gap-3"><p className="font-medium text-slate-900">{event.type}</p><span className="text-xs text-slate-400">{formatDateTime(event.createdAt)}</span></div><p className="mt-1 text-slate-600">{event.description}</p>{event.source && <p className="mt-1 text-xs text-slate-400">Origem: {event.source}</p>}</div>) : <div className="rounded-lg border border-slate-100 bg-white p-3 text-sm text-slate-500">Nenhum evento registrado.</div>}
                </div>
              </div>
              <div>
                <p className="mb-2 text-sm font-semibold text-slate-900">Notificações</p>
                <div className="space-y-2">
                  {invoiceDetail.notifications?.length ? invoiceDetail.notifications.map((notification) => <div key={notification.id} className="rounded-lg border border-slate-100 bg-white p-3 text-sm"><div className="flex items-center justify-between gap-3"><p className="font-medium text-slate-900">{notification.type}</p><span className="text-xs text-slate-400">{formatDateTime(notification.createdAt)}</span></div><p className="mt-1 text-slate-600">{notification.message}</p><p className="mt-1 text-xs text-slate-400">Canal: {notification.channel} • Status: {notification.status}</p></div>) : <div className="rounded-lg border border-slate-100 bg-white p-3 text-sm text-slate-500">Nenhuma notificação registrada.</div>}
                </div>
              </div>
            </div>
          </div>
        ) : <div className="text-sm text-slate-500">Carregando detalhes...</div>}
      </Modal>
    </div>
  );
}

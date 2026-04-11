import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import api from "../lib/api";
import Modal from "../components/Modal";
import { useToast } from "../components/Toast";

type Invoice = {
  id: number;
  unitId: number;
  unitLabel?: string;
  residentName?: string;
  referenceMonth: string;
  chargeType: string;
  title?: string;
  description?: string;
  amount: number;
  dueDate: string;
  status: string;
  paidAmount?: number;
  boletoUrl?: string;
  invoiceUrl?: string;
  pixCopyPaste?: string;
  pixQrCode?: string;
  pixExpiresAt?: string;
};

type PageData<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

const STATUS_LABELS: Record<string, string> = {
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

function formatCurrency(value?: number | null) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(value ?? 0));
}

function formatDate(value?: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString("pt-BR");
}

function resolveStatusLabel(status?: string) {
  return STATUS_LABELS[status ?? ""] ?? status ?? "—";
}

function resolveStatusColor(status?: string) {
  return STATUS_COLORS[status ?? ""] ?? "bg-slate-100 text-slate-700";
}

function getPixImageSrc(encoded?: string) {
  if (!encoded) return null;
  return encoded.startsWith("data:") ? encoded : `data:image/png;base64,${encoded}`;
}

function resolveApiErrorMessage(err: any, fallback: string) {
  const data = err?.response?.data;
  if (typeof data?.message === "string" && data.message.trim()) return data.message;
  if (typeof data?.detail === "string" && data.detail.trim()) return data.detail;
  if (typeof data?.error === "string" && data.error.trim()) return data.error;
  if (typeof data === "string" && data.trim()) return data;
  return fallback;
}

export default function MyInvoicesPage() {
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [searchInput, setSearchInput] = useState(() => searchParams.get("q") ?? "");
  const [page, setPage] = useState<PageData<Invoice>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 12,
  });
  const [loading, setLoading] = useState(true);
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);
  const [showPixModal, setShowPixModal] = useState(false);

  const status = searchParams.get("status") ?? "";
  const referenceMonthFrom = searchParams.get("refFrom") ?? "";
  const referenceMonthTo = searchParams.get("refTo") ?? "";
  const query = searchParams.get("q") ?? "";
  const currentPage = Number(searchParams.get("page") ?? 0);

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
    async function load() {
      try {
        setLoading(true);
        const response = await api.get("/api/financial/my-invoices", {
          params: {
            status: status || undefined,
            referenceMonthFrom: referenceMonthFrom || undefined,
            referenceMonthTo: referenceMonthTo || undefined,
            q: query || undefined,
            page: currentPage,
            size: 12,
            sortBy: "dueDate",
            direction: "DESC",
          },
        });
        setPage({
          content: response.data?.content ?? [],
          totalElements: Number(response.data?.totalElements ?? 0),
          totalPages: Number(response.data?.totalPages ?? 0),
          number: Number(response.data?.number ?? 0),
          size: Number(response.data?.size ?? 12),
        });
      } catch (err: any) {
        setPage({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 12 });
        toast.show({ type: "error", msg: resolveApiErrorMessage(err, "Erro ao carregar suas faturas") });
      } finally {
        setLoading(false);
      }
    }

    load();
  }, [currentPage, query, referenceMonthFrom, referenceMonthTo, status, toast]);

  async function handleCopyPix(text?: string) {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      toast.show({ type: "success", msg: "Código Pix copiado." });
    } catch {
      toast.show({ type: "error", msg: "Não foi possível copiar o Pix automaticamente." });
    }
  }

  return (
    <div className="max-w-6xl p-6">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
          Minhas Faturas
        </h1>
        <p className="mt-1 text-sm text-slate-500">Acompanhe suas cobranças, copie o Pix e acesse o boleto quando disponível.</p>
      </div>

      <div className="mb-4 grid gap-3 rounded-xl border border-slate-100 bg-white p-4 shadow-sm md:grid-cols-4">
        <label className="block md:col-span-2">
          <span className="mb-1 block text-xs font-medium text-slate-500">Buscar</span>
          <input
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Título ou descrição"
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-xs font-medium text-slate-500">Status</span>
          <select
            value={status}
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
          <span className="mb-1 block text-xs font-medium text-slate-500">Competência inicial</span>
          <input
            type="month"
            value={referenceMonthFrom}
            onChange={(event) => patchParams({ refFrom: event.target.value }, true)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-xs font-medium text-slate-500">Competência final</span>
          <input
            type="month"
            value={referenceMonthTo}
            onChange={(event) => patchParams({ refTo: event.target.value }, true)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
          />
        </label>
      </div>

      {loading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {[1, 2, 3].map((item) => (
            <div key={item} className="h-48 animate-pulse rounded-xl border border-slate-100 bg-white" />
          ))}
        </div>
      ) : page.content.length === 0 ? (
        <div className="rounded-xl border border-slate-100 bg-white p-10 text-center text-sm text-slate-500 shadow-sm">
          Nenhuma fatura encontrada.
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {page.content.map((invoice) => (
            <article key={invoice.id} className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div>
                  <p className="text-lg font-semibold text-slate-900">{invoice.title || invoice.referenceMonth}</p>
                  <p className="text-sm text-slate-500">{invoice.referenceMonth}</p>
                </div>
                <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${resolveStatusColor(invoice.status)}`}>
                  {resolveStatusLabel(invoice.status)}
                </span>
              </div>

              <div className="space-y-2 text-sm text-slate-600">
                <p><span className="font-medium text-slate-900">Valor:</span> {formatCurrency(invoice.amount)}</p>
                <p><span className="font-medium text-slate-900">Vencimento:</span> {formatDate(invoice.dueDate)}</p>
                {invoice.description && <p>{invoice.description}</p>}
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {invoice.pixCopyPaste && (
                  <button type="button" onClick={() => void handleCopyPix(invoice.pixCopyPaste)} className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm font-medium text-sky-700">
                    Copiar Pix
                  </button>
                )}
                {invoice.boletoUrl && (
                  <a href={invoice.boletoUrl} target="_blank" rel="noreferrer" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-medium text-amber-700">
                    Ver Boleto
                  </a>
                )}
                {(invoice.pixQrCode || invoice.invoiceUrl) && (
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedInvoice(invoice);
                      setShowPixModal(true);
                    }}
                    className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700"
                  >
                    QR Code
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <div className="mt-4 flex items-center justify-between">
        <p className="text-sm text-slate-500">
          Página {page.number + 1} de {Math.max(1, page.totalPages || 1)} • {page.totalElements} fatura(s)
        </p>
        <div className="flex gap-2">
          <button
            type="button"
            disabled={page.number <= 0}
            onClick={() => patchParams({ page: page.number - 1 })}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Anterior
          </button>
          <button
            type="button"
            disabled={page.totalPages === 0 || page.number >= page.totalPages - 1}
            onClick={() => patchParams({ page: page.number + 1 })}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Próxima
          </button>
        </div>
      </div>

      <Modal
        open={showPixModal}
        onClose={() => setShowPixModal(false)}
        title="Pagamento via Pix"
        size="lg"
        footer={(
          <>
            {selectedInvoice?.pixCopyPaste && (
              <button type="button" onClick={() => void handleCopyPix(selectedInvoice.pixCopyPaste)} className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-2 text-sm font-medium text-sky-700">
                Copiar Pix
              </button>
            )}
            {selectedInvoice?.invoiceUrl && (
              <a href={selectedInvoice.invoiceUrl} target="_blank" rel="noreferrer" className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white">
                Abrir checkout
              </a>
            )}
          </>
        )}
      >
        <div className="grid gap-5 md:grid-cols-[220px,1fr]">
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
            {getPixImageSrc(selectedInvoice?.pixQrCode) ? (
              <img src={getPixImageSrc(selectedInvoice?.pixQrCode) ?? ""} alt="QR Code Pix" className="mx-auto h-44 w-44 rounded-lg bg-white p-3" />
            ) : (
              <div className="flex h-44 items-center justify-center rounded-lg border border-dashed border-slate-200 bg-white text-sm text-slate-400">
                QR Code indisponível
              </div>
            )}
          </div>
          <div className="space-y-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Código copia e cola</p>
              <div className="mt-1 rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-xs text-slate-700">
                {selectedInvoice?.pixCopyPaste || "Pix não disponível"}
              </div>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <div className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm text-slate-600">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Valor</p>
                <p className="mt-1 font-medium text-slate-900">{formatCurrency(selectedInvoice?.amount)}</p>
              </div>
              <div className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm text-slate-600">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Vencimento</p>
                <p className="mt-1 font-medium text-slate-900">{formatDate(selectedInvoice?.dueDate)}</p>
              </div>
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
}

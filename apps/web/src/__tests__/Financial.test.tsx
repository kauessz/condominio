import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import FinancialPage from "../pages/Financial";

const toastShow = vi.fn();
const toastApi = { show: toastShow };
const apiGet = vi.fn();
const apiPut = vi.fn();
const apiPatch = vi.fn();
const apiPost = vi.fn();

vi.mock("recharts", () => {
  const Mock = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
  return {
    ResponsiveContainer: Mock,
    BarChart: Mock,
    Bar: Mock,
    LineChart: Mock,
    Line: Mock,
    CartesianGrid: Mock,
    Tooltip: Mock,
    Legend: Mock,
    XAxis: Mock,
    YAxis: Mock,
  };
});

vi.mock("../lib/api", () => ({
  default: {
    get: (...args: unknown[]) => apiGet(...args),
    put: (...args: unknown[]) => apiPut(...args),
    patch: (...args: unknown[]) => apiPatch(...args),
    post: (...args: unknown[]) => apiPost(...args),
  },
}));

vi.mock("../lib/auth", () => ({
  getUser: () => ({ id: "1", name: "Super", email: "super@test.com", role: "SUPERUSER", condominiumId: 1 }),
}));

vi.mock("../components/Toast", () => ({
  useToast: () => toastApi,
}));

vi.mock("../hooks/useSuperadminCondominiumFilter", () => ({
  useSuperadminCondominiumFilter: () => ({
    selectedCondominiumId: "1",
    setSelectedCondominiumId: vi.fn(),
    isSuperuser: true,
  }),
}));

function renderPage(initialEntries = ["/app/financial"]) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <FinancialPage />
    </MemoryRouter>,
  );
}

describe("Financial page", () => {
  beforeEach(() => {
    apiGet.mockReset();
    apiPut.mockReset();
    apiPatch.mockReset();
    apiPost.mockReset();
    toastShow.mockReset();
    window.localStorage.clear();
  });

  it("carrega a tabela paginada server-side sem crash", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") return Promise.resolve({ data: { items: [] } });
      if (url === "/units") return Promise.resolve({ data: { items: [] } });
      if (url === "/api/financial/config") return Promise.resolve({ data: { config: null } });
      if (url === "/api/financial/summary") return Promise.resolve({ data: {} });
      if (url === "/api/financial/invoices/search") {
        return Promise.resolve({
          data: {
            content: [],
            totalElements: 0,
            totalPages: 0,
            number: 0,
            size: 20,
          },
        });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    renderPage();

    await waitFor(() => expect(screen.getByText("Financeiro")).toBeInTheDocument());
    expect(screen.getByText("Nenhuma cobrança encontrada.")).toBeInTheDocument();
    expect(apiGet).toHaveBeenCalledWith("/api/financial/invoices/search", expect.any(Object));
  });

  it("mantém filtros na query e abre o detalhe com estado sem gateway externo", async () => {
    const invoices = [
      {
        id: 11,
        condominiumId: 1,
        condominiumName: "Condo A",
        unitId: 244,
        unitLabel: "Unidade 244 • Bloco A",
        residentName: "Rafaela Prado",
        referenceMonth: "2026-04",
        chargeType: "MULTA",
        title: "Multa por barulho",
        description: "Ocorrência em área comum",
        amount: 300,
        dueDate: "2026-04-10",
        paidAmount: 0,
        status: "PENDING",
      },
    ];

    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") return Promise.resolve({ data: { items: [] } });
      if (url === "/units") return Promise.resolve({ data: { items: [] } });
      if (url === "/api/financial/config") return Promise.resolve({ data: { config: { asaasEnabled: false } } });
      if (url === "/api/financial/summary") return Promise.resolve({ data: { totalInvoices: 1 } });
      if (url === "/api/financial/invoices/search") {
        return Promise.resolve({
          data: {
            content: invoices,
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 20,
          },
        });
      }
      if (url === "/api/financial/invoices/11") {
        return Promise.resolve({
          data: {
            ...invoices[0],
            events: [],
            notifications: [],
          },
        });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    renderPage(["/app/financial?status=PENDING"]);

    await waitFor(() => expect(screen.getByRole("button", { name: "Ocultar" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("Multa por barulho")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Detalhes" }));

    await waitFor(() => {
      expect(
        screen.getAllByText(/Cobrança externa não configurada para este condomínio/i).length,
      ).toBeGreaterThan(0);
      expect(apiGet).toHaveBeenCalledWith("/api/financial/invoices/11");
    });
  });

  it("mostra e envia o token do webhook Asaas apenas na configuração existente", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") return Promise.resolve({ data: { items: [{ id: 1, name: "Bossa Nova" }] } });
      if (url === "/units") return Promise.resolve({ data: { items: [] } });
      if (url === "/api/financial/config") {
        return Promise.resolve({
          data: {
            config: {
              monthlyFee: 620,
              dueDay: 12,
              lateFeePct: 2,
              interestPct: 1,
              pixKey: "admin@bossanova.com",
              pixKeyType: "EMAIL",
              defaultBillingType: "BOLETO",
              notificationEmailEnabled: true,
              asaasEnabled: false,
            },
          },
        });
      }
      if (url === "/api/financial/summary") return Promise.resolve({ data: { totalInvoices: 0 } });
      if (url === "/api/financial/invoices/search") {
        return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });
    apiPut.mockResolvedValue({ data: {} });

    renderPage();

    await waitFor(() => expect(screen.getByRole("button", { name: "Configuração" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Configuração" }));
    await waitFor(() => expect(screen.getByText("Configuração financeira")).toBeInTheDocument());

    expect(screen.queryByText("Token do Webhook Asaas")).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Habilitar Asaas neste condomínio"));
    expect(await screen.findByText("Token do Webhook Asaas")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Informe o token configurado no Asaas"), {
      target: { value: "segredo-webhook" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Salvar configuração" }));

    await waitFor(() => {
      expect(apiPut).toHaveBeenCalledWith(
        "/api/financial/config",
        expect.objectContaining({
          asaasEnabled: true,
          asaasWebhookToken: "segredo-webhook",
        }),
        expect.any(Object),
      );
    });
  });
});

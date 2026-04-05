import { render, screen, waitFor } from "@testing-library/react";
import FinancialPage from "../pages/Financial";

const toastShow = vi.fn();
const apiGet = vi.fn();

vi.mock("../lib/api", () => ({
  default: {
    get: (...args: unknown[]) => apiGet(...args),
    put: vi.fn(),
    patch: vi.fn(),
  },
}));

vi.mock("../lib/auth", () => ({
  getUser: () => ({ id: "1", name: "Super", email: "super@test.com", role: "SUPERUSER" }),
}));

vi.mock("../components/Toast", () => ({
  useToast: () => ({ show: toastShow }),
}));

describe("Financial page", () => {
  beforeEach(() => {
    apiGet.mockReset();
    toastShow.mockReset();
  });

  it("abre sem crash quando não existe configuração financeira", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") {
        return Promise.resolve({ data: { items: [] } });
      }
      if (url === "/api/financial/invoices") {
        return Promise.resolve({ data: { content: [] } });
      }
      if (url === "/api/financial/config") {
        return Promise.resolve({ data: { config: null } });
      }
      if (url === "/api/financial/summary") {
        return Promise.resolve({ data: {} });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    render(<FinancialPage />);

    await waitFor(() => expect(screen.getByText("Financeiro")).toBeInTheDocument());
    expect(screen.getByText("0")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma cobrança encontrada.")).toBeInTheDocument();
  });
});

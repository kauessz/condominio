import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import Users from "../pages/Users";

const toastShow = vi.fn();
const apiGet = vi.fn();

vi.mock("../lib/api", () => ({
  default: {
    get: (...args: unknown[]) => apiGet(...args),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("../lib/auth", () => ({
  getUser: () => ({ id: "1", name: "Admin", email: "admin@test.com", role: "SUPERUSER" }),
  getRoleLabel: (role?: string) => role ?? "—",
}));

vi.mock("../components/Toast", () => ({
  useToast: () => ({ show: toastShow }),
}));

describe("Users page", () => {
  beforeEach(() => {
    apiGet.mockReset();
    toastShow.mockReset();
  });

  it("busca a listagem com paginação padrão", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") {
        return Promise.resolve({ data: { items: [] } });
      }
      if (url === "/users") {
        return Promise.resolve({
          data: {
            items: [{ id: 1, name: "Ana", email: "ana@test.com", role: "ADMIN", roleLabel: "Administrador", mustChangePassword: false }],
            total: 1,
          },
        });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    render(<Users />);

    await waitFor(() =>
      expect(apiGet).toHaveBeenCalledWith("/users", { params: { q: undefined, page: 0, pageSize: 20 } })
    );
    expect(await screen.findByText("Ana")).toBeInTheDocument();
  });

  it("exibe mensagem de erro sem cair em estado vazio", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") {
        return Promise.resolve({ data: { items: [] } });
      }
      if (url === "/users") {
        return Promise.reject({ response: { data: { message: "Erro interno rastreável" } } });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    render(<Users />);

    await waitFor(() =>
      expect(screen.getByText("Não foi possível carregar os usuários")).toBeInTheDocument()
    );
    expect(screen.queryByText("Nenhum usuário encontrado")).not.toBeInTheDocument();
    expect(toastShow).toHaveBeenCalledWith({ type: "error", msg: "Erro interno rastreável" });
  });

  it("mostra estado vazio apenas quando não há dados", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") {
        return Promise.resolve({ data: { items: [] } });
      }
      if (url === "/users") {
        return Promise.resolve({ data: { items: [], total: 0 } });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    render(<Users />);

    expect(await screen.findByText("Nenhum usuário encontrado")).toBeInTheDocument();
    expect(screen.queryByText("Não foi possível carregar os usuários")).not.toBeInTheDocument();
  });

  it("permite tentar novamente após erro", async () => {
    let userCalls = 0;
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") {
        return Promise.resolve({ data: { items: [] } });
      }
      if (url === "/users") {
        userCalls += 1;
        if (userCalls === 1) {
          return Promise.reject({ response: { data: { message: "Falha de backend" } } });
        }
        return Promise.resolve({
          data: {
            items: [{ id: 2, name: "Bruno", email: "bruno@test.com", role: "MORADOR", roleLabel: "Morador", mustChangePassword: false }],
            total: 1,
          },
        });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    render(<Users />);

    const retry = await screen.findByRole("button", { name: "Tentar novamente" });
    fireEvent.click(retry);

    expect(await screen.findByText("Bruno")).toBeInTheDocument();
  });

  it("carrega unidades amigáveis ao abrir a edição", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/condominiums") {
        return Promise.resolve({ data: { items: [{ id: 10, name: "Condo Demo" }] } });
      }
      if (url === "/users") {
        return Promise.resolve({
          data: {
            items: [{
              id: 1,
              name: "Ana",
              email: "ana@test.com",
              role: "MORADOR",
              roleLabel: "Morador",
              condominiumId: 10,
              unitId: 5,
              mustChangePassword: false,
            }],
            total: 1,
          },
        });
      }
      if (url === "/units") {
        return Promise.resolve({
          data: {
            content: [{ id: 5, number: "101", block: "A" }],
          },
        });
      }
      return Promise.reject(new Error(`unexpected url ${url}`));
    });

    render(<Users />);

    fireEvent.click(await screen.findByTitle("Editar usuário"));

    expect(await screen.findByRole("option", { name: "Unidade 101 • Bloco A" })).toBeInTheDocument();
  });
});

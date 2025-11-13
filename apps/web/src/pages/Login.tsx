import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { setToken } from "../lib/api";
import { useToast } from "../components/Toast";
import { saveAuth, type User, type Role } from "../lib/auth";
import api, { normalizeToken } from "../lib/api";

// tenta extrair o token de vários formatos possíveis
function pickToken(obj: any): string | undefined {
  if (!obj) return undefined;
  return (
    obj.accessToken ??
    obj.token ??
    obj.jwt ??
    obj.access_token ??
    obj.data?.accessToken ??
    obj.data?.token ??
    obj.data?.jwt ??
    obj.data?.access_token
  );
}

export default function Login() {
  const nav = useNavigate();
  const toast = useToast();

  // default já com admin demo pra facilitar o primeiro login
  const [email, setEmail] = useState("admin@demo.com");
  const [password, setPassword] = useState("admin123");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await api.post(
        "/api/auth/login", // <-- rota nova correta do backend
        {
          email: email.trim(),
          password: password.trim(),
        },
        {
          headers: {
            "X-Tenant": "demo",
            "X-Skip-Auth-Redirect": "true",
          },
          // não tratar 4xx/5xx como erro de rede
          validateStatus: () => true,
        }
      );

      // se backend respondeu erro explícito
      if (res.status < 200 || res.status >= 300) {
        const msg =
          res.data?.error ||
          res.data?.message ||
          `Falha no login (HTTP ${res.status})`;
        toast.show({ type: "error", msg });
        return;
      }

      const data = res.data ?? {};
      const rawToken = pickToken(data);
      const tok = normalizeToken(rawToken);

      if (!tok) {
        toast.show({
          type: "error",
          msg:
            "Token não retornado pelo servidor. Verifique o payload de /api/auth/login.",
        });
        console.warn("Resposta /api/auth/login sem token reconhecido:", data);
        return;
      }

      // o backend retorna "role": "SUPER_ADMIN" | "ADMIN" | "RESIDENT" ...
      const roleFromApi: Role | undefined = (
        data.role ??
        data.user?.role ??
        data.data?.role ??
        data.data?.user?.role
      ) as Role | undefined;

      // monta objeto user mínimo.
      // se você quiser depois, você pode chamar /api/auth/me pra pegar name/id reais
      const user: User = {
        id: "admin", // fallback básico
        name: "Admin",
        email: email.trim(),
        role: (roleFromApi ?? "ADMIN") as Role,
      };

      // salva no storage/localStorage
      saveAuth(tok, user, "demo");
      // injeta token default no axios
      setToken(tok);

      toast.show({ type: "success", msg: "Login efetuado" });
      nav("/app/dashboard", { replace: true });
    } catch (err: any) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        err?.message ||
        "Falha no login";
      toast.show({ type: "error", msg });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen grid place-items-center">
      <form
        onSubmit={onSubmit}
        className="w-full max-w-sm bg-white rounded-xl p-6 shadow"
      >
        <h1 className="text-xl font-semibold mb-4">Entrar</h1>

        <label className="block text-sm text-slate-600">Email</label>
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="border rounded-lg p-2 w-full mb-3"
          placeholder="email@exemplo.com"
        />

        <label className="block text-sm text-slate-600">Senha</label>
        <input
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="border rounded-lg p-2 w-full mb-4"
          placeholder="••••••••"
        />

        <button
          disabled={loading}
          className="w-full bg-slate-900 text-white rounded-lg py-2 disabled:opacity-50"
        >
          {loading ? "Entrando..." : "Entrar"}
        </button>
      </form>
    </div>
  );
}
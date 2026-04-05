import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { setToken } from "../lib/api";
import { useToast } from "../components/Toast";
import { saveAuth, type User, type Role } from "../lib/auth";
import api, { normalizeToken } from "../lib/api";

export default function Login() {
  const nav = useNavigate();
  const toast = useToast();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);

    try {
      const res = await api.post(
        "/api/auth/login",
        { email: email.trim(), password: password.trim() },
        {
          headers: { "X-Tenant": "demo", "X-Skip-Auth-Redirect": "true" },
          validateStatus: () => true,
        }
      );

      if (res.status < 200 || res.status >= 300) {
        toast.show({
          type: "error",
          msg: res.data?.message || res.data?.error || `Falha no login (${res.status})`,
        });
        return;
      }

      const data = res.data ?? {};

      const rawToken = data.token ?? data.accessToken ?? data.jwt ?? data.access_token;
      const tok = normalizeToken(rawToken);

      if (!tok) {
        toast.show({ type: "error", msg: "Token não retornado pelo servidor." });
        return;
      }

      const user: User = {
        id: String(data.id ?? ""),
        name: data.name || data.email || email.trim(),
        email: data.email || email.trim(),
        role: (data.role as Role) ?? "ADMIN",
        unitId: data.unitId ?? null,
        condominiumId: data.condominiumId ?? null,
      };

      saveAuth(tok, user, data.tenant ?? "demo");
      setToken(tok);

      toast.show({ type: "success", msg: "Login efetuado" });
      nav("/app/dashboard", { replace: true });
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.message || err?.response?.data?.error || err?.message || "Falha no login",
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        {/* Logo / título */}
        <div className="mb-8 text-center">
          <div className="inline-flex items-center justify-center w-12 h-12 bg-indigo-600 rounded-xl mb-4">
            <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-slate-900" style={{ fontFamily: "'DM Sans', sans-serif" }}>
            CondoHub
          </h1>
          <p className="text-sm text-slate-500 mt-1">Gestão inteligente de condomínios</p>
        </div>

        {/* Card do formulário */}
        <form
          onSubmit={onSubmit}
          className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-4"
        >
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              E-mail
            </label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400 transition-colors"
              placeholder="seu@email.com"
              autoComplete="email"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Senha
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400 transition-colors"
              placeholder="••••••••"
              autoComplete="current-password"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg py-2.5 text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
          >
            {loading ? "Entrando…" : "Entrar"}
          </button>
        </form>

        {/* Link para cadastro */}
        <div className="mt-5 text-center">
          <p className="text-sm text-slate-500">
            Seu condomínio não está cadastrado?{" "}
            <Link
              to="/solicitar-cadastro"
              className="text-indigo-600 hover:text-indigo-700 font-medium hover:underline"
            >
              Solicitar cadastro
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

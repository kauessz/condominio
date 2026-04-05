import { useState } from "react";
import { Link } from "react-router-dom";
import api from "../lib/api";

type FormState = {
  condominiumName: string;
  cnpj: string;
  address: string;
  requesterName: string;
  requesterEmail: string;
  requesterPhone: string;
  requesterRole: string;
};

const initialForm: FormState = {
  condominiumName: "",
  cnpj: "",
  address: "",
  requesterName: "",
  requesterEmail: "",
  requesterPhone: "",
  requesterRole: "SINDICO",
};

/**
 * Página pública de solicitação de cadastro de condomínio.
 * Rota: /solicitar-cadastro
 * Não requer autenticação.
 */
export default function RequestOnboarding() {
  const [form, setForm] = useState<FormState>(initialForm);
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleChange(
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await api.post("/api/onboarding/request", form);
      setSubmitted(true);
    } catch (err: any) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        "Erro ao enviar solicitação. Tente novamente.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  if (submitted) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center px-4">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-10 max-w-md w-full text-center">
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-8 h-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-semibold text-slate-800 mb-2">Solicitação recebida!</h2>
          <p className="text-slate-500 text-sm mb-6">
            Entraremos em contato em até 48 horas com as próximas etapas para ativar seu condomínio.
          </p>
          <Link
            to="/"
            className="text-sm text-blue-600 hover:underline"
          >
            Voltar para o login
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center px-4 py-12">
      <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-8 max-w-lg w-full">
        {/* Header */}
        <div className="mb-8">
          <Link to="/" className="text-sm font-semibold text-slate-800 tracking-tight">
            CondoHub
          </Link>
          <h1 className="text-xl font-semibold text-slate-800 mt-4">
            Cadastrar condomínio
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            Este formulário é comercial e serve para iniciar o onboarding do condomínio.
            As roles internas do sistema são configuradas depois, no painel administrativo.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Dados do Condomínio */}
          <fieldset className="space-y-4">
            <legend className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Dados do condomínio
            </legend>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Nome do condomínio <span className="text-red-500">*</span>
              </label>
              <input
                name="condominiumName"
                value={form.condominiumName}
                onChange={handleChange}
                required
                placeholder="Ex: Condomínio Jardim das Flores"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">CNPJ</label>
                <input
                  name="cnpj"
                  value={form.cnpj}
                  onChange={handleChange}
                  placeholder="00.000.000/0001-00"
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Telefone</label>
                <input
                  name="requesterPhone"
                  value={form.requesterPhone}
                  onChange={handleChange}
                  placeholder="(11) 9 9999-9999"
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Endereço completo
              </label>
              <textarea
                name="address"
                value={form.address}
                onChange={handleChange}
                rows={2}
                placeholder="Rua, número, bairro, cidade — UF"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              />
            </div>
          </fieldset>

          {/* Dados do Solicitante */}
          <fieldset className="space-y-4 pt-2">
            <legend className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Seus dados
            </legend>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Seu nome <span className="text-red-500">*</span>
              </label>
              <input
                name="requesterName"
                value={form.requesterName}
                onChange={handleChange}
                required
                placeholder="Nome completo"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                E-mail <span className="text-red-500">*</span>
              </label>
              <input
                name="requesterEmail"
                type="email"
                value={form.requesterEmail}
                onChange={handleChange}
                required
                placeholder="seu@email.com"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Você é:
              </label>
              <select
                name="requesterRole"
                value={form.requesterRole}
                onChange={handleChange}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              >
                <option value="SINDICO">Síndico</option>
                <option value="ADMINISTRADORA">Administradora</option>
                <option value="CONSTRUTORA">Construtora</option>
                <option value="OUTRO">Outro</option>
              </select>
            </div>
          </fieldset>

          {error && (
            <div className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-medium py-2.5 rounded-lg text-sm transition-colors"
          >
            {loading ? "Enviando..." : "Enviar solicitação"}
          </button>
        </form>

        <p className="text-center text-xs text-slate-400 mt-6">
          Já tem uma conta?{" "}
          <Link to="/" className="text-blue-600 hover:underline">
            Entrar
          </Link>
        </p>
      </div>
    </div>
  );
}

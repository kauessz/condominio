// src/pages/Login.tsx
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, setToken } from "../lib/api";
import { saveAuth } from "../lib/auth";

export default function Login() {
  const [email, setEmail] = useState("admin@demo.com");
  const [password, setPassword] = useState("admin123");
  const [error, setError] = useState("");
  const nav = useNavigate();

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const { data } = await api.post("/auth/login", { email, password });
      setToken(data.token);
      const me = await api.get("/auth/me").then((r) => r.data);
      saveAuth(data.token, me);
      nav("/app");
    } catch (err: any) {
      setError(err?.response?.data?.error || "Erro ao logar");
    }
  }

  return (
    <div className="min-h-screen grid place-items-center p-6">
      <form onSubmit={onSubmit} className="bg-white max-w-sm w-full p-6 rounded-xl shadow-sm space-y-4">
        <h1 className="text-xl font-semibold">Entrar</h1>
        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="Email"
          className="w-full border rounded-lg p-2"
          autoComplete="username"
          autoFocus
        />
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Senha"
          className="w-full border rounded-lg p-2"
          autoComplete="current-password"
        />
        {error && <p className="text-red-600 text-sm">{error}</p>}
        <button className="w-full bg-slate-900 text-white rounded-lg py-2">Entrar</button>
      </form>
    </div>
  );
}
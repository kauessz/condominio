import { Link, NavLink, useNavigate } from "react-router-dom";
import { clearAuth } from "../lib/auth";
import { useCurrentUser } from "../hooks/useCurrentUser";

export default function Navbar() {
  const { user, loading } = useCurrentUser();
  const nav = useNavigate();

  function logout() {
    clearAuth();
    nav("/login");
  }

  return (
    <header className="bg-white shadow-sm">
      <div className="max-w-6xl mx-auto p-6 flex items-center gap-6">
        <Link to="/app" className="font-semibold">Condomínio</Link>

        <nav className="text-sm flex gap-4">
          <NavLink
            to="/app"
            className={({ isActive }) =>
              (isActive ? "text-slate-900" : "text-slate-600") + " hover:underline"
            }
          >
            Dashboard
          </NavLink>
        </nav>

        <div className="ml-auto text-sm flex items-center gap-3">
          {loading ? (
            <span className="text-slate-400">Carregando...</span>
          ) : user ? (
            <>
              <span className="text-slate-500">
                {user.name ?? user.email} • <b>{user.role}</b>
              </span>
              <button onClick={logout} className="text-rose-600 hover:underline">
                Sair
              </button>
            </>
          ) : (
            <Link to="/" className="text-blue-700 hover:underline">Entrar</Link>
          )}
        </div>
      </div>
    </header>
  );
}
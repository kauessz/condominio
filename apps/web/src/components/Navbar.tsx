import { Link, NavLink, useNavigate } from "react-router-dom";
import { getUser, clearAuth } from "../lib/auth";

export default function Navbar() {
  const user = getUser();
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
          {user ? (
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
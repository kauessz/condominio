import { Outlet } from "react-router-dom";
import Sidebar from "./components/Sidebar";
import { ToastProvider } from "./components/Toast";

export default function App() {
  return (
    <ToastProvider>
      <div className="flex min-h-screen bg-slate-50">
        <Sidebar />
        <main className="sidebar-content min-h-screen">
          <Outlet />
        </main>
      </div>
    </ToastProvider>
  );
}

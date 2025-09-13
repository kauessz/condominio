import { Outlet } from "react-router-dom";
import Navbar from "./components/Navbar";
import { ToastProvider } from "./components/Toast";

export default function App() {
  return (
    <ToastProvider>
      <Navbar />
      <main className="max-w-6xl mx-auto p-6">
        <Outlet />
      </main>
    </ToastProvider>
  );
}
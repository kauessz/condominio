import React from "react";
import ReactDOM from "react-dom/client";
import {
  createBrowserRouter,
  RouterProvider,
  Navigate,
} from "react-router-dom";

import App from "./App";
import RequireAuth from "./RequireAuth";

import Dashboard from "./pages/Dashboard";
import Units from "./pages/Units";
import Residents from "./pages/Residents";
import Login from "./pages/Login";
import "./styles/index.css";
import Visitors from "./pages/Visitors";

// ⬇️ importe e use o ToastProvider
import { ToastProvider } from "./components/Toast";

const router = createBrowserRouter([
  // Login direto em "/"
  { path: "/", element: <Login /> },

  // (opcional) manter /login como alias
  { path: "/login", element: <Login /> },

  // área protegida sob /app
  {
    path: "/app",
    element: (
      <RequireAuth>
        <App />
      </RequireAuth>
    ),
    children: [
      // /app → redireciona para /app/dashboard
      { index: true, element: <Navigate to="dashboard" replace /> },

      // --- Dashboard (agora existe /app/dashboard) ---
      { path: "dashboard", element: <Dashboard /> },

      // --- Unidades ---
      { path: "units", element: <Units /> },             // /app/units   (?condoId=...)
      { path: "condos/:id/units", element: <Units /> },  // /app/condos/:id/units

      // --- Moradores ---
      { path: "residents", element: <Residents /> },             // /app/residents
      { path: "condos/:id/residents", element: <Residents /> },  // /app/condos/:id/residents

      // --- Visitantes ---
      { path: "visitors", element: <Visitors /> },            // /app/visitors?condoId=...
      { path: "condos/:id/visitors", element: <Visitors /> }, // opcional

    ],
  },

  // fallback
  { path: "*", element: <Navigate to="/" replace /> },
]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ToastProvider>
      <RouterProvider router={router} />
    </ToastProvider>
  </React.StrictMode>
);
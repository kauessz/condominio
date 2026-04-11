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
import Visitors from "./pages/Visitors";
import Onboarding from "./pages/Onboarding";
import RequestOnboarding from "./pages/RequestOnboarding";
import Users from "./pages/Users";
import Reservations from "./pages/Reservations";
import WorkOrders from "./pages/WorkOrders";
import Parking from "./pages/Parking";
import Assemblies from "./pages/Assemblies";
import Financial from "./pages/Financial";
import MyInvoices from "./pages/MyInvoices";
import Audit from "./pages/Audit";

import { ToastProvider } from "./components/Toast";
import "./styles/index.css";

const router = createBrowserRouter([
  // Login direto em "/"
  { path: "/", element: <Login /> },

  // Alias de login
  { path: "/login", element: <Login /> },

  // Formulário público de solicitação de cadastro (sem autenticação)
  { path: "/solicitar-cadastro", element: <RequestOnboarding /> },

  // Área protegida sob /app
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

      // --- Dashboard (todos os usuários autenticados) ---
      { path: "dashboard", element: <Dashboard /> },

      // --- Unidades ---
      { path: "units",                element: <Units /> },
      { path: "condos/:id/units",     element: <Units /> },

      // --- Moradores ---
      { path: "residents",            element: <Residents /> },
      { path: "condos/:id/residents", element: <Residents /> },

      // --- Visitantes ---
      { path: "visitors",             element: <Visitors /> },
      { path: "condos/:id/visitors",  element: <Visitors /> },

      // --- Onboarding (SUPERUSER) ---
      { path: "onboarding",           element: <Onboarding /> },

      // --- Usuários ---
      { path: "users",                element: <Users /> },

      // --- Fase 2: Reservas de Áreas Comuns ---
      { path: "reservations",         element: <Reservations /> },

      // --- Fase 2: Ordens de Serviço ---
      { path: "work-orders",          element: <WorkOrders /> },

      // --- Fase 2: Vagas de Estacionamento ---
      { path: "parking",              element: <Parking /> },

      // --- Fase 2: Assembleias ---
      { path: "assemblies",           element: <Assemblies /> },

      // --- Fase 2: Financeiro ---
      { path: "financial",            element: <Financial /> },
      { path: "my-invoices",          element: <MyInvoices /> },

      // --- Auditoria ---
      { path: "audit",                element: <Audit /> },
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

import React from "react";
import ReactDOM from "react-dom/client";
import {
  createBrowserRouter,
  RouterProvider,
  Navigate,
} from "react-router-dom";

import App from "./App";
import RequireAuth from "./RequireAuth";
// import ProtectedRoute from "./components/ProtectedRoute"; // Descomente quando for usar

import Dashboard from "./pages/Dashboard";
import Units from "./pages/Units";
import Residents from "./pages/Residents";
import Login from "./pages/Login";
import Visitors from "./pages/Visitors";

import { ToastProvider } from "./components/Toast";
import "./styles/index.css";

const router = createBrowserRouter([
  // Login direto em "/"
  { path: "/", element: <Login /> },

  // (opcional) manter /login como alias
  { path: "/login", element: <Login /> },

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

      // --- Unidades (somente ADMIN pode criar/editar) ---
      { 
        path: "units", 
        element: <Units /> 
      },
      { 
        path: "condos/:id/units", 
        element: <Units /> 
      },

      // --- Moradores (somente ADMIN pode criar/editar) ---
      { 
        path: "residents", 
        element: <Residents /> 
      },
      { 
        path: "condos/:id/residents", 
        element: <Residents /> 
      },

      // --- Visitantes (ADMIN e STAFF podem gerenciar) ---
      { 
        path: "visitors", 
        element: <Visitors /> 
      },
      { 
        path: "condos/:id/visitors", 
        element: <Visitors /> 
      },

      // --- EXEMPLO: Rota protegida apenas para ADMIN ---
      // Descomente quando criar a página de administração e descomentar o import do ProtectedRoute
      /*
      { 
        path: "admin", 
        element: (
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminPanel />
          </ProtectedRoute>
        )
      },
      */

      // --- EXEMPLO: Rota para RESIDENT (morador) ---
      // Descomente quando criar a área do morador
      /*
      { 
        path: "my-unit", 
        element: (
          <ProtectedRoute allowedRoles={['RESIDENT']}>
            <MyUnit />
          </ProtectedRoute>
        )
      },
      */

      // --- EXEMPLO: Rota para STAFF (porteiro) ---
      /*
      { 
        path: "check-in", 
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'ADMIN']}>
            <CheckIn />
          </ProtectedRoute>
        )
      },
      */
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
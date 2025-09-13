// apps/web/src/main.tsx
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
      { index: true, element: <Dashboard /> }, // /app

      // --- Unidades ---
      { path: "units", element: <Units /> },             // /app/units   (?condoId=...)
      { path: "condos/:id/units", element: <Units /> },  // /app/condos/:id/units

      // --- Moradores ---
      { path: "residents", element: <Residents /> },             // /app/residents
      { path: "condos/:id/residents", element: <Residents /> },  // /app/condos/:id/residents
    ],
  },

  // fallback
  { path: "*", element: <Navigate to="/" replace /> },
]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
);
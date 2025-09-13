import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";

type Toast = { id: number; type: "success" | "error" | "info"; msg: string; timeout?: number };

const ToastCtx = createContext<{ show: (t: Omit<Toast, "id">) => void } | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [list, setList] = useState<Toast[]>([]);

  const show = useCallback((t: Omit<Toast, "id">) => {
    const id = Date.now() + Math.random();
    const timeout = t.timeout ?? 2800;
    setList((old) => [...old, { ...t, id }]);
    setTimeout(() => setList((old) => old.filter((x) => x.id !== id)), timeout);
  }, []);

  const api = useMemo(() => ({ show }), [show]);

  return (
    <ToastCtx.Provider value={api}>
      {children}
      <div className="fixed bottom-4 right-4 space-y-2 z-50">
        {list.map((t) => (
          <div
            key={t.id}
            className={
              "px-4 py-3 rounded-lg shadow text-white " +
              (t.type === "success" ? "bg-emerald-600" : t.type === "error" ? "bg-rose-600" : "bg-slate-700")
            }
          >
            {t.msg}
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastCtx);
  if (!ctx) throw new Error("useToast must be used within <ToastProvider>");
  return ctx;
}
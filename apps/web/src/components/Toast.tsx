import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";

type ToastType = "success" | "error" | "info";
type Toast = { id: number; type: ToastType; msg: string; timeout?: number };

const ToastCtx = createContext<{ show: (t: Omit<Toast, "id">) => void } | null>(null);

// ── Ícones por tipo ──────────────────────────────────────────────
function ToastIcon({ type }: { type: ToastType }) {
  if (type === "success")
    return (
      <svg className="w-4 h-4 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
      </svg>
    );
  if (type === "error")
    return (
      <svg className="w-4 h-4 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
      </svg>
    );
  return (
    <svg className="w-4 h-4 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

const STYLES: Record<ToastType, string> = {
  success: "bg-emerald-50 border border-emerald-200 text-emerald-800",
  error:   "bg-rose-50   border border-rose-200   text-rose-800",
  info:    "bg-blue-50   border border-blue-200   text-blue-800",
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [list, setList] = useState<Toast[]>([]);

  const show = useCallback((t: Omit<Toast, "id">) => {
    const id = Date.now() + Math.random();
    const timeout = t.timeout ?? 4000;
    setList((old) => [...old, { ...t, id }]);
    setTimeout(() => setList((old) => old.filter((x) => x.id !== id)), timeout);
  }, []);

  const api = useMemo(() => ({ show }), [show]);

  return (
    <ToastCtx.Provider value={api}>
      {children}
      {/* Canto inferior direito */}
      <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2 pointer-events-none">
        {list.map((t) => (
          <div
            key={t.id}
            className={`pointer-events-auto flex items-start gap-2.5 min-w-[260px] max-w-[380px] px-4 py-3 rounded-xl shadow-lg text-sm font-medium animate-slideUp ${STYLES[t.type]}`}
          >
            <ToastIcon type={t.type} />
            <span className="flex-1">{t.msg}</span>
            <button
              onClick={() => setList((old) => old.filter((x) => x.id !== t.id))}
              className="opacity-50 hover:opacity-100 transition-opacity text-current flex-shrink-0"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
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

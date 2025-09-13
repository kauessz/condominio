import type { ReactNode } from "react";

export default function Card({ children }: { children: ReactNode }) {
  return (
    <div className="bg-white p-4 rounded-xl shadow-sm border border-slate-100">
      {children}
    </div>
  );
}
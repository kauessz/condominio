import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";

type CondoCard = {
  id: string;
  name: string;
  cnpj: string;
  _count?: { units: number; residents: number };
  createdAt?: string;
};

export default function Dashboard() {
  const [condos, setCondos] = useState<CondoCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        setLoading(true);
        setError(null);

        // Spring Data é 0-based: primeira página é page=0
        const r = await api.get("/condominiums", { params: { page: 0, pageSize: 8 } });

        // Normaliza: aceita Page {content}, {items} ou array direto
        const raw =
          Array.isArray(r.data) ? r.data :
          r.data?.content      ?? r.data?.items ?? [];

        const list: CondoCard[] = (raw as any[]).map((it) => ({
          id: String(it.id ?? it.uuid ?? it._id ?? ""),
          name: it.name ?? "",
          cnpj: it.cnpj ?? "",
          _count: it._count,
          createdAt: it.createdAt ?? it.created_at,
        }));

        setCondos(list);
      } catch (e: any) {
        setError(e?.response?.data?.error ?? e?.message ?? "Erro ao carregar condomínios");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) return <div className="p-6">Carregando…</div>;
  if (error)   return <div className="p-6 text-red-600">{error}</div>;

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-semibold">Dashboard</h1>

      {condos.length === 0 ? (
        <p className="text-slate-500">Nenhum condomínio encontrado.</p>
      ) : (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {condos.map((c) => (
            <Link key={c.id} to={`/app/units?condoId=${c.id}`} className="block">
              <div className="bg-white rounded-xl shadow-sm p-4 hover:shadow-md transition">
                <div className="font-medium">{c.name}</div>
                <div className="text-sm text-slate-500">CNPJ {c.cnpj || "—"}</div>
                <div className="text-sm text-slate-600 mt-1">
                  Unidades: {c._count?.units ?? 0} | Moradores: {c._count?.residents ?? 0}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
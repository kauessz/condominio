import { useEffect, useState, useCallback } from "react";
import api from "../lib/api";
import { getRoleLabel, getUser } from "../lib/auth";
import { useToast } from "../components/Toast";
import Modal from "../components/Modal";

// ─── Tipos ──────────────────────────────────────────────────────────────────

type UserItem = {
  id: number;
  name: string;
  email: string;
  role: string;
  roleLabel: string;
  condominiumId?: number | null;
  unitId?: number | null;
  residentId?: number | null;
  mustChangePassword: boolean;
  createdAt?: string;
};

type Condo = { id: number; name: string };
type UnitOption = { id: number; number?: string; block?: string; code?: string };

type EditState = {
  userId: number;
  role: string;
  condominiumId: string;
  unitId: string;
};

type CreateState = {
  name: string;
  email: string;
  password: string;
  role: string;
  condominiumId: string;
  unitId: string;
};

const ALL_ROLES = [
  { value: "SUPERUSER", label: "Super Admin" },
  { value: "ADMIN",     label: "Administrador" },
  { value: "SINDICO",   label: "Síndico" },
  { value: "FINANCEIRO",label: "Financeiro" },
  { value: "OPERADOR",  label: "Operador" },
  { value: "ZELADOR",   label: "Zelador" },
  { value: "PORTARIA",  label: "Portaria" },
  { value: "MORADOR",   label: "Morador" },
];

const ROLE_BADGE: Record<string, string> = {
  SUPERUSER: "bg-purple-100 text-purple-700",
  ADMIN:     "bg-blue-100   text-blue-700",
  SINDICO:   "bg-emerald-100 text-emerald-700",
  FINANCEIRO:"bg-cyan-100 text-cyan-700",
  OPERADOR:  "bg-orange-100 text-orange-700",
  ZELADOR:   "bg-amber-100  text-amber-700",
  PORTARIA:  "bg-slate-100  text-slate-600",
  MORADOR:   "bg-rose-100   text-rose-700",
};

// ── Avatar com iniciais ──────────────────────────────────────────
function Avatar({ name, email }: { name?: string | null; email: string }) {
  const initials = name
    ? name.split(" ").slice(0, 2).map((w) => w[0]).join("").toUpperCase()
    : email[0].toUpperCase();

  const colors = [
    "bg-indigo-100 text-indigo-700",
    "bg-emerald-100 text-emerald-700",
    "bg-rose-100 text-rose-700",
    "bg-amber-100 text-amber-700",
    "bg-purple-100 text-purple-700",
  ];
  const color = colors[email.charCodeAt(0) % colors.length];

  return (
    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 ${color}`}>
      {initials}
    </div>
  );
}

// ─── Componente principal ────────────────────────────────────────────────────

export default function Users() {
  const toast = useToast();
  const currentUser = getUser();
  const isSuperuser = currentUser?.role === "SUPERUSER";
  const isAdmin = currentUser?.role === "ADMIN";
  const isSindico = currentUser?.role === "SINDICO";
  const canManageUsers = isSuperuser || isAdmin;

  const [users, setUsers]     = useState<UserItem[]>([]);
  const [total, setTotal]     = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [page, setPage]       = useState(0);
  const pageSize              = 20;
  const [q, setQ]             = useState("");

  const [condos, setCondos]   = useState<Condo[]>([]);
  const [units, setUnits]     = useState<UnitOption[]>([]);
  const [editState, setEditState] = useState<EditState | null>(null);
  const [saving, setSaving]       = useState(false);
  const [deleteId, setDeleteId]   = useState<number | null>(null);
  const [deleteName, setDeleteName] = useState("");
  const [createState, setCreateState] = useState<CreateState | null>(null);

  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  // Fetch condos para o select
  useEffect(() => {
    if (!isSuperuser) return;
    api.get("/condominiums", { params: { pageSize: 100 } })
      .then((r) => {
        const raw = r.data;
        const list: Condo[] = Array.isArray(raw.content) ? raw.content
          : Array.isArray(raw.items) ? raw.items
          : Array.isArray(raw) ? raw : [];
        setCondos(list);
      })
      .catch(() => {});
  }, [isSuperuser]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setLoadError(null);
      const r = await api.get("/users", { params: { q: q || undefined, page, pageSize } });
      const raw = r.data;
      if (Array.isArray(raw.content)) {
        setUsers(raw.content);
        setTotal(raw.totalElements ?? raw.content.length);
      } else if (Array.isArray(raw.items)) {
        setUsers(raw.items);
        setTotal(raw.total ?? raw.items.length);
      } else {
        setUsers([]);
        setTotal(0);
      }
    } catch (err: any) {
      const message =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        "Falha ao carregar usuários";
      toast.show({ type: "error", msg: message });
      setLoadError(message);
      setUsers([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [q, page, pageSize]);

  useEffect(() => { load(); }, [load]);

  function openEdit(u: UserItem) {
    setEditState({
      userId:        u.id,
      role:          u.role,
      condominiumId: u.condominiumId != null ? String(u.condominiumId) : "",
      unitId:        u.unitId != null ? String(u.unitId) : "",
    });
  }

  function roleRequiresUnit(role: string) {
    return ["MORADOR", "SINDICO", "ZELADOR"].includes(role);
  }

  function availableRoles() {
    if (isSuperuser) {
      return ALL_ROLES;
    }
    if (isAdmin) {
      return ALL_ROLES.filter((role) => !["SUPERUSER", "ADMIN"].includes(role.value));
    }
    return ALL_ROLES.filter((role) => role.value === "MORADOR");
  }

  function formatUnitLabel(unit: UnitOption) {
    const base = unit.number || unit.code || `#${unit.id}`;
    return unit.block ? `Unidade ${base} • Bloco ${unit.block}` : `Unidade ${base}`;
  }

  useEffect(() => {
    const selectedCondominiumId = editState?.condominiumId ?? createState?.condominiumId ?? "";
    if (!selectedCondominiumId) {
      setUnits([]);
      return;
    }
    api.get("/units", {
      params: { condominiumId: Number(selectedCondominiumId), page: 0, size: 100 },
    })
      .then((r) => {
        const raw = r.data;
        const list: UnitOption[] = Array.isArray(raw.content)
          ? raw.content
          : Array.isArray(raw.items)
            ? raw.items
            : Array.isArray(raw)
              ? raw
              : [];
        setUnits(list);
      })
      .catch(() => setUnits([]));
  }, [editState?.condominiumId, createState?.condominiumId]);

  async function saveEdit() {
    if (!editState) return;
    const role = editState.role;
    const condominiumId = editState.condominiumId !== "" ? Number(editState.condominiumId) : 0;
    const unitId = editState.unitId !== "" ? Number(editState.unitId) : 0;

    if (role !== "SUPERUSER" && condominiumId === 0) {
      toast.show({ type: "error", msg: "Selecione um condomínio para esta role." });
      return;
    }

    if (roleRequiresUnit(role) && unitId === 0) {
      toast.show({ type: "error", msg: `Selecione uma unidade para a role ${getRoleLabel(role)}.` });
      return;
    }

    try {
      setSaving(true);
      await api.put(`/users/${editState.userId}`, {
        role,
        condominiumId,
        unitId,
      });
      toast.show({ type: "success", msg: "Usuário atualizado." });
      setEditState(null);
      load();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.error || "Erro ao salvar." });
    } finally {
      setSaving(false);
    }
  }

  function openCreate() {
    setCreateState({
      name: "",
      email: "",
      password: "",
      role: "MORADOR",
      condominiumId: isSuperuser ? "" : String(currentUser?.condominiumId ?? ""),
      unitId: "",
    });
  }

  async function saveCreate() {
    if (!createState) return;
    const condominiumId = createState.condominiumId !== "" ? Number(createState.condominiumId) : undefined;
    const unitId = createState.unitId !== "" ? Number(createState.unitId) : undefined;

    if (!createState.name.trim() || !createState.email.trim() || !createState.password.trim()) {
      toast.show({ type: "error", msg: "Preencha nome, e-mail e senha." });
      return;
    }
    if (createState.role !== "SUPERUSER" && !condominiumId) {
      toast.show({ type: "error", msg: "Selecione um condomínio para esta role." });
      return;
    }
    if (roleRequiresUnit(createState.role) && !unitId) {
      toast.show({ type: "error", msg: `Selecione uma unidade para a role ${getRoleLabel(createState.role)}.` });
      return;
    }

    try {
      setSaving(true);
      await api.post("/users", {
        name: createState.name.trim(),
        email: createState.email.trim(),
        password: createState.password,
        role: createState.role,
        condominiumId,
        unitId,
      });
      toast.show({ type: "success", msg: "Usuário criado." });
      setCreateState(null);
      load();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.message || err?.response?.data?.error || "Erro ao criar usuário." });
    } finally {
      setSaving(false);
    }
  }

  async function confirmDelete() {
    if (deleteId == null) return;
    try {
      await api.delete(`/users/${deleteId}`);
      toast.show({ type: "success", msg: "Usuário removido." });
      setDeleteId(null);
      load();
    } catch (err: any) {
      toast.show({ type: "error", msg: err?.response?.data?.error || "Erro ao remover." });
    }
  }

  return (
    <div className="p-6 max-w-5xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900" style={{ fontFamily: "var(--font-display)" }}>
            {isSuperuser ? "Gerenciar Usuários" : "Usuários do Condomínio"}
          </h1>
          {total > 0 && (
            <p className="text-sm text-slate-500 mt-0.5">{total} usuário{total !== 1 ? "s" : ""}</p>
          )}
        </div>
        <button
          type="button"
          onClick={openCreate}
          disabled={!canManageUsers}
          className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-xl text-sm font-medium transition-colors"
        >
          Novo usuário
        </button>
      </div>

      {/* Busca */}
      <div className="mb-5">
        <input
          type="text"
          placeholder="Buscar por nome ou e-mail…"
          value={q}
          onChange={(e) => { setQ(e.target.value); setPage(0); }}
          className="border border-slate-200 rounded-lg px-3 py-2 text-sm w-full max-w-sm"
        />
      </div>

      {/* Tabela */}
      {loading ? (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-8 animate-pulse h-64" />
      ) : loadError ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <div className="w-14 h-14 bg-rose-50 rounded-2xl flex items-center justify-center mb-3">
            <svg className="w-7 h-7 text-rose-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zM12 16.5h.008v.008H12V16.5z" />
            </svg>
          </div>
          <h3 className="text-slate-700 font-semibold text-sm">Não foi possível carregar os usuários</h3>
          <p className="text-slate-400 text-xs mt-1">{loadError}</p>
          <button
            type="button"
            onClick={() => void load()}
            className="mt-4 border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-slate-50 transition-colors"
          >
            Tentar novamente
          </button>
        </div>
      ) : users.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <div className="w-14 h-14 bg-slate-100 rounded-2xl flex items-center justify-center mb-3">
            <svg className="w-7 h-7 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </div>
          <h3 className="text-slate-700 font-semibold text-sm">Nenhum usuário encontrado</h3>
          <p className="text-slate-400 text-xs mt-1">Tente ajustar a busca.</p>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 text-left border-b border-slate-100">
                <th className="px-5 py-3.5 font-medium text-slate-600 text-xs uppercase tracking-wide">Usuário</th>
                <th className="px-4 py-3.5 font-medium text-slate-600 text-xs uppercase tracking-wide">Role</th>
                {isSuperuser && (
                  <th className="px-4 py-3.5 font-medium text-slate-600 text-xs uppercase tracking-wide">Condomínio</th>
                )}
                <th className="px-4 py-3.5 font-medium text-slate-600 text-xs uppercase tracking-wide">Vínculo</th>
                <th className="px-4 py-3.5 font-medium text-slate-600 text-xs uppercase tracking-wide">Senha temp.</th>
                {canManageUsers && (
                  <th className="px-4 py-3.5 font-medium text-slate-600 text-xs uppercase tracking-wide text-right">Ações</th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {users.map((u) => (
                <tr key={u.id} className="hover:bg-slate-50/70 transition-colors">
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-3">
                      <Avatar name={u.name} email={u.email} />
                      <div className="min-w-0">
                        <p className="font-medium text-slate-900 text-sm truncate">{u.name || "—"}</p>
                        <p className="text-slate-400 text-xs truncate">{u.email}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3.5">
                    <span className={`inline-block text-xs font-semibold px-2.5 py-1 rounded-full ${ROLE_BADGE[u.role] ?? "bg-slate-100 text-slate-600"}`}>
                      {getRoleLabel(u.role || u.roleLabel)}
                    </span>
                  </td>
                  {isSuperuser && (
                    <td className="px-4 py-3.5 text-slate-500 text-xs">
                      {u.condominiumId != null
                        ? (condos.find((c) => c.id === u.condominiumId)?.name ?? `ID ${u.condominiumId}`)
                        : <span className="text-slate-300 italic">—</span>}
                    </td>
                  )}
                  <td className="px-4 py-3.5 text-slate-500 text-xs">
                    {u.residentId ? `Morador #${u.residentId}` : "Conta avulsa"}
                  </td>
                  <td className="px-4 py-3.5">
                    {u.mustChangePassword ? (
                      <span className="text-xs font-medium text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">Pendente</span>
                    ) : (
                      <span className="text-xs text-slate-400">Normal</span>
                    )}
                  </td>
                  {canManageUsers && (
                    <td className="px-4 py-3.5 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {/* Botão editar */}
                        <button
                          onClick={() => openEdit(u)}
                          title="Editar usuário"
                          className="w-8 h-8 flex items-center justify-center rounded-lg text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 transition-colors"
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                          </svg>
                        </button>
                        {/* Botão excluir */}
                        <button
                          onClick={() => { setDeleteId(u.id); setDeleteName(u.name || u.email); }}
                          title="Excluir usuário"
                          className="w-8 h-8 flex items-center justify-center rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Paginação */}
      {pageCount > 1 && (
        <div className="mt-5 flex items-center gap-3">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-50 transition-colors"
          >
            ← Anterior
          </button>
          <span className="text-sm text-slate-500">Página {page + 1} de {pageCount}</span>
          <button
            type="button"
            disabled={page + 1 >= pageCount}
            onClick={() => setPage((p) => p + 1)}
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-50 transition-colors"
          >
            Próxima →
          </button>
        </div>
      )}

      {/* ── Modal de Edição ────────────────────────────────────────── */}
      <Modal
        open={!!createState}
        onClose={() => setCreateState(null)}
        title="Novo Usuário"
        footer={
          <>
            <button
              onClick={() => setCreateState(null)}
              className="border border-slate-200 rounded-lg px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 transition-colors"
              disabled={saving}
            >
              Cancelar
            </button>
            <button
              onClick={saveCreate}
              disabled={saving}
              className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
            >
              {saving ? "Salvando…" : "Criar usuário"}
            </button>
          </>
        }
      >
        {createState && (
          <div className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Nome</label>
                <input
                  value={createState.name}
                  onChange={(e) => setCreateState((s) => s ? { ...s, name: e.target.value } : s)}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">E-mail</label>
                <input
                  type="email"
                  value={createState.email}
                  onChange={(e) => setCreateState((s) => s ? { ...s, email: e.target.value } : s)}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Senha provisória</label>
              <input
                value={createState.password}
                onChange={(e) => setCreateState((s) => s ? { ...s, password: e.target.value } : s)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Role</label>
              <select
                value={createState.role}
                onChange={(e) => setCreateState((s) => s ? { ...s, role: e.target.value, unitId: "" } : s)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              >
                {availableRoles().map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Condomínio</label>
              <select
                value={createState.condominiumId}
                onChange={(e) => setCreateState((s) => s ? { ...s, condominiumId: e.target.value, unitId: "" } : s)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                disabled={!isSuperuser}
              >
                <option value="">— Nenhum (apenas SUPERUSER) —</option>
                {condos.map((c) => (
                  <option key={c.id} value={String(c.id)}>{c.name}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Unidade</label>
              <select
                value={createState.unitId}
                onChange={(e) => setCreateState((s) => s ? { ...s, unitId: e.target.value } : s)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                disabled={!createState.condominiumId}
              >
                <option value="">{createState.condominiumId ? "Selecione uma unidade…" : "Escolha o condomínio primeiro"}</option>
                {units.map((unit) => (
                  <option key={unit.id} value={String(unit.id)}>{formatUnitLabel(unit)}</option>
                ))}
              </select>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={!!editState}
        onClose={() => setEditState(null)}
        title="Editar Usuário"
        footer={
          <>
            <button
              onClick={() => setEditState(null)}
              className="border border-slate-200 rounded-lg px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 transition-colors"
              disabled={saving}
            >
              Cancelar
            </button>
            <button
              onClick={saveEdit}
              disabled={saving}
              className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
            >
              {saving ? "Salvando…" : "Salvar alterações"}
            </button>
          </>
        }
      >
        {editState && (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Role</label>
              <select
                value={editState.role}
                onChange={(e) => setEditState((s) => s ? { ...s, role: e.target.value } : s)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              >
                {availableRoles().map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Condomínio</label>
              <select
                value={editState.condominiumId}
                onChange={(e) => setEditState((s) => s ? { ...s, condominiumId: e.target.value, unitId: "" } : s)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                disabled={!isSuperuser}
              >
                <option value="">— Nenhum (apenas SUPERUSER) —</option>
                {condos.map((c) => (
                  <option key={c.id} value={String(c.id)}>{c.name}</option>
                ))}
              </select>
              <p className="text-xs text-slate-400 mt-1">Deixe em branco apenas para SUPERUSER.</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Unidade
              </label>
              <select
                value={editState.unitId}
                onChange={(e) => setEditState((s) => s ? { ...s, unitId: e.target.value } : s)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
                disabled={!editState.condominiumId}
              >
                <option value="">{editState.condominiumId ? "Selecione uma unidade…" : "Escolha o condomínio primeiro"}</option>
                {units.map((unit) => (
                  <option key={unit.id} value={String(unit.id)}>{formatUnitLabel(unit)}</option>
                ))}
              </select>
              <p className="text-xs text-slate-400 mt-1">
                Obrigatório para MORADOR, SÍNDICO e ZELADOR. A lista é filtrada pelo condomínio selecionado.
              </p>
            </div>
          </div>
        )}
      </Modal>

      {/* ── Modal de Confirmação de Exclusão ──────────────────────── */}
      <Modal
        open={deleteId != null}
        onClose={() => setDeleteId(null)}
        title="Confirmar exclusão"
        size="sm"
        footer={
          <>
            <button
              onClick={() => setDeleteId(null)}
              className="border border-slate-200 rounded-lg px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 transition-colors"
            >
              Cancelar
            </button>
            <button
              onClick={confirmDelete}
              className="bg-rose-600 hover:bg-rose-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
            >
              Confirmar exclusão
            </button>
          </>
        }
      >
        <p className="text-slate-600 text-sm">
          Tem certeza que deseja excluir o usuário <strong className="text-slate-900">{deleteName}</strong>?
          Esta ação não pode ser desfeita.
        </p>
      </Modal>
    </div>
  );
}

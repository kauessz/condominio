import { useEffect, useState } from "react";
import Modal from "./Modal";
import CNPJField from "./CNPJField";
import { api } from "../lib/api";
import { useToast } from "./Toast";
import { onlyDigits } from "../lib/br";

export type Condo = {
  id: number;
  name: string;
  cnpj: string; // sempre só dígitos no estado
  createdAt?: string;
  units?: number;
  residents?: number;
};

type Props = {
  open: boolean;
  onClose: () => void;
  onSaved: () => void; // recarrega lista no pai
  editing?: Condo | null;
};

export default function CondoModal({ open, onClose, onSaved, editing }: Props) {
  const toast = useToast();

  const [form, setForm] = useState<{ name: string; cnpj: string }>({
    name: "",
    cnpj: "",
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (editing) {
      setForm({
        name: editing.name ?? "",
        cnpj: onlyDigits(editing.cnpj ?? ""),
      });
    } else {
      setForm({ name: "", cnpj: "" });
    }
  }, [editing, open]);

  async function submit() {
    if (!form.name.trim()) {
      toast.show({ type: "error", msg: "Nome é obrigatório" });
      return;
    }
    if (onlyDigits(form.cnpj).length !== 14) {
      toast.show({ type: "error", msg: "CNPJ inválido" });
      return;
    }

    try {
      setSaving(true);
      if (editing) {
        await api.put(`/condos/${editing.id}`, {
          name: form.name.trim(),
          cnpj: onlyDigits(form.cnpj),
        });
        toast.show({ type: "success", msg: "Condomínio atualizado" });
      } else {
        await api.post(`/condos`, {
          name: form.name.trim(),
          cnpj: onlyDigits(form.cnpj),
        });
        toast.show({ type: "success", msg: "Condomínio criado" });
      }
      onClose();
      onSaved();
    } catch (err: any) {
      toast.show({
        type: "error",
        msg: err?.response?.data?.error || "Falha ao salvar condomínio",
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => !saving && onClose()}
      title={editing ? "Editar condomínio" : "Novo condomínio"}
    >
      <div className="space-y-3">
        <div>
          <label className="block text-sm text-slate-600">Nome</label>
          <input
            value={form.name}
            onChange={(e) =>
              setForm((f) => ({ ...f, name: e.target.value }))
            }
            className="border rounded-lg px-3 py-2 w-full"
          />
        </div>

        <CNPJField
          label="CNPJ"
          value={form.cnpj}
          onChange={(digits) => setForm((f) => ({ ...f, cnpj: digits }))}
          required
        />

        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="border px-4 py-2 rounded-lg disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving}
            className="bg-slate-900 text-white px-4 py-2 rounded-lg disabled:opacity-50"
          >
            {saving ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </div>
    </Modal>
  );
}
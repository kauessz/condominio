import { useMemo, useState } from "react";
import { getRoleLabel } from "../../lib/auth";

type Candidate = {
  userId: number;
  name: string;
  role: string;
  unitLabel?: string | null;
};

export default function CandidatePickerField({
  candidates,
  selectedIds,
  onChange,
  disabled = false,
}: {
  candidates: Candidate[];
  selectedIds: number[];
  onChange: (next: number[]) => void;
  disabled?: boolean;
}) {
  const [query, setQuery] = useState("");

  const selectedCandidates = useMemo(
    () => candidates.filter((candidate) => selectedIds.includes(candidate.userId)),
    [candidates, selectedIds]
  );

  const filteredCandidates = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) return candidates;
    return candidates.filter((candidate) =>
      candidate.name.toLowerCase().includes(normalizedQuery)
      || (candidate.unitLabel ?? "").toLowerCase().includes(normalizedQuery)
    );
  }, [candidates, query]);

  function toggleCandidate(userId: number) {
    if (disabled) return;
    if (selectedIds.includes(userId)) {
      onChange(selectedIds.filter((id) => id !== userId));
      return;
    }
    onChange(selectedIds.concat(userId));
  }

  return (
    <div className="space-y-3">
      <input
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Buscar por nome ou unidade"
        className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
        disabled={disabled}
      />

      {selectedCandidates.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {selectedCandidates.map((candidate) => (
            <button
              key={candidate.userId}
              type="button"
              onClick={() => toggleCandidate(candidate.userId)}
              disabled={disabled}
              className="inline-flex items-center gap-2 rounded-full bg-indigo-50 text-indigo-700 px-3 py-1 text-xs font-medium disabled:opacity-60"
            >
              <span>{candidate.name}</span>
              <span className="text-indigo-400">×</span>
            </button>
          ))}
        </div>
      )}

      <div className="max-h-56 overflow-y-auto rounded-xl border border-slate-200 divide-y divide-slate-100 bg-white">
        {filteredCandidates.length === 0 ? (
          <div className="px-3 py-4 text-xs text-slate-400">Nenhum candidato encontrado.</div>
        ) : filteredCandidates.map((candidate) => {
          const checked = selectedIds.includes(candidate.userId);
          return (
            <label
              key={candidate.userId}
              className={`flex items-start gap-3 px-3 py-3 cursor-pointer ${checked ? "bg-indigo-50/60" : "hover:bg-slate-50"} ${disabled ? "opacity-60 cursor-not-allowed" : ""}`}
            >
              <input
                type="checkbox"
                checked={checked}
                onChange={() => toggleCandidate(candidate.userId)}
                disabled={disabled}
                className="mt-0.5 w-4 h-4 rounded text-indigo-600"
              />
              <div className="min-w-0">
                <p className="text-sm font-medium text-slate-800">{candidate.name}</p>
                <p className="text-xs text-slate-500">
                  {candidate.unitLabel ?? "Unidade não informada"} • {getRoleLabel(candidate.role)}
                </p>
              </div>
            </label>
          );
        })}
      </div>
    </div>
  );
}

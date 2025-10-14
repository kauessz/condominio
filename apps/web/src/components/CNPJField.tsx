// apps/web/src/components/CNPJField.tsx
import { useId, useMemo, useState } from "react";
import { maskCNPJ, isValidCNPJ, onlyDigits } from "../lib/br";

type Props = {
  label?: string;
  value: string;                         // pode vir mascarado ou só dígitos
  onChange: (rawDigits: string) => void; // devolve sempre SÓ dígitos (até 14)
  required?: boolean;
  disabled?: boolean;
  name?: string;
  placeholder?: string;
  className?: string;
  // validação externa opcional (ex.: erro vindo do backend)
  errorText?: string | null;
};

export default function CNPJField({
  label = "CNPJ",
  value,
  onChange,
  required,
  disabled,
  name,
  placeholder = "00.000.000/0000-00",
  className = "",
  errorText,
}: Props) {
  const id = useId();

  // exibição sempre mascarada
  const display = useMemo(() => maskCNPJ(value), [value]);

  const [touched, setTouched] = useState(false);
  const has14 = onlyDigits(value).length === 14;
  const valid = has14 && isValidCNPJ(value);

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const digits = onlyDigits(e.target.value).slice(0, 14);
    onChange(digits); // sempre só dígitos para o estado externo
  }

  function handleBlur() {
    setTouched(true);
  }

  const showInvalid = touched && !valid && onlyDigits(value).length > 0;

  return (
    <div className={`w-full ${className}`}>
      {label && (
        <label htmlFor={id} className="block text-sm text-slate-600 mb-1">
          {label} {required ? "*" : ""}
        </label>
      )}

      <input
        id={id}
        name={name}
        value={display}
        onChange={handleChange}
        onBlur={handleBlur}
        placeholder={placeholder}
        disabled={disabled}
        inputMode="numeric"
        className={`border rounded-lg px-3 py-2 w-full ${
          showInvalid ? "border-red-500" : ""
        }`}
      />

      {showInvalid && <div className="text-xs text-red-600 mt-1">CNPJ inválido</div>}

      {!!errorText && !showInvalid && (
        <div className="text-xs text-red-600 mt-1">{errorText}</div>
      )}
    </div>
  );
}
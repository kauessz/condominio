export function maskPhone(v: string) {
  const d = v.replace(/\D/g, "").slice(0, 11); // até 11 dígitos (BR)
  if (d.length <= 10) return d.replace(/(\d{2})(\d{4})(\d{0,4})/, "($1) $2-$3").trim();
  return d.replace(/(\d{2})(\d{5})(\d{0,4})/, "($1) $2-$3").trim();
}

export function isValidPhone(v: string) {
  const d = v.replace(/\D/g, "");
  return d.length === 10 || d.length === 11;
}
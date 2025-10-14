// utilitários simples de BR (CNPJ)

export function onlyDigits(v: string) {
  return (v || "").replace(/\D+/g, "");
}

export function maskCNPJ(v: string) {
  const d = onlyDigits(v).slice(0, 14);
  const p = d
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2");
  return p;
}

export function isValidCNPJ(cnpj: string) {
  let s = onlyDigits(cnpj);
  if (s.length !== 14) return false;
  if (/^(\d)\1+$/.test(s)) return false;

  const calc = (base: string) => {
    let i = base.length - 7, sum = 0;
    for (let n = 0; n < base.length; n++) {
      sum += Number(base[n]) * i--;
      if (i < 2) i = 9;
    }
    const r = sum % 11;
    return r < 2 ? 0 : 11 - r;
  };

  const d1 = calc(s.slice(0, 12));
  const d2 = calc(s.slice(0, 12) + d1);
  return s.endsWith(`${d1}${d2}`);
}
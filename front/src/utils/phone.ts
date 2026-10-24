export function formatPhone(value: unknown) {
  const raw = String(value ?? '')
  if (raw.startsWith('+')) return raw
  const digits = raw.replace(/\D/g, '').slice(0, 11)
  if (digits.startsWith('02')) return digits.replace(/^(\d{2})(\d{3,4})(\d{4})$/, '$1-$2-$3')
  return digits.replace(/^(\d{3})(\d{3,4})(\d{4})$/, '$1-$2-$3')
}

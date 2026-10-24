/** Offset-less server dates retain their wall-clock time; explicit offsets use the existing browser locale zone. */
export function dateParts(value: unknown): { date: string; time: string } | null {
  if (typeof value !== 'string') return null
  const match = value
    .trim()
    .match(
      /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?)?$/,
    )
  if (!match) return null
  const [, y, m, d, hh = '00', mm = '00', ss = '00', zone] = match
  const year = Number(y),
    month = Number(m),
    day = Number(d)
  if (
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > new Date(Date.UTC(year, month, 0)).getUTCDate() ||
    Number(hh) > 23 ||
    Number(mm) > 59 ||
    Number(ss) > 59
  )
    return null
  if (!zone) return { date: `${y}.${m}.${d}`, time: match[4] ? `${hh}:${mm}` : '' }
  const parsed = new Date(value.replace(/(\.\d{3})\d+/, '$1'))
  if (!Number.isFinite(parsed.getTime())) return null
  const pad = (n: number) => String(n).padStart(2, '0')
  return {
    date: `${parsed.getFullYear()}.${pad(parsed.getMonth() + 1)}.${pad(parsed.getDate())}`,
    time: `${pad(parsed.getHours())}:${pad(parsed.getMinutes())}`,
  }
}
export function displayDate(value: unknown, withTime = true, missing = '—'): string {
  const parts = dateParts(value)
  return parts ? `${parts.date}${withTime && parts.time ? ' ' + parts.time : ''}` : missing
}

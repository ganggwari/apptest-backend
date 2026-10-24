type Sample = Record<string, any>
export function riskTimestamp(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) return NaN
  const normalized = value
    .trim()
    .replace(' ', 'T')
    .replace(/(\.\d{3})\d+/, '$1')
  return Date.parse(/(?:Z|[+-]\d{2}:?\d{2})$/.test(normalized) ? normalized : normalized + '+09:00')
}
export function riskChartSeries(
  history: Sample[],
  forecast: Sample[],
  left = 20,
  width = 260,
  bottom = 110,
  height = 100,
  options: { now?: number; maxGapMs?: number; extendToNow?: boolean; forecastGapMs?: number } = {},
) {
  const samples = (rows: Sample[], future: boolean) => {
    const unique = new Map<number, { at: number; score: number }>()
    for (const row of rows) {
      const at = riskTimestamp(future ? row.targetAt : row.predictedAt),
        score = row.riskScore ?? row.score
      if (
        Number.isFinite(at) &&
        score != null &&
        String(score).trim() !== '' &&
        Number.isFinite(Number(score))
      )
        unique.set(at, { at, score: Math.max(0, Math.min(100, Number(score))) })
    }
    return [...unique.values()].sort((a, b) => a.at - b.at)
  }
  const past = samples(history, false),
    latest = past[past.length - 1],
    predictions = samples(forecast, true)
  // Show near-term predictions on the same time scale, never backwards into observations.
  const anchor = latest?.at ?? predictions[0]?.at
  const future =
    anchor === undefined
      ? []
      : predictions.filter(
          (row) => (!latest || row.at > latest.at) && row.at <= anchor + 3 * 3600000,
        )
  const now = options.now ?? Date.now(),
    gap = options.maxGapMs ?? 30 * 60000
  const held =
    latest && options.extendToNow !== false && now > latest.at && now - latest.at <= gap
      ? { at: now, score: latest.score }
      : undefined
  const all = [...past, ...future, ...(held ? [held] : [])].sort((a, b) => a.at - b.at)
  if (!all.length)
    return {
      observed: '',
      forecast: '',
      observedPath: '',
      forecastPath: '',
      carryPath: '',
      ticks: [],
      boundary: null,
      lastObserved: null,
      samples: [],
    }
  const min = all[0]!.at,
    max = all[all.length - 1]!.at
  const x = (at: number) =>
    max === min ? left + width / 2 : left + ((at - min) / (max - min)) * width
  const y = (score: number) => bottom - (score / 100) * height
  const points = (rows: typeof past) => rows.map((row) => `${x(row.at)},${y(row.score)}`).join(' ')
  const path = (rows: typeof past, maxGap: number) =>
    rows
      .map(
        (row, index) =>
          `${!index || row.at - rows[index - 1]!.at > maxGap ? 'M' : 'L'}${x(row.at)},${y(row.score)}`,
      )
      .join(' ')
  const forecastRows =
    future.length && latest
      ? [latest, ...(held && held.at < future[0]!.at ? [held] : []), ...future]
      : future
  // Always bridge the last observation to the first forecast; split only gaps within forecasts.
  const forecastPath = forecastRows
    .map(
      (row, index) =>
        `${!index ? 'M' : index === 1 && latest ? 'L' : row.at - forecastRows[index - 1]!.at > (options.forecastGapMs ?? 3 * 3600000) ? 'M' : 'L'}${x(row.at)},${y(row.score)}`,
    )
    .join(' ')
  const format = (at: number) =>
    new Intl.DateTimeFormat('ko-KR', {
      timeZone: 'Asia/Seoul',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(at)
  const ticks = (max === min ? [min] : [min, min + (max - min) / 2, max]).map((at) => ({
    x: x(at),
    label: format(at),
  }))
  return {
    observedPath: path(past, gap),
    forecastPath,
    carryPath: held && latest ? path([latest, held], Infinity) : '',
    samples: past.map((row) => ({
      x: x(row.at),
      y: y(row.score),
      score: row.score,
      time: new Date(row.at).toISOString(),
    })),
    observed: points(past),
    forecast: points(future.length && latest ? [latest, ...future] : future),
    ticks,
    boundary: latest && future.length ? x(latest.at) : null,
    lastObserved: latest
      ? { x: x(latest.at), y: y(latest.score), score: latest.score, label: format(latest.at) }
      : null,
  }
}

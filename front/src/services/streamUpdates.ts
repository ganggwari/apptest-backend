import type { BackendEvent } from '../composables/useBackendEvents'
export const facilityEvents = ['risk-update', 'risk-level-change', 'water-update', 'control-change']
export function patchFacility<T extends Record<string, any>>(row: T, event: BackendEvent): T {
  if (String(row.underpassId ?? row.id) !== String(event.data.underpassId)) return row
  const data = event.data,
    patch: Record<string, any> = {}
  if (event.name === 'risk-update' || event.name === 'risk-level-change') {
    if (typeof data.riskScore === 'number' && Number.isFinite(data.riskScore)) {
      patch.riskScore = data.riskScore
      patch.currentRiskScore = data.riskScore
    }
    const level = event.name === 'risk-level-change' ? data.to : data.riskLevel
    if (typeof level === 'string') patch.currentRiskLevel = level
    if (typeof data.rainfall1hMm === 'number') patch.rainfallMm = data.rainfall1hMm
    if (data.predictedAt) patch.predictedAt = data.predictedAt
  }
  if (event.name === 'water-update') {
    if (typeof data.waterCm === 'number' && Number.isFinite(data.waterCm))
      patch.currentWaterCm = data.waterCm
    if (typeof data.riskLevel === 'string') patch.currentRiskLevel = data.riskLevel
  }
  if (event.name === 'control-change' && ['NORMAL', 'BLOCKED'].includes(data.status))
    patch.currentStatus = data.status
  if (!Object.keys(patch).length) return row
  patch.updatedAt =
    data.predictedAt || data.measuredAt || data.changedAt || new Date().toISOString()
  return { ...row, ...patch }
}
export function appendRiskHistory<T extends Record<string, any>>(
  rows: T[],
  data: Record<string, any>,
  limit = 100,
): T[] {
  if (typeof data.riskScore !== 'number' || !Number.isFinite(data.riskScore) || !data.predictedAt)
    return rows
  const item = {
    riskScore: data.riskScore,
    rainfallMm: data.rainfall1hMm,
    predictedAt: data.predictedAt,
  } as unknown as T
  return [...rows.filter((row) => row.predictedAt !== item.predictedAt), item]
    .sort((a, b) => Date.parse(a.predictedAt) - Date.parse(b.predictedAt))
    .slice(-limit)
}
export function plateQueueRow(data: Record<string, any>, previous: Record<string, any> = {}) {
  return {
    ...previous,
    ...data,
    id: data.plateLogId,
    reviewStatus: data.reviewStatus || 'PENDING',
    reviewExpiresAt:
      typeof data.remainingSeconds === 'number'
        ? Date.now() + Math.max(0, data.remainingSeconds) * 1000
        : previous.reviewExpiresAt,
  }
}

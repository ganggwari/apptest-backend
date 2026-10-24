export type BusanCamera = {
  id: string
  name: string
  address: string
  url: string
  lat: number
  lng: number
  distance?: number
}
let cached: BusanCamera[] | undefined,
  loadedAt = 0,
  pending: Promise<BusanCamera[]> | undefined
export async function getBusanCameras(): Promise<BusanCamera[]> {
  if (cached && Date.now() - loadedAt < 60000) return cached
  if (pending) return pending
  pending = (async () => {
    const response = await fetch('/map-cctv.json', {
      cache: 'no-store',
      signal: AbortSignal.timeout(15000),
    })
    if (!response.ok) throw Error('부산시 CCTV 목록을 불러오지 못했습니다.')
    const rows = await response.json()
    if (!Array.isArray(rows)) throw Error('CCTV 목록의 형식을 확인하지 못했습니다.')
    cached = rows
      .filter(
        (row) =>
          /^https?:\/\//i.test(row.url || '') &&
          row.lat != null && row.lng != null &&
          Number.isFinite(Number(row.lat)) &&
          Number.isFinite(Number(row.lng)) &&
          Math.abs(Number(row.lat)) <= 90 && Math.abs(Number(row.lng)) <= 180,
      )
      .map((row) => ({
        id: String(row.id),
        name: String(row.name || row.id),
        address: String(row.address || ''),
        url: String(row.url),
        lat: Number(row.lat),
        lng: Number(row.lng),
      }))
    loadedAt = Date.now()
    return cached
  })()
  try {
    return await pending
  } finally {
    pending = undefined
  }
}
export function distanceMeters(lat: number, lng: number, camera: Pick<BusanCamera, 'lat' | 'lng'>) {
  const rad = Math.PI / 180,
    a =
      Math.sin(((camera.lat - lat) * rad) / 2) ** 2 +
      Math.cos(lat * rad) *
        Math.cos(camera.lat * rad) *
        Math.sin(((camera.lng - lng) * rad) / 2) ** 2
  return 6371000 * 2 * Math.asin(Math.min(1, Math.sqrt(a)))
}
export async function getNearbyBusanCctvs(lat: number, lng: number) {
  return (await getBusanCameras())
    .map((camera) => ({ ...camera, distance: distanceMeters(lat, lng, camera) }))
    .filter((camera) => camera.distance <= 5000)
    .sort((a, b) => a.distance - b.distance)
    .slice(0, 15)
}
export async function findFacilityLocation(name: string) {
  const response = await fetch('/facility-coordinates.json')
  if (!response.ok) return null
  const rows = await response.json()
  return Array.isArray(rows) ? rows.find((row) => row.name === name) || null : null
}

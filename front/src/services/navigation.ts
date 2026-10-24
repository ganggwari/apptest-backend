export type Place = { id?: string; name: string; address?: string; lat: number; lng: number }
export type Guide = { name: string; x: number; y: number; distance: number; guidance: string }
export type Route = {
  label?: string
  result_code: number
  summary: { distance: number; duration: number; fare?: { toll: number } }
  sections: { roads: { vertexes: number[] }[]; guides?: Guide[] }[]
}
export type Point = { lat: number; lng: number }
// 백엔드 /api/navigation/route (위험 지하차도 우회) 응답
export type HazardRoute = {
  distance: number
  duration: number
  path: number[][]
  guides: { lat: number; lng: number; name: string; text: string; type: number; at: number }[]
  detoured: boolean
  mandatory: boolean
  passesHazards: string[]
  blockedHazards: string[]
  advisoryHazards: string[]
  message: string | null
  suggestedDetour: null | {
    distance: number
    duration: number
    extraSec: number
    path: number[][]
    guides: HazardRoute['guides']
    message: string | null
  }
}
export function toRoute(
  r: { distance: number; duration: number; path: number[][]; guides: HazardRoute['guides'] },
  label?: string,
): Route {
  const vertexes: number[] = []
  for (const [lat, lng] of r.path) vertexes.push(lng!, lat!)
  // 우회용 경유지 안내("경유지")는 운전자에게 의미가 없어 뺀다
  const guides = r.guides.filter((g) => !/경유지/.test(g.text) && !/경유지/.test(g.name))
  return {
    label,
    result_code: 0,
    summary: { distance: r.distance, duration: r.duration },
    sections: [
      {
        roads: [{ vertexes }],
        guides: guides.map((g, i) => ({
          name: g.name || '',
          x: g.lng,
          y: g.lat,
          distance: Math.max(0, (guides[i + 1]?.at ?? r.distance) - g.at),
          guidance: g.text,
        })),
      },
    ],
  }
}
export async function navigationRequest<T>(
  path: string,
  params: Record<string, string>,
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetch(`/api/navigation/${path}?${new URLSearchParams(params)}`, {
    signal,
    cache: 'no-store',
  })
  const body = await response.json().catch(() => null)
  if (!response.ok || !body?.success)
    throw Error(
      body?.message || 'API 연결을 확인해 주세요. 개발 서버를 다시 실행해야 할 수 있습니다.',
    )
  return body.data
}
export async function searchNavigationPlaces(q: string, signal?: AbortSignal): Promise<Place[]> {
  try {
    return await navigationRequest<Place[]>('places', { q }, signal)
  } catch (error) {
    if (signal?.aborted) throw error
    const sdk = await loadKakao()
    if (signal?.aborted) throw error
    return new Promise((resolve, reject) => {
      new sdk.maps.services.Places().keywordSearch(
        q,
        (rows: any[], status: any) => {
          if (signal?.aborted) {
            reject(new Error('요청이 취소되었습니다.'))
            return
          }
          if (status === sdk.maps.services.Status.OK && rows.length) {
            resolve(
              rows.map((row) => ({
                id: row.id,
                name: row.place_name,
                address: row.road_address_name || row.address_name,
                lat: Number(row.y),
                lng: Number(row.x),
              })),
            )
            return
          }
          new sdk.maps.services.Geocoder().addressSearch(
            q,
            (addresses: any[], addressStatus: any) => {
              if (signal?.aborted) {
                reject(new Error('요청이 취소되었습니다.'))
                return
              }
              if (addressStatus === sdk.maps.services.Status.OK)
                resolve(
                  addresses.map((row) => ({
                    name: row.address_name,
                    address: row.address_name,
                    lat: Number(row.y),
                    lng: Number(row.x),
                  })),
                )
              else reject(error)
            },
          )
        },
        { location: new sdk.maps.LatLng(35.1796, 129.0756), size: 8 },
      )
    })
  }
}
export function routePoints(route: Route) {
  return route.sections.flatMap((section) =>
    section.roads.flatMap((road) => {
      const points: Point[] = []
      for (let i = 0; i + 1 < road.vertexes.length; i += 2)
        points.push({ lng: road.vertexes[i]!, lat: road.vertexes[i + 1]! })
      return points
    }),
  )
}
export function passesAvoidPoint(route: Route, avoid: Point, radius = 150) {
  const points = routePoints(route),
    sx = 111320 * Math.cos((avoid.lat * Math.PI) / 180),
    sy = 111320
  for (let i = 0; i < points.length; i++) {
    const a = points[i]!,
      b = points[i + 1] || a,
      ax = (a.lng - avoid.lng) * sx,
      ay = (a.lat - avoid.lat) * sy,
      dx = (b.lng - a.lng) * sx,
      dy = (b.lat - a.lat) * sy,
      t = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (dx * dx + dy * dy || 1)))
    if (Math.hypot(ax + t * dx, ay + t * dy) < radius) return true
  }
  return false
}
import { loadKakao } from './kakao'

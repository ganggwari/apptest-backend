import type { Point } from '../services/navigation'
export function distanceMeters(a: Point, b: Point) {
  const rad = Math.PI / 180,
    lat = ((a.lat + b.lat) / 2) * rad
  return Math.hypot((a.lng - b.lng) * 111320 * Math.cos(lat), (a.lat - b.lat) * 111320)
}
export function projectRoute(points: Point[], position: Point) {
  let total = 0,
    progress = 0,
    distance = Infinity
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]!,
      b = points[i]!,
      sx = 111320 * Math.cos((position.lat * Math.PI) / 180),
      sy = 111320
    const ax = (a.lng - position.lng) * sx,
      ay = (a.lat - position.lat) * sy,
      dx = (b.lng - a.lng) * sx,
      dy = (b.lat - a.lat) * sy
    const t = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (dx * dx + dy * dy || 1))),
      length = Math.hypot(dx, dy),
      gap = Math.hypot(ax + t * dx, ay + t * dy)
    if (gap < distance) {
      distance = gap
      progress = total + t * length
    }
    total += length
  }
  return { total, progress, distance, remaining: Math.max(0, total - progress) }
}
export function formatDistance(meters: number) {
  return meters >= 1000
    ? `${(meters / 1000).toFixed(1)} km`
    : `${Math.max(0, Math.round(meters / 10) * 10)} m`
}

// 경로의 누적 거리로 위치를 보간합니다. 중복 좌표는 건너뜁니다.
export function routeTimeline(points: Point[]) {
  const cumulative = [0]
  for (let i = 1; i < points.length; i++)
    cumulative.push(cumulative[i - 1]! + distanceMeters(points[i - 1]!, points[i]!))
  const total = cumulative[cumulative.length - 1] || 0
  return {
    total,
    positionAt(meters: number): Point | null {
      if (!points.length) return null
      const target = Math.max(0, Math.min(total, meters))
      for (let i = 1; i < points.length; i++) {
        const length = cumulative[i]! - cumulative[i - 1]!
        if (length <= 0 || cumulative[i]! < target) continue
        const t = (target - cumulative[i - 1]!) / length
        return {
          lat: points[i - 1]!.lat + (points[i]!.lat - points[i - 1]!.lat) * t,
          lng: points[i - 1]!.lng + (points[i]!.lng - points[i - 1]!.lng) * t,
        }
      }
      return { ...points[points.length - 1]! }
    },
  }
}

// 경로를 따라 meters 만큼 간 지점과 진행 방향(도, 0=북). 가상 주행(시뮬레이션)에서 쓴다.
export function pointAlong(points: Point[], meters: number) {
  if (!points.length) return { lat: 0, lng: 0, heading: 0 }
  let left = Math.max(0, meters)
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]!,
      b = points[i]!,
      len = distanceMeters(a, b),
      heading = (Math.atan2((b.lng - a.lng) * Math.cos((a.lat * Math.PI) / 180), b.lat - a.lat) * 180) / Math.PI
    if (left <= len || i === points.length - 1) {
      const t = len > 0 ? Math.min(1, left / len) : 0
      return { lat: a.lat + (b.lat - a.lat) * t, lng: a.lng + (b.lng - a.lng) * t, heading: (heading + 360) % 360 }
    }
    left -= len
  }
  const last = points[points.length - 1]!
  return { lat: last.lat, lng: last.lng, heading: 0 }
}

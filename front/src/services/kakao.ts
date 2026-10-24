declare global {
  interface Window {
    kakao: any
  }
}
import { installOsmKakao } from './osmKakao'
let loading: Promise<any> | undefined
let keyPromise: Promise<string> | undefined
// 카카오 JS 키: 백엔드 /api/navigation/config 우선, 없으면 빌드 시 VITE_KAKAO_MAP_KEY.
export function getKakaoKey(): Promise<string> {
  if (keyPromise) return keyPromise
  const fallback = (import.meta.env.VITE_KAKAO_MAP_KEY as string) || ''
  keyPromise = fetch('/api/navigation/config', { credentials: 'include' })
    .then((r) => (r.ok ? r.json() : null))
    .then((j) => (j?.data?.kakaoJsKey as string) || fallback)
    .catch(() => fallback)
    .then((key) => {
      if (!key) keyPromise = undefined
      return key
    })
  return keyPromise
}
async function loadKakaoSdk(): Promise<any> {
  if (window.kakao?.maps?.Map) return Promise.resolve(window.kakao)
  if (loading) return loading
  const key = await getKakaoKey()
  if (!key)
    return Promise.reject(
      new Error('카카오 지도 키가 없습니다. .env에 서버 SECRET_KAKAO_JS_KEY 또는 VITE_KAKAO_MAP_KEY를 설정하세요.'),
    )
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    let settled = false
    const fail = (message: string) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      loading = undefined
      script.remove()
      reject(new Error(message))
    }
    const timer = setTimeout(
      () => fail('지도를 불러오는 데 시간이 걸립니다. 네트워크를 확인하고 다시 시도해 주세요.'),
      8000,
    )
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false&libraries=services,clusterer`
    script.onload = () => {
      if (settled) return
      if (!window.kakao?.maps) {
        fail('카카오 SDK를 사용할 수 없습니다. 키와 허용 도메인을 확인하세요.')
        return
      }
      window.kakao.maps.load(() => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        resolve(window.kakao)
      })
    }
    script.onerror = () => fail('지도 SDK 로딩 실패. 네트워크와 도메인 설정을 확인하세요.')
    document.head.append(script)
  })
  return loading
}
// 지도 엔진 선택: ?map=osm / ?map=kakao (localStorage 'puf-map' 에 기억). 기본은 카카오, 실패하면 OSM 자동 대체.
export function mapPreference(): 'auto' | 'osm' | 'kakao' {
  try {
    const q = new URLSearchParams(location.search).get('map')
    if (q === 'osm' || q === 'kakao') localStorage.setItem('puf-map', q)
    if (q === 'auto') localStorage.removeItem('puf-map')
    const v = localStorage.getItem('puf-map')
    return v === 'osm' || v === 'kakao' ? v : 'auto'
  } catch {
    return 'auto'
  }
}
let engine: Promise<any> | undefined
export function loadKakao(): Promise<any> {
  if (engine) return engine
  const pref = mapPreference()
  engine = (async () => {
    if (pref === 'osm') return installOsmKakao()
    try {
      const k = await loadKakaoSdk()
      ;(window as any).__mapEngine = 'kakao'
      return k
    } catch (e) {
      if (pref === 'kakao') throw e
      console.warn('[map] 카카오 지도를 쓸 수 없어 OpenStreetMap 으로 대체합니다:', e)
      loading = undefined
      return installOsmKakao()
    }
  })()
  engine.catch(() => (engine = undefined))
  return engine
}
export async function geocode(address: string): Promise<{ lat: number; lng: number }> {
  const k = await loadKakao()
  return new Promise((resolve, reject) =>
    new k.maps.services.Geocoder().addressSearch(address, (result: any, status: any) => {
      if (status !== k.maps.services.Status.OK || !result.length)
        reject(new Error('주소를 찾지 못했습니다.'))
      else resolve({ lat: Number(result[0].y), lng: Number(result[0].x) })
    }),
  )
}

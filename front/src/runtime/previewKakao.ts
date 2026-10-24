import { pageFriendlyMapScroll } from './mapScroll'
import { getKakaoKey, loadKakao } from '../services/kakao'
import '../styles/map-weather.css'
import { useAdminProfile } from '../stores/adminProfile'

export async function mountPreviewKakao(
  root: HTMLElement,
  ctx: any,
  points: any[],
  selectGroup: (points: any[]) => void,
) {
  if (!(await getKakaoKey())) return
  let disposed = false,
    map: any,
    clusterer: any,
    userLocation: any,
    resize: ResizeObserver | undefined
  const listeners: { target: any; event: string; handler: (...args: any[]) => void }[] = []
  let sdk: any
  ctx.on(root, 'page-dispose', () => {
    disposed = true
    delete ctx.zoomCluster
    userLocation?.setMap(null)
    resize?.disconnect()
    clusterer?.clear()
    if (sdk) listeners.forEach((l) => sdk.maps.event.removeListener(l.target, l.event, l.handler))
  })
  const parent = root.querySelector<HTMLElement>('.mapw,.map-body,.map')
  if (!parent) return
  const status = document.createElement('p')
  status.className = 'preview-map-status'
  status.setAttribute('role', 'status')
  status.textContent = '카카오 지도를 불러오는 중입니다.'
  parent.append(status)
  try {
    sdk = await loadKakao()
    if (disposed) return
    const host = document.createElement('div')
    host.className = 'real-map-host'
    host.setAttribute('aria-label', '카카오 지도')
    parent.append(host)
    map = new sdk.maps.Map(host, { center: new sdk.maps.LatLng(35.1796, 129.0756), level: 8 })
    const disposeScroll = pageFriendlyMapScroll(host, map,
      /^\/(admin|customer)-app\//.test(window.location.pathname))
    ctx.on(root, 'page-dispose', disposeScroll)
    map.setCursor('grab')
    const controls = document.createElement('div')
    controls.className = 'kakao-map-controls'
    controls.setAttribute('aria-label', '지도 조작')
    const zoomIn = document.createElement('button'),
      zoomOut = document.createElement('button'),
      locate = document.createElement('button')
    zoomIn.type = zoomOut.type = locate.type = 'button'
    zoomIn.textContent = '+'
    zoomOut.textContent = '−'
    zoomIn.setAttribute('aria-label', '카카오 지도 확대')
    zoomOut.setAttribute('aria-label', '카카오 지도 축소')
    locate.setAttribute('aria-label', '내 위치 찾기')
    locate.innerHTML =
      '<svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2"/><path d="M12 1v4m0 14v4M1 12h4m14 0h4"/></svg>'
    controls.append(zoomIn, zoomOut, locate)
    parent.append(controls)
    ctx.on(zoomIn, 'click', () => map.setLevel(Math.max(1, map.getLevel() - 1)))
    ctx.on(zoomOut, 'click', () => map.setLevel(Math.min(14, map.getLevel() + 1)))
    ctx.on(locate, 'click', () => {
      if (!navigator.geolocation) {
        ctx.notify('이 브라우저에서는 위치 찾기를 지원하지 않습니다.')
        return
      }
      locate.disabled = true
      locate.setAttribute('aria-busy', 'true')
      navigator.geolocation.getCurrentPosition(
        (position) => {
          if (disposed) return
          locate.disabled = false
          locate.removeAttribute('aria-busy')
          const center = new sdk.maps.LatLng(position.coords.latitude, position.coords.longitude)
          if (!userLocation) {
            const dot = document.createElement('div')
            dot.className = 'map-user-location'
            dot.setAttribute('role', 'img')
            dot.setAttribute('aria-label', '내 현재 위치')
            userLocation = new sdk.maps.CustomOverlay({
              map,
              position: center,
              content: dot,
              zIndex: 4,
            })
          } else userLocation.setPosition(center)
          map.setLevel(4)
          map.panTo(center)
        },
        (error) => {
          if (disposed) return
          locate.disabled = false
          locate.removeAttribute('aria-busy')
          ctx.notify(
            error.code === 1
              ? '위치 권한을 허용한 뒤 다시 눌러 주세요.'
              : '현재 위치를 확인하지 못했습니다. 다시 시도해 주세요.',
          )
        },
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
      )
    })
    parent.querySelector('svg')?.setAttribute('visibility', 'hidden')
    const toolbar = parent.querySelector<HTMLElement>('.map-zoom-controls')
    if (toolbar) toolbar.hidden = true
    const colors: Record<string, string> = {
      '통제': '#8b1e2d',
      '위험': '#c62828',
      '주의': '#e88700',
      '안전': '#16805d',
      '미확인': '#64748b',
    }
    const markerImage = (risk: string) => {
      const color = colors[risk] || colors['미확인']
      const label =
        risk === '통제'
          ? '×'
          : risk === '위험'
            ? '!'
            : risk === '주의'
              ? '!'
              : risk === '안전'
                ? '✓'
                : '?'
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="40" height="50" viewBox="0 0 40 50"><path d="M20 48C16 40 2 30 2 20a18 18 0 1 1 36 0c0 10-14 20-18 28Z" fill="${color}" stroke="white" stroke-width="2"/><text x="20" y="27" text-anchor="middle" fill="white" font-family="sans-serif" font-size="23" font-weight="bold">${label}</text></svg>`
      return new sdk.maps.MarkerImage(
        'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg),
        new sdk.maps.Size(40, 50),
        { offset: new sdk.maps.Point(20, 48) },
      )
    }
    const clusterStyles = ['#64748b', '#16805d', '#e88700', '#c62828', '#8b1e2d'].map(
      (background) => ({
        width: '44px',
        height: '44px',
        background,
        color: '#fff',
        border: '3px solid white',
        borderRadius: '50%',
        textAlign: 'center',
        lineHeight: '38px',
        fontWeight: '700',
      }),
    )
    clusterer = new sdk.maps.MarkerClusterer({
      map,
      averageCenter: true,
      minLevel: 6,
      gridSize: 70,
      disableClickZoom: true,
      styles: clusterStyles,
    })
    const markerPoints = new Map<any, any>(),
      positions = new Map<number, any>()
    const filterMarkers = () => {
      const selected = ctx.selectedIndexes?.() as number[] | undefined
      clusterer.clear()
      clusterer.addMarkers(
        [...markerPoints.keys()].filter((marker) => {
          const point = markerPoints.get(marker)
          return (
            (!selected || selected.includes(point.index)) &&
            !root.querySelector<HTMLElement>(`.it[data-i="${point.index}"]`)?.parentElement?.hidden
          )
        }),
      )
    }
    ctx.on(root, 'facility-filter', filterMarkers)
    ctx.on(root, 'facility-focus', (event: Event) => {
      const index = (event as CustomEvent).detail.index,
        position = positions.get(index),
        point = points.find((p) => p.index === index)
      if (position) {
        map.setLevel(4)
        map.panTo(position)
        root.dispatchEvent(
          new CustomEvent('map-weather-location', {
            detail: {
              lat: position.getLat(),
              lng: position.getLng(),
              name: point?.name || '부산',
              weather: point?.weather,
              rainfall: point?.rainfall,
            },
          }),
        )
      }
    })
    ctx.on(root, 'input', () => ctx.setTimeout(filterMarkers, 0))
    ctx.on(root, 'click', (e: Event) => {
      if ((e.target as Element).closest('#seg')) ctx.setTimeout(filterMarkers, 0)
    })
    const on = (target: any, event: string, handler: (...args: any[]) => void) => {
      sdk.maps.event.addListener(target, event, handler)
      listeners.push({ target, event, handler })
    }
    const closeClusterList = () => {
      const panel = parent.querySelector<HTMLElement>('.cluster-list-panel')
      if (panel) panel.hidden = true
    }
    on(map, 'dragstart', () => {
      map.setCursor('grabbing')
      closeClusterList()
    })
    on(map, 'dragend', () => map.setCursor('grab'))
    on(map, 'zoom_changed', closeClusterList)
    on(map, 'click', closeClusterList)
    ctx.zoomCluster = (group: any[]) => {
      closeClusterList()
      const locations = group.map((point) => positions.get(point.index)).filter(Boolean)
      if (!locations.length) return
      const bounds = new sdk.maps.LatLngBounds()
      locations.forEach((position) => bounds.extend(position))
      map.setBounds(bounds, 64, 64, 64, 64)
      if (map.getLevel() >= 6) map.setLevel(5)
    }
    on(clusterer, 'clusterclick', (cluster: any) =>
      selectGroup(
        cluster
          .getMarkers()
          .map((m: any) => markerPoints.get(m))
          .filter(Boolean),
      ),
    )
    on(clusterer, 'clustered', (clusters: any[]) =>
      clusters.forEach((cluster) => {
        const risks = cluster.getMarkers().map((m: any) => markerPoints.get(m)?.risk)
        const level = cluster.getMarkers().some((m: any) => markerPoints.get(m)?.controlled)
          ? 4
          : risks.includes('위험')
            ? 3
            : risks.includes('주의')
              ? 2
              : risks.every((r: string) => r === '안전')
                ? 1
                : 0
        const content = cluster.getClusterMarker().getContent()
        if (content instanceof HTMLElement) Object.assign(content.style, clusterStyles[level])
      }),
    )
    resize = new ResizeObserver(() => {
      const center = map.getCenter()
      map.relayout()
      map.setCenter(center)
      clusterer.redraw()
    })
    resize.observe(host)
    ctx.on(root, 'click', (event: Event) => {
      const target = (event.target as Element).closest<HTMLElement>('.it,.mk')
      if (!target) return
      const index = Number(target.dataset.i),
        position = positions.get(index),
        point = points.find((p) => p.index === index)
      if (position) {
        map.setLevel(4)
        map.panTo(position)
        root.dispatchEvent(
          new CustomEvent('map-weather-location', {
            detail: {
              lat: position.getLat(),
              lng: position.getLng(),
              name: point?.name || '부산',
              weather: point?.weather,
              rainfall: point?.rainfall,
            },
          }),
        )
      }
    })
    let coords: any[] = []
    try {
      const response = await fetch('/facility-coordinates.json')
      if (response.ok) {
        const data = await response.json()
        if (Array.isArray(data)) coords = data
      }
    } catch {
      /* Resolve missing coordinates with Places below. */
    }
    if (disposed) return
    const places = new sdk.maps.services.Places()
    const find = (point: any): Promise<any> =>
      new Promise((resolve) =>
        places.keywordSearch(`부산 ${point.name}`, (rows: any[], result: any) => {
          const row =
            result === sdk.maps.services.Status.OK
              ? rows.find(
                  (r) =>
                    String(r.place_name)
                      .replace(/\s/g, '')
                      .includes(point.name.replace(/\s/g, '')) &&
                    String(r.address_name).startsWith('부산'),
                )
              : null
          resolve(row ? { lat: Number(row.y), lng: Number(row.x) } : null)
        }),
      )
    let missing = 0
    for (const point of points) {
      if (disposed) return
      const coord = coords.find((c) => c.name === point.name) || (await find(point))
      if (disposed) return
      if (!coord || !Number.isFinite(Number(coord.lat)) || !Number.isFinite(Number(coord.lng))) {
        missing++
        continue
      }
      const position = new sdk.maps.LatLng(Number(coord.lat), Number(coord.lng))
      positions.set(point.index, position)
      const marker = new sdk.maps.Marker({
        position,
        title: point.name + ' · ' + point.risk,
        image: markerImage(point.controlled ? '통제' : point.risk),
      })
      markerPoints.set(marker, point)
      on(marker, 'click', () => {
        point.original.dispatchEvent(new MouseEvent('click', { bubbles: true }))
        map.panTo(position)
      })
      clusterer.addMarker(marker)
    }
    filterMarkers()
    if (positions.size) {
      const manager =
        !!root.querySelector('.page-admin-dashboard,.admin-home-logo') ||
        root.classList.contains('page-admin-dashboard')
      const regions = ctx.store.account?.regions?.map((r: any) => r.name) || [
        useAdminProfile().profile.district,
      ]
      const scoped = manager
        ? points
            .filter((p) =>
              regions.some((region: string) => {
                const address = coords.find((c) => c.name === p.name)?.address || ''
                return (
                  address.includes(region) || address.includes(region.split(' ').at(-1) || region)
                )
              }),
            )
            .map((p) => positions.get(p.index))
            .filter(Boolean)
        : []
      const bounds = new sdk.maps.LatLngBounds()
      ;(scoped.length ? scoped : [...positions.values()]).forEach((p) => bounds.extend(p))
      map.setBounds(bounds, 40, 40, 40, 40)
      if (scoped.length === 1) map.setLevel(4)
    }
    status.textContent = missing
      ? `위치를 확인하지 못한 시설 ${missing}곳은 오른쪽 목록에서 확인해 주세요.`
      : ''
    status.hidden = !missing
    const provenance = parent.querySelector<HTMLElement>('.data-provenance')
    if (provenance)
      provenance.textContent = '시설명: 부산 공개 자료 · 상태·수치: 구성 예시 · 지도: 카카오맵'
  } catch (error) {
    if (!disposed) {
      status.textContent =
        error instanceof Error
          ? error.message
          : '카카오 지도를 불러오지 못했습니다. 키와 허용 도메인을 확인해 주세요.'
      status.setAttribute('role', 'alert')
    }
  }
}

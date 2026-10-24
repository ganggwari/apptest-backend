// 카카오 지도 SDK가 안 될 때(키·도메인·네트워크 문제) 대신 쓰는 OpenStreetMap(Leaflet) 호환 계층.
// 앱 코드가 쓰는 kakao.maps API 일부(Map/Marker/CustomOverlay/InfoWindow/Polyline/Circle/MarkerClusterer/
// event/services)를 흉내내서 window.kakao 로 노출한다. 지도 타일은 openstreetmap.org, 주소·장소 검색은 Nominatim.
// 레벨 변환: Kakao level L  <->  Leaflet zoom = 20 - L
/* eslint-disable @typescript-eslint/no-explicit-any */
const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const levelToZoom = (l: number) => 20 - l
const zoomToLevel = (z: number) => Math.max(1, Math.min(14, Math.round(20 - z)))

class Emitter {
  _h: Record<string, Set<(...a: any[]) => void>> = {}
  _on(n: string, f: (...a: any[]) => void) {
    ;(this._h[n] ||= new Set()).add(f)
  }
  _off(n: string, f: (...a: any[]) => void) {
    this._h[n]?.delete(f)
  }
  _emit(n: string, ...a: any[]) {
    this._h[n]?.forEach((f) => {
      try {
        f(...a)
      } catch (e) {
        console.error(e)
      }
    })
  }
}

let installing: Promise<any> | undefined
export function installOsmKakao(): Promise<any> {
  if (installing) return installing
  installing = (async () => {
    // @ts-ignore leaflet 은 번들에 포함(타입 선언 없음)
    const L: any = (await import('leaflet')).default ?? (await import('leaflet'))
    await import('leaflet/dist/leaflet.css')
    ensureStyle()

    class LatLng {
      constructor(public _lat: number, public _lng: number) {}
      getLat() { return this._lat }
      getLng() { return this._lng }
      equals(o: LatLng) { return o && o._lat === this._lat && o._lng === this._lng }
      toString() { return `(${this._lat}, ${this._lng})` }
    }
    const ll = (p: any): [number, number] => (p && p.getLat ? [p.getLat(), p.getLng()] : [p.lat, p.lng])
    const fromL = (p: any) => new LatLng(p.lat, p.lng)

    class LatLngBounds {
      _b: any = null
      extend(p: LatLng) {
        const a = ll(p)
        this._b ? this._b.extend(a) : (this._b = L.latLngBounds([a, a]))
      }
      contain(p: LatLng) { return !!this._b && this._b.contains(ll(p)) }
      isEmpty() { return !this._b }
      getSouthWest() { return fromL(this._b.getSouthWest()) }
      getNorthEast() { return fromL(this._b.getNorthEast()) }
    }
    class Point { constructor(public x: number, public y: number) {} }
    class Size { constructor(public width: number, public height: number) {} }
    class MarkerImage {
      constructor(public src: string, public size: Size, public options: any = {}) {}
    }
    const PIN =
      'data:image/svg+xml;charset=utf-8,' +
      encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="35" viewBox="0 0 24 35"><path d="M12 0C5.4 0 0 5.2 0 11.7 0 20.6 12 35 12 35s12-14.4 12-23.3C24 5.2 18.6 0 12 0z" fill="#1d6fdc" stroke="#fff" stroke-width="2"/><circle cx="12" cy="12" r="4.5" fill="#fff"/></svg>',
      )
    const iconOf = (img?: MarkerImage) => {
      if (!img) return L.icon({ iconUrl: PIN, iconSize: [24, 35], iconAnchor: [12, 35] })
      const w = img.size?.width ?? 24,
        h = img.size?.height ?? 35
      const o = img.options?.offset
      return L.icon({ iconUrl: img.src, iconSize: [w, h], iconAnchor: o ? [o.x, o.y] : [w / 2, h] })
    }

    // ---- Map ----
    class KMap extends Emitter {
      _m: any
      _el: HTMLElement
      constructor(el: HTMLElement, opt: any = {}) {
        super()
        this._el = el
        const c = ll(opt.center || new LatLng(35.1796, 129.0756))
        this._m = L.map(el, {
          center: c,
          zoom: levelToZoom(opt.level ?? 7),
          zoomControl: false,
          minZoom: 5,
          maxZoom: 19,
          scrollWheelZoom: opt.scrollwheel !== false,
          attributionControl: true,
          zoomAnimation: true,
        })
        L.tileLayer(TILE_URL, {
          maxZoom: 19,
          attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>',
        }).addTo(this._m)
        const m = this._m
        m.on('click', (e: any) => this._emit('click', { latLng: fromL(e.latlng) }))
        m.on('zoomstart', () => this._emit('zoomstart'))
        m.on('zoomend', () => {
          this._emit('zoom_changed')
          this._emit('bounds_changed')
        })
        m.on('dragstart', () => this._emit('dragstart'))
        m.on('dragend', () => this._emit('dragend'))
        m.on('movestart', () => undefined)
        m.on('move', () => this._emit('center_changed'))
        m.on('moveend', () => {
          this._emit('bounds_changed')
          this._emit('idle')
        })
        m.whenReady(() => setTimeout(() => this._emit('idle'), 0))
      }
      setCenter(p: LatLng) { this._m.setView(ll(p), this._m.getZoom(), { animate: false }) }
      getCenter() { return fromL(this._m.getCenter()) }
      setLevel(level: number, opt?: any) {
        const z = levelToZoom(Math.max(1, Math.min(14, level)))
        if (opt?.anchor) this._m.setZoomAround(ll(opt.anchor), z, { animate: false })
        else this._m.setZoom(z, { animate: false })
      }
      getLevel() { return zoomToLevel(this._m.getZoom()) }
      setBounds(b: LatLngBounds, t = 0, r = 0, bt = 0, l = 0) {
        if (!b || b.isEmpty()) return
        this._m.fitBounds(b._b, { paddingTopLeft: [l, t], paddingBottomRight: [r, bt], maxZoom: 16, animate: false })
      }
      panTo(p: LatLng) { this._m.panTo(ll(p), { animate: true }) }
      relayout() { this._m.invalidateSize({ animate: false }) }
      getBounds() {
        const b = new LatLngBounds()
        const lb = this._m.getBounds()
        b.extend(fromL(lb.getSouthWest()))
        b.extend(fromL(lb.getNorthEast()))
        return b
      }
      getProjection() {
        const m = this._m
        return {
          containerPointFromCoords: (p: LatLng) => {
            const pt = m.latLngToContainerPoint(ll(p))
            return { x: pt.x, y: pt.y }
          },
          coordsFromContainerPoint: (pt: any) => fromL(m.containerPointToLatLng([pt.x, pt.y])),
        }
      }
      setZoomable(on: boolean) { on ? this._m.scrollWheelZoom.enable() : this._m.scrollWheelZoom.disable() }
      setDraggable(on: boolean) { on ? this._m.dragging.enable() : this._m.dragging.disable() }
      setCursor(c: string) { this._el.style.cursor = c }
      setMinLevel() {}
      setMaxLevel() {}
      addControl(_c: any, pos?: string) {
        L.control.zoom({ position: pos || 'bottomright' }).addTo(this._m)
      }
    }
    const POS: Record<string, string> = {
      TOP: 'topleft', TOPLEFT: 'topleft', TOPRIGHT: 'topright',
      BOTTOM: 'bottomleft', BOTTOMLEFT: 'bottomleft', BOTTOMRIGHT: 'bottomright', LEFT: 'topleft', RIGHT: 'topright',
    }
    const ControlPosition: Record<string, string> = {}
    Object.keys(POS).forEach((k) => (ControlPosition[k] = POS[k]!))
    class ZoomControl {}

    // ---- 공통: map 에 붙였다 떼는 객체 ----
    class Attachable extends Emitter {
      _map: KMap | null = null
      _layer: any = null
      _make(): any { return null }
      setMap(m: KMap | null) {
        if (this._layer && this._map) this._map._m.removeLayer(this._layer)
        this._map = m
        if (m) {
          this._layer ||= this._make()
          if (this._layer) this._layer.addTo(m._m)
        }
      }
      getMap() { return this._map }
    }

    class Marker extends Attachable {
      _pos: LatLng
      _opt: any
      _img?: MarkerImage
      _z = 0
      _clustered = false
      constructor(opt: any = {}) {
        super()
        this._opt = opt
        this._pos = opt.position
        this._img = opt.image
        this._z = opt.zIndex ?? 0
        if (opt.map) this.setMap(opt.map)
      }
      _make() {
        const m = L.marker(ll(this._pos), {
          icon: iconOf(this._img),
          title: this._opt.title,
          zIndexOffset: this._z * 1000,
          draggable: !!this._opt.draggable,
          keyboard: false,
        })
        m.on('click', () => this._emit('click'))
        m.on('dragend', () => {
          const p = m.getLatLng()
          this._pos = fromL(p)
          this._emit('dragend', { latLng: this._pos })
        })
        return m
      }
      getPosition() { return this._pos }
      setPosition(p: LatLng) { this._pos = p; this._layer?.setLatLng(ll(p)) }
      setImage(i: MarkerImage) { this._img = i; this._layer?.setIcon(iconOf(i)) }
      setZIndex(z: number) { this._z = z; this._layer?.setZIndexOffset(z * 1000) }
      setTitle(t: string) { this._opt.title = t }
      setDraggable(d: boolean) { this._opt.draggable = d }
      getDraggable() { return !!this._opt.draggable }
    }

    class CustomOverlay extends Attachable {
      _pos: LatLng
      _content: HTMLElement
      _opt: any
      constructor(opt: any = {}) {
        super()
        this._opt = opt
        this._pos = opt.position
        this._content = toEl(opt.content)
        if (opt.map) this.setMap(opt.map)
      }
      _make() {
        const wrap = document.createElement('div')
        wrap.className = 'kakao-co-wrap'
        const ax = this._opt.xAnchor ?? 0.5,
          ay = this._opt.yAnchor ?? 0.5
        wrap.style.cssText = `position:absolute;left:0;top:0;transform:translate(${-ax * 100}%,${-ay * 100}%);white-space:nowrap`
        wrap.appendChild(this._content)
        L.DomEvent.disableClickPropagation(wrap)
        L.DomEvent.disableScrollPropagation(wrap)
        const icon = L.divIcon({ className: 'kakao-co', html: wrap, iconSize: [0, 0], iconAnchor: [0, 0] })
        return L.marker(ll(this._pos), { icon, zIndexOffset: (this._opt.zIndex ?? 0) * 1000, interactive: true, keyboard: false })
      }
      getContent() { return this._content }
      setContent(c: any) {
        this._content = toEl(c)
        const wrap = this._layer?.getElement()?.querySelector('.kakao-co-wrap')
        if (wrap) wrap.replaceChildren(this._content)
      }
      getPosition() { return this._pos }
      setPosition(p: LatLng) { this._pos = p; this._layer?.setLatLng(ll(p)) }
      setZIndex(z: number) { this._opt.zIndex = z; this._layer?.setZIndexOffset(z * 1000) }
      setVisible(v: boolean) {
        const el = this._layer?.getElement()
        if (el) el.style.display = v ? '' : 'none'
      }
    }

    class InfoWindow extends Emitter {
      _opt: any
      _content: any
      _pos?: LatLng
      _p: any = null
      _map: KMap | null = null
      constructor(opt: any = {}) {
        super()
        this._opt = opt
        this._content = opt.content
        this._pos = opt.position
      }
      _build(marker?: any) {
        const h = marker?._img?.size?.height ?? (marker ? 35 : 0)
        const el = toEl(this._content)
        return L.popup({
          closeButton: this._opt.removable !== false,
          autoPan: !this._opt.disableAutoPan,
          offset: [0, -h],
          className: 'kakao-iw',
          maxWidth: 360,
          minWidth: 0,
          closeOnClick: false,
          autoClose: false,
        }).setContent(el)
      }
      open(map: KMap, marker?: any) {
        this.close()
        this._map = map
        const pos = marker ? marker.getPosition() : this._pos
        if (!pos) return
        this._p = this._build(marker).setLatLng(ll(pos))
        this._p.openOn(map._m)
        this._p.on('remove', () => this._emit('close'))
      }
      close() {
        if (this._p && this._map) this._map._m.closePopup(this._p)
        this._p = null
      }
      setContent(c: any) { this._content = c; this._p?.setContent(toEl(c)) }
      getContent() { return this._content }
      setPosition(p: LatLng) { this._pos = p; this._p?.setLatLng(ll(p)) }
      setZIndex() {}
    }

    class Polyline extends Attachable {
      constructor(public _o: any = {}) { super(); if (_o.map) this.setMap(_o.map) }
      _make() {
        const o = this._o
        return L.polyline((o.path || []).map(ll), {
          weight: o.strokeWeight ?? 4,
          color: o.strokeColor ?? '#1d6fdc',
          opacity: o.strokeOpacity ?? 0.9,
          dashArray: o.strokeStyle && o.strokeStyle !== 'solid' ? '6 6' : undefined,
        })
      }
      setPath(p: LatLng[]) { this._o.path = p; this._layer?.setLatLngs(p.map(ll)) }
      getPath() { return this._o.path }
    }
    class Circle extends Attachable {
      constructor(public _o: any = {}) { super(); if (_o.map) this.setMap(_o.map) }
      _make() {
        const o = this._o
        return L.circle(ll(o.center), {
          radius: o.radius ?? 100,
          weight: o.strokeWeight ?? 2,
          color: o.strokeColor ?? '#e2692b',
          opacity: o.strokeOpacity ?? 0.9,
          fillColor: o.fillColor ?? '#e2692b',
          fillOpacity: o.fillOpacity ?? 0.2,
          dashArray: o.strokeStyle && o.strokeStyle !== 'solid' ? '6 6' : undefined,
        })
      }
      setPosition(p: LatLng) { this._o.center = p; this._layer?.setLatLng(ll(p)) }
      setRadius(r: number) { this._o.radius = r; this._layer?.setRadius(r) }
    }

    // ---- MarkerClusterer: 화면 픽셀 격자 기반 단순 구현 ----
    class Cluster {
      _el: HTMLElement
      _marker: CustomOverlay
      constructor(public _members: Marker[], public _center: LatLng, el: HTMLElement) {
        this._el = el
        this._marker = new CustomOverlay({ position: _center, content: el, zIndex: 5 })
      }
      getMarkers() { return this._members }
      getCenter() { return this._center }
      getClusterMarker() { return this._marker }
    }
    class MarkerClusterer extends Emitter {
      _map: KMap | null
      _markers = new Set<Marker>()
      _clusters: Cluster[] = []
      _o: any
      _t: any
      _busy = false
      constructor(opt: any = {}) {
        super()
        this._o = opt
        this._map = opt.map || null
        if (this._map) {
          const re = () => this.redraw()
          this._map._on('idle', re)
          this._map._on('zoom_changed', re)
        }
      }
      addMarker(m: Marker, noRedraw?: boolean) {
        m._clustered = true
        this._markers.add(m)
        if (m._map) m.setMap(null)
        if (!noRedraw) this._schedule()
      }
      addMarkers(ms: Marker[]) {
        ms.forEach((m) => this.addMarker(m, true))
        this._schedule()
      }
      removeMarker(m: Marker) { this._markers.delete(m); m.setMap(null); this._schedule() }
      clear() {
        this._markers.forEach((m) => m.setMap(null))
        this._markers.clear()
        this._dropClusters()
      }
      _dropClusters() {
        this._clusters.forEach((c) => c._marker.setMap(null))
        this._clusters = []
      }
      _schedule() {
        clearTimeout(this._t)
        this._t = setTimeout(() => this.redraw(), 0)
      }
      redraw() {
        const map = this._map
        if (!map || this._busy) return
        this._busy = true
        try {
          this._dropClusters()
          const level = map.getLevel(),
            grid = this._o.gridSize ?? 60,
            minLevel = this._o.minLevel ?? 0,
            m = map._m
          const cells = new Map<string, Marker[]>()
          const clusterOn = level >= minLevel
          this._markers.forEach((mk) => {
            if (!clusterOn) return
            const p = m.latLngToContainerPoint(ll(mk.getPosition()))
            const key = `${Math.floor(p.x / grid)}:${Math.floor(p.y / grid)}`
            ;(cells.get(key) || cells.set(key, []).get(key)!).push(mk)
          })
          const clustered = new Set<Marker>()
          const made: Cluster[] = []
          cells.forEach((list) => {
            if (list.length < 2) return
            let lat = 0, lng = 0
            list.forEach((mk) => {
              const p = mk.getPosition()
              lat += p.getLat(); lng += p.getLng()
            })
            const center = new LatLng(lat / list.length, lng / list.length)
            const el = document.createElement('div')
            const st = (this._o.styles && this._o.styles[0]) || {}
            Object.assign(el.style, { width: '40px', height: '40px', borderRadius: '50%', background: '#1d6fdc', color: '#fff', textAlign: 'center', lineHeight: '40px', fontWeight: '700' }, st)
            el.textContent = String(list.length)
            const c = new Cluster(list, center, el)
            el.addEventListener('click', (ev) => {
              ev.stopPropagation()
              this._emit('clusterclick', c)
              if (!this._o.disableClickZoom) map.setLevel(Math.max(1, level - 1), { anchor: center })
            })
            list.forEach((mk) => clustered.add(mk))
            made.push(c)
          })
          this._markers.forEach((mk) => {
            const hide = clustered.has(mk)
            if (hide && mk._map) mk.setMap(null)
            else if (!hide && !mk._map) mk.setMap(map)
          })
          made.forEach((c) => c._marker.setMap(map))
          this._clusters = made
          this._emit('clustered', made)
        } finally {
          this._busy = false
        }
      }
      setMap(m: KMap | null) { this._map = m; m ? this.redraw() : this.clear() }
    }

    // ---- 이벤트 ----
    const event = {
      addListener(t: any, n: string, f: (...a: any[]) => void) { t?._on?.(n, f) },
      removeListener(t: any, n: string, f: (...a: any[]) => void) { t?._off?.(n, f) },
      trigger(t: any, n: string, ...a: any[]) { t?._emit?.(n, ...a) },
      preventMap() {},
    }

    // ---- 검색(Nominatim) ----
    let last = 0
    let chain: Promise<any> = Promise.resolve()
    const nominatim = (path: string, params: Record<string, string>): Promise<any> => {
      chain = chain.then(async () => {
        const wait = last + 1100 - Date.now()
        if (wait > 0) await new Promise((r) => setTimeout(r, wait))
        last = Date.now()
        const q = new URLSearchParams({ format: 'jsonv2', addressdetails: '1', 'accept-language': 'ko', ...params })
        const r = await fetch(`https://nominatim.openstreetmap.org/${path}?${q}`)
        if (!r.ok) throw new Error(String(r.status))
        return r.json()
      })
      return chain.catch(() => null)
    }
    const Status = { OK: 'OK', ZERO_RESULT: 'ZERO_RESULT', ERROR: 'ERROR' }
    const toRow = (r: any) => ({
      id: String(r.place_id),
      place_name: r.name || r.display_name?.split(',')[0] || '',
      address_name: r.display_name || '',
      road_address_name: '',
      x: String(r.lon),
      y: String(r.lat),
    })
    class Geocoder {
      addressSearch(q: string, cb: (r: any[], s: string) => void) {
        void nominatim('search', { q, countrycodes: 'kr', limit: '5' }).then((rows) =>
          rows && rows.length ? cb(rows.map(toRow), Status.OK) : cb([], rows ? Status.ZERO_RESULT : Status.ERROR),
        )
      }
      coord2RegionCode(lng: number, lat: number, cb: (r: any[], s: string) => void) {
        void nominatim('reverse', { lat: String(lat), lon: String(lng), zoom: '10' }).then((r) => {
          if (!r || !r.address) return cb([], rows0(r))
          const a = r.address
          cb(
            [{
              region_type: 'H',
              region_1depth_name: a.state || a.province || a.city || '',
              region_2depth_name: a.city_district || a.county || a.city || a.town || '',
              region_3depth_name: a.suburb || a.quarter || a.neighbourhood || '',
            }],
            Status.OK,
          )
        })
      }
      coord2Address(lng: number, lat: number, cb: (r: any[], s: string) => void) {
        void nominatim('reverse', { lat: String(lat), lon: String(lng), zoom: '18' }).then((r) => {
          if (!r || !r.display_name) return cb([], rows0(r))
          cb([{ address: { address_name: r.display_name }, road_address: { address_name: r.display_name } }], Status.OK)
        })
      }
    }
    const rows0 = (r: any) => (r ? Status.ZERO_RESULT : Status.ERROR)
    class Places {
      keywordSearch(q: string, cb: (r: any[], s: string, p?: any) => void, opt: any = {}) {
        const params: Record<string, string> = { q, countrycodes: 'kr', limit: String(opt.size || 5) }
        if (opt.location) {
          const c = ll(opt.location)
          params.viewbox = `${c[1] - 0.5},${c[0] + 0.5},${c[1] + 0.5},${c[0] - 0.5}`
        }
        void nominatim('search', params).then((rows) =>
          rows && rows.length ? cb(rows.map(toRow), Status.OK) : cb([], rows ? Status.ZERO_RESULT : Status.ERROR),
        )
      }
    }

    const maps = {
      load: (cb: () => void) => cb(),
      LatLng, LatLngBounds, Point, Size, MarkerImage,
      Map: KMap, Marker, CustomOverlay, InfoWindow, Polyline, Circle, MarkerClusterer,
      ZoomControl, ControlPosition, event,
      services: { Status, Geocoder, Places },
    }
    ;(window as any).kakao = { ...(window as any).kakao, maps, osm: true }
    ;(window as any).__mapEngine = 'osm'
    return (window as any).kakao
  })()
  return installing
}

function toEl(c: any): HTMLElement {
  if (c instanceof HTMLElement) return c
  const d = document.createElement('div')
  d.innerHTML = String(c ?? '')
  return d
}
let styled = false
function ensureStyle() {
  if (styled) return
  styled = true
  const s = document.createElement('style')
  s.textContent = `
.leaflet-container{font:inherit;background:#e8eef3}
.kakao-co{background:transparent!important;border:0!important;overflow:visible!important}
.kakao-iw .leaflet-popup-content-wrapper{border-radius:10px;padding:0}
.kakao-iw .leaflet-popup-content{margin:8px 10px}
.leaflet-top,.leaflet-bottom{z-index:5}
`
  document.head.appendChild(s)
}

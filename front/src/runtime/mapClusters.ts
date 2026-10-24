import { mountPreviewKakao } from './previewKakao'
interface Point {
  index: number
  x: number
  y: number
  name: string
  risk: string
  controlled: boolean
  original: SVGElement
  weather?: string
  rainfall?: string
  lat?: number
  lng?: number
}
export function mountClusters(root: HTMLElement, ctx: any, name: string) {
  const svg = root.querySelector<SVGSVGElement>(
    name === 'admin-dashboard'
      ? '.map-body svg'
      : name === 'customer-app'
        ? '.map svg'
        : '.mapw svg',
  )
  if (!svg) return
  if (name === 'customer-app') svg.setAttribute('preserveAspectRatio', 'xMidYMid meet')
  const ns = 'http://www.w3.org/2000/svg'
  const markers = Array.from(svg.querySelectorAll<SVGElement>('.mk'))
  if (!markers.length) return
  const seeds = ctx.store.data[
    `${ctx.store.roleKey}:${name}:${name === 'admin-dashboard' ? 'FAC' : 'F'}`
  ] as any[] | undefined
  if (name === 'c-map')
    seeds?.forEach((seed, index) => {
      if (markers.some((m) => Number(m.dataset.i) === index)) return
      const el = document.createElementNS(ns, 'g')
      el.classList.add('mk')
      el.dataset.i = String(index)
      el.setAttribute('aria-label', seed[0])
      svg.append(el)
      markers.push(el)
    })
  const points: Point[] = markers.map((el, i) => {
    const index = Number(el.dataset.i ?? el.dataset.f ?? i),
      seed = seeds?.[index]
    const circle = el.tagName === 'circle' ? el : el.querySelector('circle')
    const x = Number(circle?.getAttribute('cx') || seed?.x || seed?.[6] || 0),
      y = Number(circle?.getAttribute('cy') || seed?.y || seed?.[7] || 0)
    return {
      index,
      x,
      y,
      name: seed?.name || seed?.[0] || `시설 ${i + 1}`,
      risk: seed?.lvt || seed?.[1] || '',
      controlled: seed?.ctl === 'BLOCKED' || seed?.[2] === '통제 중',
      original: el,
      weather: seed?.[4],
      rainfall: seed?.[3],
    }
  })
  markers.forEach((el) => el.setAttribute('visibility', 'hidden'))
  const originalView = svg.getAttribute('viewBox') || '0 0 800 460'
  const baseView = originalView.split(/\s+/).map(Number)
  const layer = document.createElementNS(ns, 'g')
  layer.setAttribute('class', 'cluster-layer')
  svg.append(layer)
  let scale = 1
  let selectedGroup: Point[] = []
  let disposed = false
  const panel = document.createElement('section')
  panel.className = 'cluster-list-panel'
  panel.hidden = true
  const parent = svg.closest<HTMLElement>('.mapw,.map-body,.map')!
  parent.append(panel)
  root.querySelectorAll<HTMLElement>('.legend,.app-map-legend').forEach((el) => (el.hidden = true))
  const legend = document.createElement('div')
  legend.className = 'map-status-legend'
  legend.setAttribute('aria-label', '지도 범례')
  ;[
    ['통제', '#8b1e2d'],
    ['위험', '#c62828'],
    ['주의', '#e88700'],
    ['안전', '#16805d'],
    ['내 위치', '#1671d9'],
  ].forEach(([label, color]) => {
    const item = document.createElement('span'),
      dot = document.createElement('i')
    dot.style.background = color!
    item.append(dot, document.createTextNode(label!))
    legend.append(item)
  })
  parent.append(legend)
  const toolbar = document.createElement('div')
  toolbar.className = 'map-zoom-controls'
  const minus = document.createElement('button')
  minus.textContent = '−'
  minus.setAttribute('aria-label', '지도 축소')
  const plus = document.createElement('button')
  plus.textContent = '+'
  plus.setAttribute('aria-label', '지도 확대')
  toolbar.append(plus, minus)
  parent.append(toolbar)
  function showList(group: Point[]) {
    if (ctx.onCluster) {
      ctx.onCluster(group)
      return
    }
    selectedGroup = group
    panel.replaceChildren()
    panel.hidden = false
    const title = document.createElement('h3')
    title.textContent = `묶음 시설 ${group.length}곳`
    const close = document.createElement('button')
    close.textContent = '목록 닫기'
    ctx.on(close, 'click', () => (panel.hidden = true))
    const expand = document.createElement('button')
    expand.textContent = '지도에서 확대'
    ctx.on(expand, 'click', () => {
      panel.hidden = true
      if (ctx.zoomCluster) {
        ctx.zoomCluster(group)
        return
      }
      scale = Math.min(4, scale * 1.7)
      draw()
    })
    panel.append(title, close, expand)
    group.forEach((p) => {
      const b = document.createElement('button')
      b.className = 'cluster-facility-row'
      b.textContent = `${p.name} · ${p.risk} · ${p.controlled ? '통제 중' : '정상 운영'}`
      ctx.on(b, 'click', () => {
        const scroll = panel.scrollTop
        p.original.dispatchEvent(new MouseEvent('click', { bubbles: true }))
        panel.replaceChildren()
        const h = document.createElement('h3')
        h.textContent = p.name
        const info = document.createElement('p')
        info.textContent = `${p.risk} · ${p.controlled ? '통제 중' : '정상 운영'}`
        const back = document.createElement('button')
        back.textContent = '묶음 목록으로'
        ctx.on(back, 'click', () => {
          showList(selectedGroup)
          panel.scrollTop = scroll
        })
        panel.append(h, info, back)
        const details = document.createElement('a')
        details.textContent = '시설 상세'
        details.href =
          name === 'admin-dashboard'
            ? '/fac-detail'
            : name === 'customer-app'
              ? '/customer-app/screen-6'
              : '/c-map'
        ctx.on(details, 'click', (e: Event) => {
          e.preventDefault()
          ctx.route(details.href)
        })
        panel.append(details)
      })
      panel.append(b)
    })
  }
  function draw() {
    const [bx, by, bw, bh] = baseView
    svg!.setAttribute(
      'viewBox',
      `${bx! + bw! / 2 - bw! / scale / 2} ${by! + bh! / 2 - bh! / scale / 2} ${bw! / scale} ${bh! / scale}`,
    )
    layer.replaceChildren()
    const groups: Point[][] = []
    const rect = svg!.getBoundingClientRect()
    const view = svg!.viewBox.baseVal
    const ratio = rect.width / (view.width || 800)
    const visible = points.filter((p) =>
      ctx.selectedIndexes
        ? ctx.selectedIndexes().includes(p.index)
        : !root.querySelector<HTMLElement>(`.it[data-i="${p.index}"]`)?.parentElement?.hidden,
    )
    if (selectedGroup.some((p) => !visible.includes(p))) {
      selectedGroup = []
      panel.hidden = true
    }
    visible.forEach((p) => {
      const group = groups.find(
        (g) => Math.hypot((g[0]!.x - p.x) * ratio, (g[0]!.y - p.y) * ratio) < 70,
      )
      if (group) group.push(p)
      else groups.push([p])
    })
    groups.forEach((group) => {
      const g = document.createElementNS(ns, 'g')
      const x = group.reduce((a, p) => a + p.x, 0) / group.length,
        y = group.reduce((a, p) => a + p.y, 0) / group.length
      g.setAttribute('transform', `translate(${x},${y})`)
      g.setAttribute('tabindex', '0')
      g.setAttribute('role', 'button')
      g.setAttribute(
        'aria-label',
        group.length > 1 ? `${group.length}개 시설 목록 열기` : group[0]!.name,
      )
      const circle = document.createElementNS(ns, 'circle')
      circle.setAttribute(
        'r',
        name === 'customer-app' ? (group.length > 1 ? '5' : '3') : group.length > 1 ? '22' : '14',
      )
      circle.setAttribute('class', 'cluster-circle')
      const risk = group.some((p) => p.risk === '위험')
        ? '위험'
        : group.some((p) => p.risk === '주의')
          ? '주의'
          : '안전'
      g.dataset.risk = risk
      g.dataset.controlled = String(group.some((p) => p.controlled))
      circle.style.fill = group.some((p) => p.controlled)
        ? '#8b1e2d'
        : risk === '위험'
          ? '#c62828'
          : risk === '주의'
            ? '#e88700'
            : '#16805d'
      g.append(circle)
      const t = document.createElementNS(ns, 'text')
      t.setAttribute('text-anchor', 'middle')
      t.setAttribute('y', '5')
      t.setAttribute('class', 'cluster-number')
      if (name === 'customer-app') {
        t.setAttribute('font-size', '4')
        t.setAttribute('y', '1.5')
      }
      t.textContent =
        group.length > 1
          ? String(group.length)
          : group[0]!.controlled
            ? '×'
            : group[0]!.risk === '위험'
              ? '!'
              : '•'
      g.append(t)
      const tooltip = document.createElementNS(ns, 'title')
      tooltip.textContent = `${group.length}곳 · 통제 ${group.filter((p) => p.controlled).length} · 위험 ${group.filter((p) => p.risk === '위험').length}`
      g.append(tooltip)
      const action = () =>
        group.length > 1
          ? showList(group)
          : group[0]!.original.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      ctx.on(g, 'click', action)
      ctx.on(g, 'keydown', (e: Event) => {
        if (['Enter', ' '].includes((e as KeyboardEvent).key)) {
          e.preventDefault()
          action()
        }
      })
      layer.append(g)
    })
  }
  ctx.on(minus, 'click', () => {
    scale = Math.max(0.2, scale / 1.5)
    draw()
  })
  ctx.on(plus, 'click', () => {
    scale = Math.min(4, scale * 1.5)
    draw()
  })
  ctx.on(root, 'facility-filter', draw)
  ctx.on(root, 'input', () => ctx.setTimeout(draw, 0))
  ctx.on(root, 'change', () => ctx.setTimeout(draw, 0))
  const observer = new ResizeObserver(draw)
  observer.observe(svg)
  draw()
  void mountPreviewKakao(root, ctx, points, showList)
  ctx.on(root, 'page-dispose', () => {
    disposed = true
    observer.disconnect()
  })
}

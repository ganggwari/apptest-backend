import { mountClusters } from './mapClusters'
import seeds from '../data/c-map.json'
export function mobileMap(root: HTMLElement, router: any, store: any) {
  const map = root.querySelector<HTMLElement>('.map')
  const svg = map?.querySelector<SVGSVGElement>('svg')
  if (!map || !svg) return () => {}
  map.classList.add('mobile-map-root')
  const ns = 'http://www.w3.org/2000/svg'
  const pins = Array.from(map.querySelectorAll<HTMLElement>('i:not(.me)'))
  pins.forEach((pin, i) => {
    const computed = getComputedStyle(pin),
      x = (parseFloat(computed.left) / map.clientWidth) * 100,
      y = (parseFloat(computed.top) / map.clientHeight) * 100
    const group = document.createElementNS(ns, 'g')
    group.setAttribute('class', 'mk')
    group.dataset.i = String(i)
    const circle = document.createElementNS(ns, 'circle')
    circle.setAttribute('cx', String(x))
    circle.setAttribute('cy', String(y))
    circle.setAttribute('r', '3')
    group.append(circle)
    group.addEventListener('click', () =>
      router.push(
        root.classList.contains('page-admin-app-3')
          ? '/admin-app/screen-5'
          : '/customer-app/screen-6',
      ),
    )
    svg.append(group)
    pin.hidden = true
  })
  const listeners: { target: EventTarget; event: string; fn: EventListener }[] = []
  const key = `${store.roleKey}:customer-app:F`
  store.data[key] = seeds.F
  const ctx = {
    store,
    on: (target: EventTarget, event: string, fn: EventListener) => {
      target.addEventListener(event, fn)
      listeners.push({ target, event, fn })
    },
    route: (path: string) => router.push(path === '/c-map' ? '/customer-app/screen-6' : path),
    notify: (message: string) => {
      let status = root.querySelector<HTMLElement>('.mobile-status')
      if (!status) {
        status = document.createElement('p')
        status.className = 'mobile-status'
        status.setAttribute('role', 'status')
        map.after(status)
      }
      status.textContent = message
    },
    setTimeout: (fn: () => void, delay: number) => window.setTimeout(fn, delay),
  }
  mountClusters(root, ctx, 'customer-app')
  return () => {
    root.dispatchEvent(new Event('page-dispose'))
    listeners.forEach((l) => l.target.removeEventListener(l.event, l.fn))
  }
}

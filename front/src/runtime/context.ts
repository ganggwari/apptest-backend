import type { Router } from 'vue-router'
import { useApp } from '../stores/app'
export function makeContext(root: HTMLElement, router: Router) {
  const store = useApp()
  const timers = new Set<number>(),
    intervals = new Set<number>()
  const listeners: {
    target: EventTarget
    event: string
    fn: EventListenerOrEventListenerObject
    options?: boolean | AddEventListenerOptions
  }[] = []
  const on = (
    target: EventTarget,
    event: string,
    fn: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions,
  ) => {
    target.addEventListener(event, fn, options)
    listeners.push({ target, event, fn, options })
  }
  const route = (href: string) => {
    const path = href.replace(/^(?:\.\/)?([\w-]+)\.html/, '/$1')
    if (path.startsWith('/')) router.push(path)
    else if (path.startsWith('#')) {
      root.querySelector(path)?.scrollIntoView({ block: 'start' })
      history.replaceState(null, '', path)
    }
  }
  const localWindow: any = {
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    print: () => window.print(),
    open: (url: string, target?: string) =>
      window.open(url.replace(/([\w-]+)\.html/, '/$1'), target, 'noopener,noreferrer'),
    addEventListener: (e: string, fn: EventListener) => on(window, e, fn),
    removeEventListener: (e: string, fn: EventListener) => window.removeEventListener(e, fn),
  }
  const doc: any = {
    getElementById: (id: string) => root.querySelector('#' + CSS.escape(id)),
    querySelector: (selector: string) => root.querySelector(selector),
    querySelectorAll: (selector: string) => root.querySelectorAll(selector),
    body: root,
    documentElement: root,
    createElement: (tag: string) => document.createElement(tag),
    addEventListener: (e: string, fn: EventListener) =>
      on(e === 'keydown' ? document : root, e, fn),
    removeEventListener: (e: string, fn: EventListener) => {
      root.removeEventListener(e, fn)
      document.removeEventListener(e, fn)
    },
  }
  const location = {
    get search() {
      return window.location.search
    },
    get hash() {
      return window.location.hash
    },
    get href() {
      return window.location.href
    },
    set href(v: string) {
      route(v)
    },
  }
  localWindow.location = location
  const notify = (text: string) => {
    let el = root.querySelector<HTMLElement>('#toast')
    if (!el) {
      el = document.createElement('div')
      el.id = 'toast'
      el.className = 'runtime-toast'
      el.setAttribute('role', 'status')
      root.append(el)
    }
    el.textContent = text.replace(/시연(?:용)?|샘플|화면 확인용/g, '').trim()
    el.hidden = false
    const timer = window.setTimeout(() => {
      if (el) el.hidden = true
    }, 2500)
    timers.add(timer)
  }
  const ctx: any = {
    root,
    router,
    store,
    document: doc,
    window: localWindow,
    location,
    localStorage,
    sessionStorage,
    alert: notify,
    confirm: (text: string) => window.confirm(text),
    setTimeout: (fn: TimerHandler, ms?: number) => {
      const id = window.setTimeout(fn, ms)
      timers.add(id)
      return id
    },
    setInterval: (fn: TimerHandler, ms?: number) => {
      const id = window.setInterval(fn, ms)
      intervals.add(id)
      return id
    },
    clearTimeout: (id: number) => {
      clearTimeout(id)
      timers.delete(id)
    },
    clearInterval: (id: number) => {
      clearInterval(id)
      intervals.delete(id)
    },
    notify,
    on,
    route,
    closeAll: (except?: string) => localWindow.closeAll?.(except),
  }
  on(root, 'click', (e: Event) => {
    const a = (e.target as Element).closest<HTMLAnchorElement>('a[href]')
    if (!a) return
    const href = a.getAttribute('href') || ''
    if (a.target === '_blank') return
    if (href.includes('.html')) {
      e.preventDefault()
      route(href)
    } else if (href === '#') e.preventDefault()
  })
  let focusBefore: HTMLElement | null = null
  on(root, 'click', () => {
    const opened = root.querySelector<HTMLElement>('.drawer:not([hidden]),.side.open,dialog[open]')
    if (opened && !focusBefore) {
      focusBefore = document.activeElement as HTMLElement
      opened.querySelector<HTMLElement>('button,input,[tabindex="0"]')?.focus()
    } else if (!opened && focusBefore) {
      focusBefore.focus()
      focusBefore = null
    }
  })
  on(document, 'keydown', (e: Event) => {
    const key = e as KeyboardEvent
    const opened = root.querySelector<HTMLElement>('dialog[open],.drawer:not([hidden]),.side.open')
    if (!opened) return
    if (key.key === 'Escape') {
      root.querySelectorAll<HTMLDialogElement>('dialog[open]').forEach((d) => d.close())
      ctx.closeAll()
      focusBefore?.focus()
      focusBefore = null
    }
    if (key.key === 'Tab') {
      const all = Array.from(
        opened.querySelectorAll<HTMLElement>('button,a[href],input,select,textarea,[tabindex="0"]'),
      ).filter((el) => el.getClientRects().length && !el.hasAttribute('disabled'))
      const first = all[0],
        last = all.at(-1)
      if (key.shiftKey && document.activeElement === first) {
        key.preventDefault()
        last?.focus()
      } else if (!key.shiftKey && document.activeElement === last) {
        key.preventDefault()
        first?.focus()
      }
    }
  })
  const mutation = new MutationObserver(() =>
    root.querySelectorAll<HTMLElement>('[data-layout]').forEach((el) => {
      el.classList.add(el.dataset.layout!)
      el.removeAttribute('data-layout')
    }),
  )
  mutation.observe(root, { childList: true, subtree: true })
  function dispose() {
    mutation.disconnect()
    root.dispatchEvent(new Event('page-dispose'))
    timers.forEach(clearTimeout)
    intervals.forEach(clearInterval)
    listeners.forEach((l) => l.target.removeEventListener(l.event, l.fn, l.options))
    root.querySelectorAll<HTMLDialogElement>('dialog').forEach((d) => d.close())
  }
  return { ctx, dispose }
}

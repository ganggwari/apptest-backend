import { uiIconSvg } from '../utils/ui-icons'
import { mobileMap } from './mobileMap'
import { watch } from 'vue'
import { useNotificationGlow } from '../stores/notificationGlow'
import { loginCustomer, logoutCustomer } from '../services/customerSession'
export function mobileInteractions(root: HTMLElement, path: string, router: any, store: any) {
  const glow = useNotificationGlow()
  const stopGlow = watch(() => glow.bell, (active) => {
    root.querySelectorAll('.app-bell').forEach((bell) => {
      bell.classList.toggle('notification-arrival-glow', active)
    })
  }, { immediate: true })
  const disposeMap = path.startsWith('/admin-app/') ? mobileMap(root, router, store) : () => {}
  const abort = new AbortController()
  const on = (target: EventTarget, event: string, fn: EventListener, options: any = {}) =>
    target.addEventListener(event, fn, { ...options, signal: abort.signal })
  function notify(text: string) {
    let p = root.querySelector<HTMLElement>('.mobile-status')
    if (!p) {
      p = document.createElement('p')
      p.className = 'mobile-status'
      p.setAttribute('role', 'status')
      root.querySelector('.bd')?.append(p)
    }
    p.textContent = text
  }
  root.querySelectorAll<HTMLAnchorElement>('.app-bell').forEach((a) => {
    a.innerHTML = uiIconSvg('bell')
    a.setAttribute('aria-label', '알림')
    const badge = document.createElement('span')
    badge.textContent = String(store.unread)
    badge.hidden = store.unread === 0
    a.append(badge)
  })
  if (path === '/customer-app/screen-1') {
    root.querySelectorAll('.inl-0d0267131b,.inl-946a990a1e').forEach((e) => e.remove())
    on(
      root,
      'click',
      (async (e: Event) => {
        const button = (e.target as Element).closest<HTMLButtonElement>('button')
        if (button?.textContent === '로그인') {
          e.preventDefault()
          e.stopImmediatePropagation()
          const inputs = root.querySelectorAll<HTMLInputElement>('input')
          if (!inputs[0]?.value || !inputs[1]?.value) {
            notify('아이디와 비밀번호를 입력해 주세요.')
            return
          }
          button.disabled = true
          try {
            await loginCustomer(inputs[0].value, inputs[1].value)
            router.replace(
              new URLSearchParams(location.search).get('next')?.startsWith('/customer-app/')
                ? new URLSearchParams(location.search).get('next')!
                : '/customer-app/screen-5',
            )
          } catch (e) {
            notify(e instanceof Error ? e.message : '로그인 실패')
          } finally {
            button.disabled = false
          }
        }
      }) as EventListener,
      { capture: true },
    )
  }
  if (path === '/admin-app/screen-13') {
    const toggle = document.createElement('button')
    toggle.textContent = '읽지 않은 알림'
    toggle.setAttribute('aria-pressed', 'false')
    let unreadOnly = false
    const rows = Array.from(root.querySelectorAll<HTMLElement>('.bd>.row'))
    const read = new Set<HTMLElement>()
    on(toggle, 'click', () => {
      unreadOnly = !unreadOnly
      toggle.setAttribute('aria-pressed', String(unreadOnly))
      rows.forEach((r) => (r.hidden = unreadOnly && read.has(r)))
    })
    root.querySelector('.chips')?.after(toggle)
    rows.forEach((row) => {
      on(
        row,
        'click',
        () => {
          read.add(row)
          store.unread = Math.max(0, store.unread - 1)
        },
        { capture: true },
      )
      on(row, 'keydown', (e: Event) => {
        if ((e as KeyboardEvent).key === 'Enter') row.click()
      })
    })
  }
  if (path.startsWith('/admin-app/') && !path.endsWith('/screen-1')) {
  }
  on(
    root,
    'click',
    (e: Event) => {
      const b = (e.target as Element).closest<HTMLButtonElement>('button')
      if (!b) return
      const label = b.textContent?.trim() || ''
      if (path === '/admin-app/screen-1' && label === '로그인') {
        e.preventDefault()
        e.stopImmediatePropagation()
        notify('관리자 인증 서비스 연결이 필요합니다.')
        return
      }
      if (
        ['인증번호 받기', '확인', '비밀번호 변경', '회원가입 완료'].includes(label) &&
        /^\/customer-app\/screen-[234]$/.test(path)
      ) {
        e.preventDefault()
        e.stopImmediatePropagation()
        const passwords = root.querySelectorAll<HTMLInputElement>('input[type="password"]')
        if (passwords.length > 1 && passwords[0]!.value !== passwords[1]!.value)
          notify('비밀번호 확인이 일치하지 않습니다.')
        else notify('휴대폰 인증 서비스 연결이 필요합니다.')
        return
      }
      if (label === '로그아웃') {
        e.preventDefault()
        e.stopImmediatePropagation()
        void logoutCustomer()
          .then(() => router.replace('/customer-app/screen-1'))
          .catch((e) => notify(e.message))
      }
    },
    { capture: true },
  )
  return () => {
    stopGlow()
    disposeMap()
    abort.abort()
  }
}

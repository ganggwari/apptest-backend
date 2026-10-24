import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useApp } from '../stores/app'
import { backend } from '../services/backend'
import { syncAppPush } from '../services/push'
import { playAlertCue } from '../utils/alertCue'
import { reportSse, probeWs } from '../runtime/connStatus'
export type BackendEvent = { name: string; data: Record<string, any> }
const eventNames = [
  'connected',
  'heartbeat',
  'risk-update',
  'water-update',
  'risk-level-change',
  'control-change',
  'transit-update',
  'plate-queue-add',
  'plate-queue-done',
  'notice-new',
  'inquiry-new',
  'display-change',
  'notification-new',
  'data-changed',
  'pm-update',
]
// One stream for the app, restarted when the authenticated identity changes.
const WATCHDOG_MS = 40000 // 서버 하트비트 15초 간격의 2.5배
const BACKOFF_MS = [1000, 2000, 5000, 10000, 20000]
export function useBackendStream() {
  const app = useApp()
  let stream: EventSource | undefined
  let watchdog: number | undefined
  let retryTimer: number | undefined
  let attempts = 0
  let countVersion = 0
  let alive = true
  const url = () => (app.account?.role === 'REGIONAL_ADMIN' ? '/api/admin/stream' : '/api/stream')
  const emit = (name: string, data: Record<string, any>) =>
    window.dispatchEvent(new CustomEvent<BackendEvent>('routel-backend-event', { detail: { name, data } }))
  const clearTimers = () => {
    clearTimeout(watchdog)
    clearTimeout(retryTimer)
    watchdog = retryTimer = undefined
  }
  const close = () => {
    clearTimers()
    stream?.close()
    stream = undefined
  }
  const refreshManagerUnread = async (source: EventSource) => {
    if (app.account?.role !== 'REGIONAL_ADMIN') return
    const version = ++countVersion
    try {
      const result = await backend('/api/managers/me/notifications/unread-count')
      if (stream === source && version === countVersion) {
        const next = Math.max(0, Number(result?.total) || 0)
        if (next > app.unread && countVersion > 1) playAlertCue()   // 관리자: 새 알림이 늘면 소리·진동
        app.unread = next
      }
    } catch {
      /* Keep the last count until the next event or reconnect. */
    }
  }
  const scheduleRetry = () => {
    if (!alive || retryTimer) return
    const delay = BACKOFF_MS[Math.min(attempts++, BACKOFF_MS.length - 1)]
    retryTimer = window.setTimeout(() => {
      retryTimer = undefined
      connect()
    }, delay)
  }
  const armWatchdog = (source: EventSource) => {
    clearTimeout(watchdog)
    watchdog = window.setTimeout(() => {
      if (stream !== source) return
      // 하트비트가 끊김(프록시/절전/네트워크 전환): 연결을 새로 만든다.
      close()
      reportSse('lost', { retry: attempts })
      scheduleRetry()
    }, WATCHDOG_MS)
  }
  function connect() {
    close()
    if (!alive || typeof EventSource === 'undefined') return
    const source = new EventSource(url(), { withCredentials: true })
    stream = source
    reportSse('connecting', { retry: attempts, url: url() })
    source.onopen = () => {
      if (stream === source) reportSse('open', { event: 'open' })
    }
    armWatchdog(source)
    void refreshManagerUnread(source)
    source.onerror = () => {
      if (stream !== source) return
      reportSse(source.readyState === EventSource.CLOSED ? 'lost' : 'connecting', { retry: attempts })
      // CLOSED 면 브라우저가 재시도하지 않으므로 직접 백오프 재연결. CONNECTING 이면 브라우저 자동 재시도에 맡김.
      if (source.readyState === EventSource.CLOSED) {
        close()
        scheduleRetry()
      }
    }
    for (const name of eventNames)
      source.addEventListener(name, (event) => {
        if (stream !== source) return
        attempts = 0
        armWatchdog(source)
        reportSse('open', { event: name })
        if (name === 'heartbeat') return
        if (name === 'connected' || name === 'notification-new') void refreshManagerUnread(source)
        if (name === 'connected') {
          emit(name, {})
          return
        }
        try {
          const data = JSON.parse((event as MessageEvent).data || '{}')
          if (data && typeof data === 'object' && !Array.isArray(data)) emit(name, data)
        } catch {
          /* Ignore malformed events; REST remains authoritative. */
        }
      })
  }
  // 로그인 주체(계정/권한/지역)가 바뀌면 재연결.
  const stop = watch(
    () => [
      app.account?.id,
      app.account?.role,
      app.authVersion,
      (app.account?.regions || [])
        .map((region) => String(region.id))
        .sort()
        .join(','),
    ],
    () => {
      attempts = 0
      syncAppPush(app.account?.role === 'customer', app.account?.role === 'REGIONAL_ADMIN')
      connect()
      if (app.account) probeWs()
    },
    { immediate: true },
  )
  // 탭 복귀/네트워크 복구 시 끊겼으면 즉시 재연결.
  const wake = () => {
    if (document.visibilityState === 'hidden') return
    if (!stream || stream.readyState === EventSource.CLOSED) {
      attempts = 0
      connect()
    }
  }
  document.addEventListener('visibilitychange', wake)
  window.addEventListener('online', wake)
  onBeforeUnmount(() => {
    alive = false
    stop()
    document.removeEventListener('visibilitychange', wake)
    window.removeEventListener('online', wake)
    close()
  })
}
export function useBackendEvents(handler: (event: BackendEvent) => void) {
  const receive = (event: Event) => handler((event as CustomEvent<BackendEvent>).detail)
  onMounted(() => window.addEventListener('routel-backend-event', receive))
  onBeforeUnmount(() => window.removeEventListener('routel-backend-event', receive))
}

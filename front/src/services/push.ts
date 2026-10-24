// 푸시 토큰 등록.
//  - Android WebView 앱(JS 브릿지 window.PufAndroid): 앱이 받은 FCM 토큰을 등록 (platform APP)
//  - 브라우저/PWA: Firebase 웹 푸시 토큰을 받아 등록 (platform WEB). 서버 /api/push/config 가 설정을 내려줄 때만 동작
// 푸시를 못 쓰는 환경에서도 실시간 알림은 SSE 로 계속 받는다.
declare global {
  interface Window {
    PufAndroid?: { getFcmToken?: () => string; requestNotificationPermission?: () => void }
    onPufPushToken?: (token: string) => void
  }
}
const WEB_FLAG = 'puf-web-push'
const WEB_TOKEN = 'puf-web-push-token'
let lastRegistered = ''
let loggedIn = false
let kind: 'customers' | 'managers' = 'customers'   // 로그인한 계정에 따라 토큰을 등록할 API 가 다르다
/** 내 푸시 토큰 API 주소 (고객/관리자) */
export const deviceTokenPath = () => `/api/${kind}/me/device-tokens`

function store(key: string, value?: string | null): string | null {
  try {
    if (value === undefined) return localStorage.getItem(key)
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    /* 저장소를 못 쓰는 환경 */
  }
  return null
}

async function register(token: string, platform: 'APP' | 'WEB') {
  if (!token || !loggedIn || token === lastRegistered) return false
  try {
    const response = await fetch(deviceTokenPath(), {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, platform }),
    })
    if (response.ok) lastRegistered = token
    return response.ok
  } catch {
    return false
  }
}

// ---------------------------------------------------------------- 웹 푸시 (브라우저/PWA)
export type WebPushResult = { ok: boolean; reason?: string }

export function webPushSupported() {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  )
}
export function webPushWanted() {
  return store(WEB_FLAG) === 'on'
}

/** 알림 권한이 허용된 상태에서 FCM 웹 토큰을 받아 서버에 등록한다 */
export async function enableWebPush(): Promise<WebPushResult> {
  if (window.PufAndroid) return { ok: false, reason: '앱에서는 앱 알림을 사용합니다.' }
  if (!webPushSupported())
    return { ok: false, reason: '이 브라우저는 백그라운드 푸시를 지원하지 않습니다. (iPhone은 홈 화면에 추가한 앱에서만 가능)' }
  if (Notification.permission !== 'granted') return { ok: false, reason: '알림 권한이 필요합니다.' }
  let config: any
  try {
    const response = await fetch('/api/push/config', { credentials: 'include' })
    config = (await response.json())?.data
  } catch {
    return { ok: false, reason: '푸시 설정을 불러오지 못했습니다.' }
  }
  if (!config?.enabled) return { ok: false, reason: '서버에 웹 푸시 설정이 없습니다.' }
  try {
    const [{ initializeApp, getApps }, { getMessaging, getToken, isSupported }] = await Promise.all([
      import('firebase/app'),
      import('firebase/messaging'),
    ])
    if (!(await isSupported())) return { ok: false, reason: '이 브라우저는 FCM 웹 푸시를 지원하지 않습니다.' }
    const app = getApps()[0] || initializeApp({
      apiKey: config.apiKey,
      projectId: config.projectId,
      messagingSenderId: config.senderId,
      appId: config.appId,
    })
    const registration =
      (await navigator.serviceWorker.getRegistration('/')) ||
      (await navigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' }))
    await navigator.serviceWorker.ready
    const token = await getToken(getMessaging(app), {
      vapidKey: config.vapidKey,
      serviceWorkerRegistration: registration,
    })
    if (!token) return { ok: false, reason: '푸시 토큰을 받지 못했습니다.' }
    if (!(await register(token, 'WEB'))) return { ok: false, reason: '서버에 토큰을 등록하지 못했습니다. 로그인을 확인해 주세요.' }
    store(WEB_FLAG, 'on')
    store(WEB_TOKEN, token)
    return { ok: true }
  } catch (error) {
    return { ok: false, reason: (error as Error)?.message || '웹 푸시를 켜지 못했습니다.' }
  }
}

export async function disableWebPush() {
  store(WEB_FLAG, 'off')
  const token = store(WEB_TOKEN)
  store(WEB_TOKEN, null)
  lastRegistered = ''
  if (!token || !loggedIn) return
  try {
    await fetch(deviceTokenPath() + '?token=' + encodeURIComponent(token), {
      method: 'DELETE',
      credentials: 'include',
    })
  } catch {
    /* 다음 발송 때 만료 토큰으로 정리된다 */
  }
}

// ---------------------------------------------------------------- 로그인 상태가 바뀔 때
export function syncAppPush(customerLoggedIn: boolean, managerLoggedIn = false) {
  kind = managerLoggedIn ? 'managers' : 'customers'
  loggedIn = customerLoggedIn || managerLoggedIn
  if (!loggedIn) {
    lastRegistered = ''
    return
  }
  if (window.PufAndroid) {
    window.onPufPushToken = (token) => void register(token, 'APP')
    window.PufAndroid.requestNotificationPermission?.()
    const token = window.PufAndroid.getFcmToken?.()
    if (token) void register(token, 'APP')
    return
  }
  // 이미 켜 둔 브라우저는 로그인할 때마다 조용히 토큰을 다시 등록한다
  if (webPushWanted() && webPushSupported() && Notification.permission === 'granted') void enableWebPush()
}
export {}

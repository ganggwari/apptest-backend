import { useApp } from '../stores/app'
// Cookie credentials and the observed first-phase endpoints are preserved.
export async function customerRequest(path: string, body?: unknown) {
  const response = await fetch(path, {
    method: body === undefined ? 'GET' : 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const data = await response.json().catch(() => null)
  if (!response.ok || data?.success === false || !data)
    throw new Error(data?.message || '서버 응답을 확인하지 못했습니다. 연결 설정을 확인해 주세요.')
  return data
}
export async function loginCustomer(loginId: string, password: string) {
  const data = await customerRequest('/api/customers/login', { loginId, password })
  if (data.data?.id == null) throw new Error('로그인 응답에 사용자 정보가 없습니다.')
  localStorage.setItem('routel-session-kind', 'customer')
  useApp().authVersion++
  useApp().account = { id: String(data.data.id), name: data.data.name || loginId, role: 'customer' }
}
export async function restoreCustomer() {
  const app = useApp(),
    version = app.authVersion
  try {
    const body = await customerRequest('/api/customers/me')
    if (app.authVersion === version && body.data?.id != null)
      app.account = { id: String(body.data.id), name: body.data.name || '', role: 'customer' }
  } catch {
    /* Signed-out state is valid; no token is fabricated. */
  }
}
export async function logoutCustomer() {
  const response = await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
  if (!response.ok) throw new Error('로그아웃에 실패했습니다.')
  useApp().clearAccount()
}

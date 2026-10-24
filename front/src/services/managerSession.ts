import { backend } from './backend'
import { useApp } from '../stores/app'
export async function loginManager(loginId: string, password: string) {
  const user = await backend('/api/managers/login', { method: 'POST', body: { loginId, password } })
  if (!user?.id || !user.role) throw new Error('관리자 로그인 응답을 확인하지 못했습니다.')
  const app = useApp()
  app.authVersion++
  localStorage.setItem('routel-session-kind', 'manager')
  app.account = {
    id: String(user.id),
    name: user.name,
    role: user.role,
    regions: user.regions || [],
  }
  return user
}
export async function restoreManager() {
  const app = useApp(),
    version = app.authVersion
  try {
    const user = await backend('/api/managers/me')
    if (version === app.authVersion)
      app.account = {
        id: String(user.id),
        name: user.name,
        role: user.role,
        regions: user.regions || [],
      }
    return user
  } catch {
    return null
  }
}

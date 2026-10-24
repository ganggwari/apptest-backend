import { backend } from './backend'
import { useApp } from '../stores/app'
let restored = false,
  pending: Promise<void> | undefined
// Storage only determines probe order. The server establishes the session.
export async function restoreSession(force = false) {
  if (pending) return pending
  if (restored && !force) return
  const app = useApp(),
    version = app.authVersion
  pending = (async () => {
    const kinds =
      localStorage.getItem('routel-session-kind') === 'manager'
        ? ['manager', 'customer']
        : ['customer', 'manager']
    for (const kind of kinds) {
      try {
        const user = await backend(`/api/${kind === 'customer' ? 'customers' : 'managers'}/me`)
        if (app.authVersion !== version) return
        if (user?.id == null || (kind === 'manager' && user.role !== 'REGIONAL_ADMIN')) continue
        app.account = {
          id: String(user.id),
          name: user.name || '',
          role: kind === 'customer' ? 'customer' : user.role,
          regions: user.regions || [],
        }
        localStorage.setItem('routel-session-kind', kind)
        return
      } catch {
        if (app.authVersion !== version) return
      }
    }
    if (app.authVersion === version) app.clearAccount()
  })().finally(() => {
    restored = true
    pending = undefined
  })
  return pending
}

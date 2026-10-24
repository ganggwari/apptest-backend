const adminAuth = new Set([
  '/admin-login',
  '/admin-signup',
  '/admin-password-reset',
  '/admin-app/screen-1',
  '/admin-app/signup',
  '/admin-app/password-reset',
])
export function requiresCustomer(path: string) {
  return (
    path.startsWith('/my-') ||
    path.startsWith('/customer-app/account/') ||
    /^\/customer-app\/screen-(12|13)$/.test(path)
  )
}
export function requiresManager(path: string) {
  return (
    !adminAuth.has(path) &&
    (/^\/(?:admin-|admin-app\/|fac-|pm-|cs-|bot-|shift-)/.test(path) ||
      [
        '/index',
        '/verify',
        '/logs',
        '/notifications',
        '/handover-pdf',
        '/jurisdiction-contacts',
      ].includes(path))
  )
}
export function loginDestination(next: unknown, manager: boolean): string | undefined {
  if (typeof next !== 'string' || !next.startsWith('/') || next.startsWith('//')) return
  const path = next.split(/[?#]/)[0]!
  if (manager ? requiresManager(path) : requiresCustomer(path)) return next
}

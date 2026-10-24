import { createRouter, createWebHistory } from 'vue-router'
import { useApp } from './stores/app'
import { restoreSession } from './services/session'
import { requiresCustomer, requiresManager } from './services/access'
// 앱 시연 배포본: 고객 앱(/customer-app/*)과 관리자 앱(/admin-app/*) 화면만 둔다.
const pages = import.meta.glob([
  './pages/customer-app-*.vue',
  './pages/admin-app-*.vue',
  './pages/backend-resource.vue',
  './pages/service-auth.vue',
])
const routes = [
{ path: '/admin-app/menu', component: pages['./pages/admin-app-menu.vue'] },
  { path: '/admin-app/notifications', component: pages['./pages/backend-resource.vue'] },
  { path: '/customer-app/password-reset', component: pages['./pages/service-auth.vue'] },
  { path: '/admin-app/password-reset', component: pages['./pages/service-auth.vue'] },
  { path: '/admin-app/signup', component: pages['./pages/service-auth.vue'] },
  { path: '/customer-app/find-id', component: pages['./pages/customer-app-help.vue'] },
  { path: '/customer-app/notices', component: pages['./pages/customer-app-help.vue'] },
  { path: '/customer-app/faq', component: pages['./pages/customer-app-help.vue'] },
  { path: '/customer-app/screen-1', component: pages['./pages/customer-app-1.vue'] },
  { path: '/customer-app/screen-2', component: pages['./pages/customer-app-2.vue'] },
  { path: '/customer-app/screen-3', component: pages['./pages/customer-app-3.vue'] },
  { path: '/customer-app/screen-4', component: pages['./pages/customer-app-4.vue'] },
  { path: '/customer-app/screen-5', component: pages['./pages/customer-app-5.vue'] },
  { path: '/customer-app/screen-6', component: pages['./pages/customer-app-6.vue'] },
  { path: '/customer-app/screen-7', component: pages['./pages/customer-app-7.vue'] },
  { path: '/customer-app/screen-8', component: pages['./pages/customer-app-8.vue'] },
  { path: '/customer-app/screen-9', component: pages['./pages/customer-app-9.vue'] },
  { path: '/customer-app/screen-10', component: pages['./pages/customer-app-10.vue'] },
  { path: '/customer-app/screen-11', component: pages['./pages/customer-app-11.vue'] },
  { path: '/customer-app/screen-12', component: pages['./pages/customer-app-12.vue'] },
  { path: '/customer-app/screen-13', component: pages['./pages/customer-app-13.vue'] },
  { path: '/customer-app/screen-14', component: pages['./pages/customer-app-14.vue'] },
  { path: '/admin-app/screen-1', component: pages['./pages/admin-app-1.vue'] },
  { path: '/admin-app/screen-2', component: pages['./pages/admin-app-2.vue'] },
  { path: '/admin-app/screen-3', component: pages['./pages/admin-app-3.vue'] },
  { path: '/admin-app/screen-4', component: pages['./pages/admin-app-4.vue'] },
  { path: '/admin-app/screen-5', component: pages['./pages/admin-app-5.vue'] },
  { path: '/admin-app/screen-6', component: pages['./pages/admin-app-6.vue'] },
  { path: '/admin-app/screen-7', component: pages['./pages/admin-app-7.vue'] },
  { path: '/admin-app/screen-8', component: pages['./pages/admin-app-8.vue'] },
  { path: '/admin-app/screen-9', component: pages['./pages/admin-app-9.vue'] },
  { path: '/admin-app/screen-10', component: pages['./pages/admin-app-10.vue'] },
  { path: '/admin-app/screen-11', component: pages['./pages/admin-app-11.vue'] },
  { path: '/admin-app/screen-12', component: pages['./pages/admin-app-12.vue'] },
  { path: '/admin-app/screen-13', redirect: '/admin-app/notifications' },
  { path: '/admin-app/screen-14', component: pages['./pages/admin-app-14.vue'] },
  {
    path: '/admin-app/:resource(ocr|reviews|transits)',
    component: pages['./pages/backend-resource.vue'],
  },

  {
    path: '/admin-app/:section(history|document|contacts)',
    component: pages['./pages/admin-app-extra.vue'],
  },

  {
    path: '/customer-app/account/:section(profile|emergency|cars|settings|inquiries|withdraw)',
    component: pages['./pages/customer-app-account.vue'],
    meta: { appMember: true },
  },
  { path: '/', redirect: '/customer-app/screen-5' },
  { path: '/customer-app', redirect: '/customer-app/screen-5' },
  { path: '/admin-app', redirect: '/admin-app/screen-1' },
  { path: '/:pathMatch(.*)*', redirect: '/customer-app/screen-5' },
]
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to) => (to.hash ? { el: to.hash, top: 24 } : { top: 0 }),
})
router.beforeEach(async (to) => {
  const customer = requiresCustomer(to.path),
    manager = requiresManager(to.path)
  await restoreSession(customer || manager)
  const role = useApp().account?.role
  if ((customer && role !== 'customer') || (manager && role !== 'REGIONAL_ADMIN')) {
    const path = manager ? '/admin-app/screen-1' : '/customer-app/screen-1'
    return { path, query: { next: to.fullPath }, replace: true }
  }
})
export default router

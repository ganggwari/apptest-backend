<script setup lang="ts">
import './styles/customer-app-shell.css'
import './styles/notification-glow.css'
import { useNotificationGlow } from './stores/notificationGlow'
import { useBackendEvents } from './composables/useBackendEvents'
import { useBackendStream } from './composables/useBackendEvents'
import AdminAppNav from './components/AdminAppNav.vue'
import AdminAppHeader from './components/AdminAppHeader.vue'
import PwaStatus from './components/PwaStatus.vue'
import { watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { VNode } from 'vue'
import ServiceAppLayout from './components/ServiceAppLayout.vue'
import AdminAppFacilityLive from './pages/admin-app-facility-live.vue'
import AdminAppOverview from './pages/admin-app-overview.vue'
import ServiceAuth from './pages/service-auth.vue'
import ServiceNotifications from './pages/service-notifications.vue'
import ServiceAccount from './pages/service-account.vue'
import ServiceResource from './pages/backend-resource.vue'
import LiveMap from './pages/c-live-map.vue'
import { resourceRoutes } from './data/backendResources'
import CustomerArrivalAlert from './components/CustomerArrivalAlert.vue'
import AdminUrgentPopup from './components/AdminUrgentPopup.vue'
import { mobileInteractions } from './runtime/mobileInteractions'
import { useApp } from './stores/app'
useBackendStream()
const app = useApp()
const notificationGlow = useNotificationGlow()
watch(() => [app.account?.id, app.account?.role], () => notificationGlow.reset())
useBackendEvents(({ name, data }) => {
  if (name === 'notification-new') notificationGlow.mark('notification', data.id)
  if (name === 'notice-new') notificationGlow.mark('notice', data.id ?? data.noticeId)
  if (name === 'inquiry-new') notificationGlow.mark('inquiry', data.id ?? data.inquiryId)
})
watch(
  () => app.theme,
  (t) => (document.documentElement.dataset.theme = t),
  { immediate: true },
)
const route = useRoute(),
  router = useRouter()
const customer = computed(() => false)   // 데스크톱 고객 사이트 제거: 항상 앱 화면
watch(
  () => route.path,
  (path) => {
    if (!/^\/(admin|customer)-app\//.test(path)) return
    const kind = path.startsWith('/admin-app/') ? 'admin' : 'customer'
    const manifest = document.querySelector<HTMLLinkElement>('#app-manifest'),
      icon = document.querySelector<HTMLLinkElement>('#app-touch-icon')
    if (manifest) manifest.href = `/${kind}.webmanifest`
    if (icon) icon.href = `/icons/${kind}-180.png`
  },
  { immediate: true },
)
const serviceComponent = computed(() => {
  const path = route.path
  if (['/customer-app/screen-12'].includes(path)) return ServiceNotifications
  if (
    [
      '/admin-app/screen-1',
      '/customer-app/screen-1',
      '/customer-app/screen-2',
      '/customer-app/screen-3',
      '/customer-app/screen-4',
      '/customer-app/password-reset',
      '/admin-app/signup',
      '/admin-app/password-reset',
    ].includes(path)
  )
    return ServiceAuth
  if (['/customer-app/notices', '/customer-app/faq'].includes(path))
    return ServiceResource
  if (['/customer-app/screen-5', '/customer-app/screen-6'].includes(path)) return LiveMap
  if (path === '/admin-app/screen-5') return AdminAppFacilityLive
  if (path === '/admin-app/screen-3') return AdminAppOverview
  if (
    ['/admin-app/screen-14'].includes(path) ||
    /^\/customer-app\/account\/(profile|emergency|cars|settings|withdraw)$/.test(path)
  )
    return ServiceAccount
  if (
    resourceRoutes[path] ||
    /^\/admin-app\/(ocr|reviews|transits)$/.test(path)
  )
    return ServiceResource
  return null
})
const managerApp = computed(
  () =>
    route.path.startsWith('/admin-app/') &&
    !['/admin-app/screen-1', '/admin-app/signup', '/admin-app/password-reset'].includes(route.path),
)
const appService = computed(
  () => !!serviceComponent.value && /^\/(admin|customer)-app\//.test(route.path),
)
const cleanups = new Map<HTMLElement, () => void>()
function mounted(vnode: VNode) {
  const el = vnode.el as HTMLElement
  if (
    !serviceComponent.value &&
    (route.path.startsWith('/customer-app/') || route.path.startsWith('/admin-app/'))
  )
    cleanups.set(el, mobileInteractions(el, route.path, router, app))
}
function unmounted(vnode: VNode) {
  const el = vnode.el as HTMLElement
  cleanups.get(el)?.()
  cleanups.delete(el)
}
</script>
<template>
  <div
    :class="{'admin-interface':!customer&&!route.path.startsWith('/customer-app/'),'manager-app-shell':managerApp,'customer-shell':customer,'customer-editorial':customer&&!['/c-map','/c-live-map'].includes(route.path),'customer-app-editorial':route.path.startsWith('/customer-app/')&&!['/customer-app/screen-5','/customer-app/screen-6'].includes(route.path),'service-mobile':appService,'admin-shell':!customer&&!route.path.startsWith('/admin-app/')&&!route.path.startsWith('/customer-app/')}"
  >
    <AdminAppHeader v-if="managerApp&&route.path!=='/admin-app/menu'" />
    <PwaStatus :hide-install="managerApp" v-if="/^\/(admin|customer)-app\//.test(route.path)" />
    <CustomerArrivalAlert />
    <AdminUrgentPopup v-if="managerApp" />
    <RouterView v-slot="{Component}">
      <ServiceAppLayout v-if="appService">
        <component :is="serviceComponent" :key="$route.path" />
      </ServiceAppLayout>
      <component
        v-else
        :is="serviceComponent||Component"
        :key="$route.path"
        @vue:mounted="mounted"
        @vue:unmounted="unmounted"
      />
    </RouterView>
    <AdminAppNav v-if="managerApp&&route.path!=='/admin-app/menu'" />
  </div>
</template>

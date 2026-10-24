<script setup lang="ts">
import NotificationBell from './NotificationBell.vue'
import AdminSidebarLinks from '../components/AdminSidebarLinks.vue'
import '../styles/fac-list.css'
import MenuIcon from './MenuIcon.vue'
import { ref, computed, watch } from 'vue'
import { useApp } from '../stores/app'
import { backend } from '../services/backend'
import { useRouter, useRoute } from 'vue-router'
const route = useRoute(),
  mobile = computed(() => route.path.startsWith('/admin-app/'))
import AdminSidebarNav from './AdminSidebarNav.vue'
import AdminIdentity from './AdminIdentity.vue'
import { useBackendEvents } from '../composables/useBackendEvents'
import { useManagerNotifications } from '../stores/managerNotifications'
withDefaults(defineProps<{ standardMenu?: boolean }>(), { standardMenu: false })
const app = useApp(),
  router = useRouter(),
  open = ref(false),
  error = ref('')
const managerNotifications = useManagerNotifications()
watch(
  () => [app.account?.id, app.account?.role],
  () => {
    managerNotifications.reset()
    void managerNotifications.refresh()
  },
  { immediate: true },
)
useBackendEvents(({ name }) => {
  if (name === 'connected' || name === 'notification-new') void managerNotifications.refresh()
})
const navigating = ref(false)
watch(
  () => route.fullPath,
  () => {
    open.value = false
  },
)
async function navigateMenu(event: MouseEvent) {
  const link = (event.target as Element).closest<HTMLAnchorElement>('a[href]')
  if (
    !link ||
    event.button !== 0 ||
    event.ctrlKey ||
    event.metaKey ||
    event.shiftKey ||
    event.altKey ||
    link.target === '_blank' ||
    link.hasAttribute('download')
  )
    return
  const url = new URL(link.href, location.href)
  if (url.origin !== location.origin) return
  event.preventDefault()
  open.value = false
  if (navigating.value) return
  navigating.value = true
  error.value = ''
  try {
    await router.push(url.pathname + url.search + url.hash)
  } catch {
    error.value = '페이지를 불러오지 못했습니다. 메뉴를 다시 선택해 주세요.'
  } finally {
    navigating.value = false
  }
}
async function logout() {
  try {
    await backend('/api/auth/logout', { method: 'POST' })
    app.clearAccount()
    await router.push(mobile.value ? '/admin-app/screen-1' : '/admin-login')
  } catch (e) {
    error.value = (e as Error).message
  }
}
</script>
<template>
  <div class="backend-layout wire-page page-fac-list restored-admin-layout">
    <template v-if="!mobile">
      <header class="hdr backend-header">
        <div class="hdr-l">
          <button
            class="iconbtn"
            type="button"
            aria-label="관리자 메뉴 열기"
            :aria-expanded="open"
            @click="open=!open"
          >
            <MenuIcon />
          </button>
          <RouterLink class="admin-home-logo" :to="mobile?'/admin-app/screen-3':'/admin-dashboard'">ROUTEL</RouterLink>
        </div>
        <div class="hdr-r">
          <NotificationBell class="manager-header-bell" @opened="open=false" />
          <RouterLink class="user backend-user" to="/admin-profile">
            {{app.account?.name||'관리자'}}님</RouterLink>
          <button class="btn sm" v-if="app.account" @click="logout">로그아웃</button>
          <RouterLink v-else to="/admin-login">관리자 로그인</RouterLink>
          <button class="ui-icon-button theme-toggle" type="button" aria-label="라이트·다크 모드 전환" :aria-pressed="app.theme==='dark'" :title="app.theme==='dark'?'라이트 모드로 전환':'다크 모드로 전환'" @click="app.themeToggle()"><UiIcon name="theme" /></button>
        </div>
      </header>
      <div v-if="open" class="backend-scrim scrim" @click="open=false"></div>
      <aside
        v-if="open"
        @click.capture="navigateMenu"
        class="backend-menu side open"
        aria-label="관리자 메뉴"
      >
        <div class="backend-menu-head side-top">
          <b>관리자 메뉴</b>
          <button class="btn sm" @click="open=false">닫기</button>
        </div>
        <AdminIdentity />
        <AdminSidebarNav live />
        <AdminSidebarLinks />
      </aside>
    </template>
    <main :class="mobile?'app-service-content':'backend-main page'" :aria-busy="navigating">
      <p v-if="navigating" role="status">페이지를 이동하고 있습니다…</p>
      <p v-if="error" role="alert">{{error}}</p>
      <slot />
    </main>
  </div>
</template>
<style src="../styles/components/backend-layout.css"></style>
<style src="../styles/components/backend-layout-2.css"></style>

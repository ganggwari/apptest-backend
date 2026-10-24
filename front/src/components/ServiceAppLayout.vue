<script setup lang="ts">
import AdminBackButton from './AdminBackButton.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useApp } from '../stores/app'
import CustomerAppNav from './CustomerAppNav.vue'
import NotificationBell from './NotificationBell.vue'
const route = useRoute(),
  app = useApp(),
  manager = computed(() => route.path.startsWith('/admin-app/'))
const auth = computed(
  () =>
    /\/(signup|password-reset)$/.test(route.path) ||
    route.path.endsWith('/screen-1') ||
    (!manager.value && /\/screen-[234]$/.test(route.path)),
)
const map = computed(() => route.path === '/customer-app/screen-5')
const title = computed(() =>
  route.path === '/admin-app/notifications'
    ? '알림'
    : auth.value
      ? route.path.endsWith('/screen-1')
        ? '로그인'
        : route.path.endsWith('/password-reset')
          ? '비밀번호 재설정'
          : '회원가입'
      : map.value
        ? 'ROUTEL'
        : manager.value
          ? { '3': '종합 현황', '4': '통제', '5': '시설 상세', '13': '알림', '14': '더보기' }[
              route.path.split('-').at(-1)!
            ] || '관리자 앱'
          : route.path.endsWith('/screen-12')
            ? '알림'
            : route.path.endsWith('/screen-6')
              ? '지하차도 상세'
              : route.path.endsWith('/notices')
                ? '공지사항'
                : route.path.endsWith('/faq')
                  ? 'FAQ'
                  : '내 정보',
)
const links = [
  ['현황', '/admin-app/screen-3', '▦'],
  ['통제', '/admin-app/screen-4', '◼'],
  ['예지보전', '/admin-app/screen-9', '⚙'],
  ['알림', '/admin-app/screen-13', '◔'],
  ['더보기', '/admin-app/screen-14', '☰'],
]
function active(path: string) {
  return route.path === path || (path.endsWith('/screen-4') && route.path.endsWith('/screen-5'))
}
</script>
<template>
  <div
    class="service-app-layout"
    :class="{'app-manager':manager,'app-auth':auth,'app-map-screen':map,'app-manager-overview':route.path==='/admin-app/screen-3'}"
  >
    <header v-if="!manager||auth" class="ah app-restored-header">
      <AdminBackButton v-if="manager&&!auth&&!route.path.endsWith('/screen-3')" />
      <RouterLink
        v-if="!manager&&!map&&!auth&&!route.path.endsWith('/screen-3')"
        :to="manager?'/admin-app/screen-3':'/customer-app/screen-5'"
        class="app-back"
        aria-label="이전 화면으로"
      ><UiIcon name="back" /></RouterLink>
      <strong>{{title}}</strong>
      <NotificationBell v-if="!auth&&!manager&&app.account?.role==='customer'" />
      <RouterLink
        v-if="!auth&&manager"
        class="app-bell"
        to="/admin-app/notifications"
        aria-label="알림"
      >
        <UiIcon name="bell" />
      </RouterLink>
      <button
        type="button"
        class="ui-icon-button theme-toggle"
        aria-label="라이트·다크 전환"
        @click="app.themeToggle()"
      >
          <UiIcon name="theme" />
        </button>
    </header>
    <main class="app-restored-body"><slot /></main>
    <CustomerAppNav v-if="!auth&&!manager" />
  </div>
</template>

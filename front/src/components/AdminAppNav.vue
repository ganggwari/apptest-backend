<script setup lang="ts">
import { useRoute } from 'vue-router'
import { uiIconPaths } from '../utils/ui-icons'
const route = useRoute()
const items = [
  {
    label: '현황',
    path: '/admin-app/screen-3',
    icon: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  },
  { label: '통제', path: '/admin-app/screen-4', icon: 'M4 5h16v14H4zM4 10h16M9 5v14M15 5v14' },
  {
    label: '예지보전',
    path: '/admin-app/screen-9',
    icon: 'M14 5a5 5 0 0 0-6 6L3 16l5 5 5-5a5 5 0 0 0 6-6l-3 3-4-4 3-3z',
  },
  {
    label: '알림',
    path: '/admin-app/notifications',
    icon: uiIconPaths.bell,
  },
  { label: '더보기', path: '/admin-app/screen-14', icon: uiIconPaths.menu },
]
function active(path: string) {
  const current = route.path
  if (path.endsWith('screen-4'))
    return /^\/admin-app\/(screen-[4-8]|ocr|reviews|transits)$/.test(current)
  if (path.endsWith('screen-9')) return /^\/admin-app\/(screen-(9|10|11|12)|history)$/.test(current)
  if (path === '/admin-app/notifications')
    return ['/admin-app/screen-13', '/admin-app/notifications'].includes(current)
  if (path.endsWith('screen-14'))
    return ['/admin-app/screen-14', '/admin-app/contacts', '/admin-app/document'].includes(current)
  return current === path
}
</script>
<template>
  <nav class="admin-app-bottom-nav" aria-label="관리자앱 메뉴">
    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      :aria-current="active(item.path)?'page':undefined"
    >
      <svg
        viewBox="0 0 24 24"
        width="22"
        height="22"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path :d="item.icon" />
      </svg>
      <span>{{item.label}}</span>
    </RouterLink>
  </nav>
</template>
<style scoped src="../styles/components/admin-app-nav.css"></style>

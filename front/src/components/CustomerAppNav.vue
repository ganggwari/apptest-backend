<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import '../styles/customer-app-nav.css'
const route = useRoute()
const active = (section: string) =>
  section === 'map'
    ? ['5', '6'].some((n) => route.path.endsWith(`/screen-${n}`))
    : section === 'directions'
      ? ['7', '8'].some((n) => route.path.endsWith(`/screen-${n}`))
      : route.path.startsWith('/customer-app/account/') ||
        ['/customer-app/notices', '/customer-app/faq'].includes(route.path) ||
        ['12', '13'].some((n) => route.path.endsWith(`/screen-${n}`))
</script>
<template>
  <nav class="tb customer-app-nav" aria-label="고객앱 메뉴">
    <RouterLink to="/customer-app/screen-5" :aria-current="active('map')?'page':undefined">
      <svg
        viewBox="0 0 24 24"
        width="22"
        height="22"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        aria-hidden="true"
      >
        <path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2V5Zm6-2v16M15 5v16" />
      </svg>
      <span>통제현황</span>
    </RouterLink>
 <RouterLink to="/customer-app/screen-7" :aria-current="active('directions')?'page':undefined">
      <svg
        viewBox="0 0 24 24"
        width="22"
        height="22"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        aria-hidden="true"
      >
        <path d="m21 3-7 18-3-8-8-3L21 3Zm0 0L11 13" />
      </svg>
      <span>길찾기</span>
    </RouterLink>
 <RouterLink to="/customer-app/screen-13" :aria-current="active('account')?'page':undefined">
      <svg
        viewBox="0 0 24 24"
        width="22"
        height="22"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        aria-hidden="true"
      >
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21v-2a8 8 0 0 1 16 0v2" />
      </svg>
      <span>내 정보</span>
    </RouterLink>
  </nav>
</template>

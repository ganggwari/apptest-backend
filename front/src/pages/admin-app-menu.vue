<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminAppMenuGroups } from '../data/admin-app-menu'
const route = useRoute(), router = useRouter()
const previous = computed(() => {
  const value = route.query.from
  return typeof value === 'string' && value.startsWith('/admin-app/') && !value.startsWith('/admin-app/menu')
    ? value : '/admin-app/screen-3'
})
function close() {
  if (window.history.state?.back === previous.value) router.back()
  else void router.replace(previous.value)
}
</script>
<template>
  <main class="admin-menu-page">
    <header><strong>ROUTEL 관리자 메뉴</strong><button type="button" class="ui-icon-button" aria-label="메뉴 닫기" @click="close"><UiIcon name="close" /></button></header>
    <nav aria-label="관리자 앱 페이지 목록">
      <section v-for="group in adminAppMenuGroups" :key="group.title">
        <h2>{{group.title}}</h2>
        <RouterLink v-for="[label,path] in group.links" :key="path" :to="path!" :aria-current="previous.split('?')[0]===path?'page':undefined">{{label}}<span aria-hidden="true">›</span></RouterLink>
      </section>
    </nav>
  </main>
</template>
<style scoped>
.admin-menu-page { display:flex; flex:1; flex-direction:column; width:100%; min-height:0; background:var(--surface,#fff); color:var(--foreground,#20252b); }
header { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 16px; border-bottom:1px solid var(--line); flex-shrink:0; }
header button { border:0; border-radius:8px; background:transparent; color:inherit; cursor:pointer; }
nav { flex:1; min-height:0; overflow:auto; overscroll-behavior:contain; padding:16px 16px max(24px,env(safe-area-inset-bottom)); }
section+section { margin-top:20px; }
h2 { font-size:12px; margin:0 0 8px; color:var(--ink-sub,#64748b); }
a { display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:44px; padding:10px 12px; border-radius:9px; color:inherit; text-decoration:none; font-size:14px; box-sizing:border-box; }
a[aria-current=page] { background:#e8f0f8; color:#173d62; }
:global(:root[data-theme=dark]) a[aria-current=page] { background:#30465c; color:#edf1f5; }
a:hover { background:var(--soft,#edf3f9); }
</style>

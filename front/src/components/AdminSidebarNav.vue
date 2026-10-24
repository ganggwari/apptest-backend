<script setup lang="ts">
import { useRoute } from 'vue-router'
import { adminSidebarGroups, sidebarLinkActive } from '../data/adminSidebar'
const route = useRoute()
withDefaults(defineProps<{ live?: boolean }>(), { live: false })
const groups = adminSidebarGroups
</script>
<template>
  <RouterLink class="sidebar-home" to="/admin-dashboard">종합 현황</RouterLink>
  <nav class="admin-sidebar-groups" aria-label="관리자 주요 메뉴">
    <details
      v-for="group in groups"
      :key="group.title"
      class="sidebar-group"
      :open="group.links.some((link)=>sidebarLinkActive(link,route.path))||(group===groups[0]&&route.path==='/admin-dashboard')"
    >
      <summary class="sidebar-group-label">{{group.title}}</summary>
      <template v-for="[label,preview,path] in group.links" :key="label">
        <RouterLink
          :to="preview||path!"
          :aria-current="sidebarLinkActive([label!,preview!,path!],route.path)?'page':undefined"
        >
          {{label}}
        </RouterLink>
      </template>
    </details>
  </nav>
</template>

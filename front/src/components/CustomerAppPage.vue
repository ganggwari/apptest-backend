<script setup lang="ts">
import NotificationBell from './NotificationBell.vue'
import { RouterLink } from 'vue-router'
import { useApp } from '../stores/app'
import CustomerAppNav from './CustomerAppNav.vue'
import '../styles/customer-app-account.css'
defineProps<{ title: string }>()
const app = useApp()
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-account">
    <div class="ph">
      <header class="ah">
        <RouterLink to="/customer-app/screen-13" class="app-back-title" aria-label="마이페이지로 돌아가기"><span class="ui-icon-button"><UiIcon name="back" /></span><strong>{{title}}</strong>
        </RouterLink>
        <NotificationBell />
        <button class="ui-icon-button theme-toggle" aria-label="라이트·다크 전환" @click="app.themeToggle()">
          <UiIcon name="theme" />
        </button>
      </header>
      <main class="bd app-account-body"><slot /></main>
      <CustomerAppNav />
    </div>
  </div>
</template>

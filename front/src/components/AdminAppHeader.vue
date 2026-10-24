<script setup lang="ts">
import NotificationBell from './NotificationBell.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useApp } from '../stores/app'
import AdminBackButton from './AdminBackButton.vue'
const route = useRoute(),
  app = useApp()
const titles: Record<string, string> = {
  '2': '근무 시작',
  '3': '종합 현황',
  '4': '지하차도·통제',
  '5': '시설 상세',
  '6': '통제 시작',
  '7': '통제 현황',
  '8': '통제 해제',
  '9': '설비 목록',
  '10': '설비 상세',
  '11': '점검 대상·알림',
  '12': '점검 등록',
  '13': '알림',
  '14': '더보기·내 정보',
  history: '점검 이력',
  document: '통제 전달 문서',
  contacts: '관할 담당자 연락처',
  notifications: '내 관리자 알림',
  ocr: 'OCR 인식 기록',
  reviews: '수동 판별',
  transits: '차량 통과 기록',
}
const section = computed(() => route.path.split('/').at(-1)!.replace('screen-', '')),
  title = computed(() => titles[section.value] || '관리자 앱'),
  fallback = computed(() =>
    ['10', '11', 'history'].includes(section.value)
      ? '/admin-app/screen-9'
      : section.value === '12'
        ? '/admin-app/screen-11'
        : ['5', '6', '7', '8'].includes(section.value)
          ? '/admin-app/screen-4'
          : '/admin-app/screen-3',
  )
</script>
<template>
  <header class="manager-app-header">
    <div class="manager-app-brandbar">
      <RouterLink :to="{path:'/admin-app/menu',query:{from:route.fullPath}}" class="manager-app-menu-link" aria-label="관리자 앱 메뉴 열기">
        <UiIcon name="menu" />
      </RouterLink>
      <RouterLink
        class="manager-app-logo"
        to="/admin-app/screen-3"
        aria-label="ROUTEL 관리자 앱 홈"
      >ROUTEL</RouterLink>
      <NotificationBell class="manager-app-bell-preview" />
    </div>
    <div class="manager-app-titlebar">
      <AdminBackButton :fallback="fallback" />
      <strong>{{title}}</strong>
      <button class="ui-icon-button theme-toggle" type="button" aria-label="라이트·다크 전환" :aria-pressed="app.theme==='dark'" @click="app.themeToggle()">
          <UiIcon name="theme" />
        </button>
    </div>
  </header>
</template>
<style scoped src="../styles/components/admin-app-header.css"></style>

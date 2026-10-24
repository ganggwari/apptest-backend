<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-14.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
const clock = ref(Date.now())
let timer: number | undefined
onMounted(() => {
  timer = window.setInterval(() => (clock.value = Date.now()), 1000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>
<template>
  <div class="wire-page mobile-page page-admin-app-14">
    <div class="ph adm">
      <div class="ah">
        <span>더보기</span>
        <span class="inl-683edd9226"></span>
        <a class="app-bell" href="/admin-app/screen-13">알림</a>
        <button
          class="ui-icon-button theme-toggle"
          type="button"
          aria-label="라이트·다크 전환"
          @click="store.themeToggle()"
        >
          <UiIcon name="theme" />
        </button>
      </div>
      <div
        v-if="store.bannerAt > 0 &amp;&amp; clock-store.bannerAt&lt;300000"
        class="urgent-app-banner"
        role="alert"
      >초량1지하차도 위험 · 통제 상태를 확인하세요.</div>
      <div class="bd">
        <div class="card">
          <b class="inl-6750b23420">○○○님</b>
          <div class="sub">부산 · 지역 관리자</div>
        </div>
        <div class="row" @click="router.push('/admin-app/history')">
          <div class="g"><b>점검 이력</b></div>
          <small>›</small>
        </div>
        <div class="row">
          <div class="g"><b>푸시 알림 설정</b></div>
          <small>ON</small>
        </div>
        <RouterLink class="row" to="/admin-app/contacts">
          <div class="g"><b>관할 담당자 연락처</b></div>
          <small>›</small>
        </RouterLink>
        <div class="row">
          <div class="g"><b>앱 정보</b></div>
          <small>v1.0.0</small>
        </div>
        <button
          class="btn inl-26d3d1951d"
          type="button"
          @click="router.push('/admin-app/screen-1')"
        >로그아웃</button>
      </div>
      <div class="tb">
        <a class="" href="/admin-app/screen-3">
          <b>▦</b>현황</a>
        <a class="" href="/admin-app/screen-4">
          <b>◼</b>통제</a>
        <a class="" href="/admin-app/screen-9">
          <b>⚙</b>예지보전</a>
        <a class="" href="/admin-app/screen-13">
          <b>◔</b>알림</a>
        <a class="on" href="/admin-app/screen-14">
          <b>☰</b>더보기</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-13.css'
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
  <div class="wire-page mobile-page page-admin-app-13">
    <div class="ph adm">
      <div class="ah">
        <span>알림</span>
        <span class="inl-683edd9226">모두 읽음</span>
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
        <div class="chips">
          <button class="chip on" type="button">전체</button>
          <button class="chip" type="button">위험</button>
          <button class="chip" type="button">설비</button>
          <button class="chip" type="button">고객</button>
          <button class="chip" type="button">시스템</button>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-5')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-5')"
        >
          <span class="lv d inl-478ee99241">위험</span>
          <div class="g">
            <b>초량1지하차도 위험 단계로 상승</b>
            <small>방금</small>
          </div>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-10')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-10')"
        >
          <span class="lv d inl-478ee99241">설비</span>
          <div class="g">
            <b>P-01 배수펌프 위험 진입 (82점)</b>
            <small>5분 전</small>
          </div>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-5')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-5')"
        >
          <span class="lv n inl-478ee99241">고객</span>
          <div class="g">
            <b>신규 문의 접수</b>
            <small>12분 전</small>
          </div>
        </div>
        <div class="row">
          <span class="lv w inl-478ee99241">시스템</span>
          <div class="g">
            <b>초량2지하차도 데이터 미수신 10분</b>
            <small>1시간 전</small>
          </div>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-10')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-10')"
        >
          <span class="lv w inl-478ee99241">설비</span>
          <div class="g">
            <b>D-03 전광판 주의 진입 (55점)</b>
            <small>2시간 전</small>
          </div>
        </div>
      </div>
      <div class="tb">
        <a class="" href="/admin-app/screen-3">
          <b>▦</b>현황</a>
        <a class="" href="/admin-app/screen-4">
          <b>◼</b>통제</a>
        <a class="" href="/admin-app/screen-9">
          <b>⚙</b>예지보전</a>
        <a class="on" href="/admin-app/screen-13">
          <b>◔</b>알림</a>
        <a class="" href="/admin-app/screen-14">
          <b>☰</b>더보기</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-2.css'
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
  <div class="wire-page mobile-page page-admin-app-2">
    <div class="ph adm">
      <div class="ah">
        <span>ROUTEL</span>
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
        <div class="card"><b>홍○○님, 오늘도 수고하세요</b></div>
      </div>
      <div class="dim"></div>
      <div class="sheet inl-f0abc1db33">
        <b class="inl-5e0faad207">이전 근무 인수인계</b>
        <div class="sub">직전 근무조 · 06:00~14:00 · 박○○</div>
        <div class="card">
          <div class="row inl-4673a44c72">
            <div class="g">
              <b>데일리 리포트 (PDF)</b>
              <small>통제 1건 · 점검 2건 · 위험 상승 3건</small>
            </div>
            <small>열기 ›</small>
          </div>
        </div>
        <div class="card"><div class="sub">미처리 점검 대상 2건이 넘어왔어요</div></div>
        <button class="btn p" type="button" @click="router.push('/admin-app/screen-3')">닫기</button>
        <button class="btn" type="button" @click="router.push('/admin-app/screen-3')">닫기</button>
      </div>
      <div class="tb">
        <a class="on" href="/admin-app/screen-3">
          <b>▦</b>현황</a>
        <a class="" href="/admin-app/screen-4">
          <b>◼</b>통제</a>
        <a class="" href="/admin-app/screen-9">
          <b>⚙</b>예지보전</a>
        <a class="" href="/admin-app/screen-13">
          <b>◔</b>알림</a>
        <a class="" href="/admin-app/screen-14">
          <b>☰</b>더보기</a>
      </div>
    </div>
  </div>
</template>

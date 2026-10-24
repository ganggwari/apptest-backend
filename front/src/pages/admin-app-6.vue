<script setup lang="ts">
import AdminBackButton from '../components/AdminBackButton.vue'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import HoldControl from '../components/HoldControl.vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-6.css'
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
  <div class="wire-page mobile-page page-admin-app-6">
    <div class="ph adm">
      <div class="ah">
        <span class="app-header-title">
          <AdminBackButton fallback="/admin-app/screen-4" />
          <span>초량1지하차도</span>
        </span>
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
        <div class="card"><b>초량1지하차도</b></div>
      </div>
      <div class="dim"></div>
      <div class="sheet inl-f0abc1db33">
        <b class="inl-5e0faad207">통제 시작</b>
        <div class="sub">초량1지하차도</div>
        <div class="chips">
          <button class="chip on" type="button">침수 위험</button>
          <button class="chip" type="button">설비 점검</button>
          <button class="chip" type="button">사고</button>
          <button class="chip" type="button">기타</button>
        </div>
        <input
          class="inp inl-d1b3a3ffca"
          placeholder="사유 직접 입력 (필수)"
          aria-label="사유 직접 입력 (필수)"
          type="text"
        />
        <input
          class="inp inl-e7e33a7403"
          placeholder="전광판 문구 · 침수 위험 통행금지 ▾"
          aria-label="전광판 문구 · 침수 위험 통행금지 ▾"
          type="text"
        />
        <div class="card inl-d69d528a65">
          <div><b>☑ 경찰·소방 전달 문서(PDF) 저장</b></div>
          <div class="sub">○○경찰서 · ○○소방서 · </div>
        </div>
        <HoldControl action="block" facility-id="0" facility-name="초량1지하차도" />
        <button class="btn" type="button" @click="router.back()">취소</button>
      </div>
      <div class="tb">
        <a class="" href="/admin-app/screen-3">
          <b>▦</b>현황</a>
        <a class="on" href="/admin-app/screen-4">
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

<script setup lang="ts">
import AdminBackButton from '../components/AdminBackButton.vue'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-7.css'
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
  <div class="wire-page mobile-page page-admin-app-7">
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
        <div class="ban d">통제 중 · 10:42 시작 (○○○)</div>
        <div class="card">
          <h4>진행 상태</h4>
          <div class="row">
            <span class="inl-36510e94a5">✓</span>
            <div class="g">
              <b>전광판 표출</b>
              <small>통행금지 문구 표출 완료</small>
            </div>
          </div>
          <div class="row">
            <span class="inl-36510e94a5">✓</span>
            <div class="g">
              <b>입구 차단</b>
              <small>차단 완료</small>
            </div>
          </div>
          <div class="row">
            <span class="inl-36510e94a5">✓</span>
            <div class="g">
              <b>경찰·소방 전달 문서</b>
              <small>PDF 저장됨 · 사유: 침수 위험</small>
            </div>
          </div>
        </div>
        <button class="btn" type="button" @click="router.push('/admin-app/document')">전달 문서 다시 저장</button>
        <div class="kv">
          <div>
            <small>진입 차량</small>
            <b>0대</b>
          </div>
          <div>
            <small>강우량</small>
            <b>18.0 mm</b>
          </div>
        </div>
        <button
          class="btn inl-ccd60a8702"
          type="button"
          @click="router.push('/admin-app/screen-8')"
        >통제 해제</button>
        <div class="sub inl-91a87015f4">해제할 때도 사유를 입력하고 전달 문서를 남겨요</div>
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

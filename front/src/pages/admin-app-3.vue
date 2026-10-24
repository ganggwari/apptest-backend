<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-3.css'
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
  <div class="wire-page mobile-page page-admin-app-3">
    <div class="ph adm">
      <div class="ah">
        <span>종합 현황</span>
        <span class="inl-683edd9226">● 연결됨</span>
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
        <div class="ban d">초량1지하차도 위험 · 통제 중</div>
        <div class="kv">
          <div>
            <small>안전</small>
            <b>12</b>
          </div>
          <div>
            <small>주의</small>
            <b class="inl-81fa6ce477">3</b>
          </div>
          <div>
            <small>위험</small>
            <b class="inl-497726e8c9">1</b>
          </div>
          <div>
            <small>통제 중</small>
            <b>1</b>
          </div>
        </div>
        <div class="map">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none">
            <path d="M0 0H100V28C80 36 70 24 50 32S20 40 0 34Z" fill="#cfe2f3" opacity=".6"></path>
            <path
              d="M0 60C30 52 40 70 70 62S90 70 100 66"
              stroke="#fff"
              stroke-width="2.4"
              fill="none"
            ></path>
            <path d="M35 100C38 70 55 55 60 20" stroke="#fff" stroke-width="2.4" fill="none"></path>
          </svg>
          <i class="d inl-94de920e87"></i>
          <i class="w inl-97923cada7"></i>
          <i class="w inl-168d0a1043"></i>
          <i class="s inl-2590214bdd"></i>
          <i class="s inl-3c50ca00d3"></i>
          <i class="s inl-0e7cc9bb5a"></i>
          <i class="me inl-afc107c1af"></i>
        </div>
        <div class="card inl-a3d12b9b83">
          <b>점검 필요 3건</b>
          <span class="sub">예지보전 ›</span>
        </div>
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

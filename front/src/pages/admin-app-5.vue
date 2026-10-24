<script setup lang="ts">
import AdminBackButton from '../components/AdminBackButton.vue'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-5.css'
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
  <div class="wire-page mobile-page page-admin-app-5">
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
        <div class="inl-31a824a217">
          <span class="lv d">위험</span>
          <span class="lv n">정상 운영</span>
        </div>
        <div class="kv">
          <div>
            <small>강우량</small>
            <b>18.0 mm</b>
          </div>
          <div>
            <small>침수 임계치</small>
            <b>50 / 70</b>
          </div>
          <div>
            <small>진입 차량</small>
            <b>0대</b>
          </div>
          <div>
            <small>전광판</small>
            <b>정상 표출</b>
          </div>
        </div>
        <div class="vid">CCTV 영상 </div>
        <div class="card">
          <h4>권장</h4>
          <span class="sub">위험 단계예요. 통제를 검토하세요.</span>
        </div>
        <div class="card inl-7aab68d540">
          <h4 class="inl-1169661891">데이터 수신 상태</h4>
          <span class="sub">날씨 ✓ 10:40 · 위험도 ✓ 10:40 · CCTV ✓ 10:41</span>
        </div>
        <div class="ban w">차단됐는데 차량 3대 통과 중 · 현장 확인</div>
        <button
          class="btn d inl-26d3d1951d"
          type="button"
          @click="router.push('/admin-app/screen-6')"
        >통제 시작</button>
        <button class="btn" type="button">전광판 문구 변경</button>
        <a class="btn" href="/admin-app/screen-9">예지보전 장비 목록 보기</a>
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

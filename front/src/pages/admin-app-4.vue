<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-4.css'
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
  <div class="wire-page mobile-page page-admin-app-4">
    <div class="ph adm">
      <div class="ah">
        <span>통제</span>
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
        <div class="chips">
          <button class="chip on" type="button">전체</button>
          <button class="chip" type="button">위험</button>
          <button class="chip" type="button">주의</button>
          <button class="chip" type="button">통제 중</button>
        </div>
        <input
          class="inp inl-d1b3a3ffca"
          placeholder="시설 검색"
          aria-label="시설 검색"
          type="text"
        />
        <div
          class="row"
          @click="router.push('/admin-app/screen-5')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-5')"
        >
          <div class="g">
            <b>초량1지하차도</b>
            <small>통제 중 · 차량 0대</small>
          </div>
          <span class="lv d">위험</span>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-5')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-5')"
        >
          <div class="g">
            <b>초량2지하차도</b>
            <small>정상 운영 · 차량 2대</small>
          </div>
          <span class="lv w">주의</span>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-5')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-5')"
        >
          <div class="g">
            <b>우장춘지하차도</b>
            <small>정상 운영 · 차량 1대</small>
          </div>
          <span class="lv w">주의</span>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-5')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-5')"
        >
          <div class="g">
            <b>범천지하차도</b>
            <small>정상 운영 · 차량 0대</small>
          </div>
          <span class="lv s">안전</span>
        </div>
        <div
          class="row"
          @click="router.push('/admin-app/screen-5')"
          role="button"
          tabindex="0"
          @keydown.enter="router.push('/admin-app/screen-5')"
        >
          <div class="g">
            <b>문현지하차도</b>
            <small>정상 운영 · 차량 3대</small>
          </div>
          <span class="lv s">안전</span>
        </div>
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

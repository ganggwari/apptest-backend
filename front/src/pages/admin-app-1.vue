<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/admin-app-1.css'
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
  <div class="wire-page mobile-page page-admin-app-1">
    <div class="ph adm">
      <div class="ah">ROUTEL<a class="app-bell" href="/admin-app/screen-13">알림</a>
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
        <div class="inl-28002cbc3a">
          <div class="inl-9bda79891e">ROUTEL</div>
          <div class="sub">관리자 앱</div>
        </div>
        <div class="inl-946a990a1e"></div>
        <input class="inp" placeholder="아이디" aria-label="아이디" type="text" />
        <PasswordInput class="inp" placeholder="비밀번호" aria-label="비밀번호" />
        <button class="btn k" type="button" @click="router.push('/admin-app/screen-2')">로그인</button>
        <div class="sub inl-91a87015f4">관리자 계정 전용 · 비밀번호 재설정은 웹에서</div>
      </div>
    </div>
  </div>
</template>

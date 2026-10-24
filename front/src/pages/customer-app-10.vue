<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-10.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-10">
    <div class="ph">
      <div class="ah">ROUTEL<NotificationBell />
        <button
          class="ui-icon-button theme-toggle"
          type="button"
          aria-label="라이트·다크 전환"
          @click="store.themeToggle()"
        >
          <UiIcon name="theme" />
        </button>
      </div>
      <div class="bd">
        <div class="ban d">전방 초량1지하차도가 방금 통제됐어요</div>
        <div class="map">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none">
            <path d="M0 0H100V28C80 36 70 24 50 32S20 40 0 34Z" fill="#cfe2f3" opacity=".6"></path>
            <path
              d="M0 60C30 52 40 70 70 62S90 70 100 66"
              stroke="#fff"
              stroke-width="2.4"
              fill="none"
            ></path>
            <path
              d="M35 100C38 70 55 55 60 20"
              stroke="#fff"
              stroke-width="2.4"
              fill="none"
            ></path></svg
          ><svg viewBox="0 0 100 100" preserveAspectRatio="none">
            <path
              d="M18 84C34 70 46 56 62 48S80 28 84 14"
              stroke="#c62828"
              stroke-width="2.4"
              fill="none"
              stroke-dasharray="3 3"
            ></path>
            <path
              d="M18 84C20 62 34 46 52 44S76 36 84 14"
              stroke="#1a6fd6"
              stroke-width="3"
              fill="none"
            ></path>
          </svg>
          <i class="me inl-196a7307af"></i>
          <i class="d inl-94de920e87"></i>
          <i class="s inl-1ce555177d"></i>
        </div>
        <div class="card">
          <b>우회 경로 +6분</b>
          <div class="sub">초량1지하차도를 피해 △△로로 안내해요 · 도착 11:10</div>
        </div>
        <button class="btn p" type="button" @click="router.push('/customer-app/screen-9')">새 경로로 안내</button>
        <button class="btn" type="button" @click="router.push('/customer-app/screen-9')">기존 경로 유지</button>
      </div>
    </div>
  </div>
</template>

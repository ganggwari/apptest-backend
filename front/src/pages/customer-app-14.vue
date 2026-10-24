<script setup lang="ts">
import NotificationBell from '../components/NotificationBell.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-14.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-14">
    <div class="ph">
      <div class="ah">
        <span>← 챗봇 문의</span>
        <span class="inl-683edd9226"></span>
        <NotificationBell />
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
        <div class="chat b">안녕하세요! 지하차도 통제 현황이나 앱 사용법을 물어보세요.</div>
        <div class="chat u">초량1지하차도 지금 통제 중이에요?</div>
        <div class="chat b">네, 초량1지하차도는 10:40부터 통제 중이에요. 상세 화면에서 위험도 추이도 볼 수 있어요.</div>
        <div class="chips">
          <button class="chip" type="button">통제 현황</button>
          <button class="chip" type="button">날씨 보기</button>
          <button class="chip" type="button">문의하기</button>
        </div>
        <input
          class="inp inl-26d3d1951d"
          placeholder="메시지를 입력하세요"
          aria-label="메시지를 입력하세요"
          type="text"
        />
      </div>
    </div>
  </div>
</template>

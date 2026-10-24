<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-4.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-4">
    <div class="ph">
      <div class="ah">
        <span>← 비밀번호 찾기</span>
        <span class="inl-683edd9226"></span>
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
        <div class="sub">가입한 아이디와 휴대폰 번호로 본인 확인 후 새 비밀번호를 설정해요.</div>
        <input class="inp" placeholder="아이디" aria-label="아이디" type="text" />
        <div class="inl-31a824a217">
          <input class="inp inl-a9d9967a1c" placeholder="010" aria-label="010" type="text" />
          <input class="inp inl-d7f9d58ca8" placeholder="1234" aria-label="1234" type="text" />
          <input class="inp inl-d7f9d58ca8" placeholder="5678" aria-label="5678" type="text" />
        </div>
        <button class="btn p" type="button">인증번호 받기</button>
        <input
          class="inp inl-bc7d642668"
          placeholder="● ● ● ● ● ●"
          aria-label="● ● ● ● ● ●"
          type="text"
        />
        <PasswordInput class="inp" placeholder="새 비밀번호" aria-label="새 비밀번호" />
        <PasswordInput class="inp" placeholder="새 비밀번호 확인" aria-label="새 비밀번호 확인" />
        <button class="btn inl-26d3d1951d" type="button">비밀번호 변경</button>
      </div>
    </div>
  </div>
</template>

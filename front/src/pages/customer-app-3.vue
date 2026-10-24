<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import '../styles/customer-app-3.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-3">
    <div class="ph">
      <div class="ah">
        <span>← 회원가입</span>
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
        <div class="chips">
          <button class="chip" type="button">① 휴대폰 인증 ✓</button>
          <button class="chip on" type="button">② 정보 입력</button>
          <button class="chip" type="button">③ 동의</button>
        </div>
        <input
          class="inp"
          placeholder="아이디 (중복확인)"
          aria-label="아이디 (중복확인)"
          type="text"
        />
        <PasswordInput class="inp" placeholder="비밀번호" aria-label="비밀번호" />
        <PasswordInput class="inp" placeholder="비밀번호 재입력" aria-label="비밀번호 재입력" />
        <input class="inp" placeholder="이름" aria-label="이름" type="text" />
        <input
          class="inp"
          placeholder="생년월일 (YYYY.MM.DD)"
          aria-label="생년월일 (YYYY.MM.DD)"
          type="text"
        />
        <input class="inp" placeholder="이메일 (선택)" aria-label="이메일 (선택)" type="text" />
        <input
          class="inp"
          placeholder="주소 (선택) · 우편번호 찾기"
          aria-label="주소 (선택) · 우편번호 찾기"
          type="text"
        />
        <div class="card">
          <div><b>☑ 개인정보 이용 동의 (필수)</b></div>
          <div><b>☑ 알림 수신 동의</b></div>
          <div class="sub">알림 수신은 내 정보에서 언제든 바꿀 수 있어요</div>
        </div>
        <button
          class="btn p inl-26d3d1951d"
          type="button"
          @click="router.push('/customer-app/screen-5')"
        >가입하기</button>
      </div>
    </div>
  </div>
</template>

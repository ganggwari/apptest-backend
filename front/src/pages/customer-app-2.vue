<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApp } from '../stores/app'
import NationalitySelect from '../components/NationalitySelect.vue'
import CarrierSelect from '../components/CarrierSelect.vue'
import '../styles/customer-app-2.css'
const router = useRouter(),
  store = useApp()
const weather = ref(false)
const country = ref('+82')
function phoneInput(event: Event, length: number) {
  const input = event.target as HTMLInputElement
  input.value = input.value.replace(/\D/g, '').slice(0, length)
  if (input.value.length === length && input.nextElementSibling instanceof HTMLInputElement)
    input.nextElementSibling.focus()
}
</script>
<template>
  <div class="wire-page mobile-page page-customer-app-2">
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
          <button class="chip on" type="button">① 휴대폰 인증</button>
          <button class="chip" type="button">② 정보 입력</button>
          <button class="chip" type="button">③ 동의</button>
        </div>
        <NationalitySelect v-model="store.signupNationality" name="app-nationality" />
        <CarrierSelect v-model="store.signupCarrier" />
        <div class="sub inl-b08c664aae">휴대폰 번호 (숫자만, 칸마다 자동 이동)</div>
        <div class="app-signup-phone-row">
          <select v-model="country" class="app-signup-country" aria-label="국가번호">
            <option value="+82">+82 대한민국</option>
            <option value="+1">+1 미국·캐나다</option>
            <option value="+81">+81 일본</option>
            <option value="+86">+86 중국</option>
            <option value="+44">+44 영국</option>
            <option value="+61">+61 호주</option>
            <option value="+84">+84 베트남</option>
          </select>
          <input
            class="inp inl-a9d9967a1c"
            placeholder="010"
            aria-label="010"
            type="tel"
            inputmode="numeric"
            maxlength="3"
            @input="phoneInput($event,3)"
          />
          <input
            class="inp inl-d7f9d58ca8"
            placeholder="1234"
            aria-label="1234"
            type="tel"
            inputmode="numeric"
            maxlength="4"
            @input="phoneInput($event,4)"
          />
          <input
            class="inp inl-d7f9d58ca8"
            placeholder="5678"
            aria-label="5678"
            type="tel"
            inputmode="numeric"
            maxlength="4"
            @input="phoneInput($event,4)"
          />
        </div>
        <button class="btn p" type="button">인증번호 받기</button>
        <div class="inl-31a824a217">
          <input
            class="inp inl-15b008b5cd"
            placeholder="● ● ● ● ● ●"
            aria-label="● ● ● ● ● ●"
            type="text"
          />
          <button class="btn inl-ea45a29341" type="button">확인</button>
        </div>
        <div class="sub">남은 시간 02:41 · <u>인증번호 다시 받기</u>
        </div>
        <div class="card inl-cdfda7bde9"><div class="sub"></div></div>
        <button
          class="btn inl-867e7fed32"
          type="button"
          @click="router.push('/customer-app/screen-3')"
        >다음</button>
      </div>
    </div>
  </div>
</template>

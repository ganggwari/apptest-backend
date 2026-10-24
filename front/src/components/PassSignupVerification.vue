<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import BirthDateInput from './BirthDateInput.vue'
const open = defineModel<boolean>({ default: false }),
  props = defineProps<{ phone: string; name?: string; birthDate?: string }>(),
  emit = defineEmits<{ complete: [identity: { name: string; birthDate: string }] }>()
const dialog = ref<HTMLDialogElement>(),
  stage = ref(0),
  carrier = ref(''),
  name = ref(''),
  birthDate = ref(''),
  terms = ref([false, false, false, false]),
  remaining = ref(180)
const allAgreed = computed({
  get: () => terms.value.every(Boolean),
  set: (value) => (terms.value = terms.value.map(() => value)),
})
const phone = computed(() => props.phone)
let timer: ReturnType<typeof setInterval> | undefined
function stop() {
  clearInterval(timer)
  timer = undefined
}
watch(open, async (value) => {
  stop()
  if (value) {
    stage.value = 0
    carrier.value = ''
    terms.value = [false, false, false, false]
    name.value = props.name || ''
    birthDate.value = props.birthDate || ''
    remaining.value = 180
    await nextTick()
    dialog.value?.showModal()
  } else dialog.value?.close()
})
function request() {
  if (!carrier.value || !name.value.trim() || !birthDate.value || !allAgreed.value) return
  stage.value = 1
  remaining.value = 180
  stop()
  timer = setInterval(() => {
    remaining.value = Math.max(0, remaining.value - 1)
    if (!remaining.value) stop()
  }, 1000)
}
function finish() {
  stop()
  emit('complete', { name: name.value.trim(), birthDate: birthDate.value })
  open.value = false
}
onBeforeUnmount(() => {
  stop()
  dialog.value?.close()
})
</script>
<template>
  <dialog
    ref="dialog"
    class="pass-signup-dialog"
    aria-label="PASS 회원가입 본인인증"
    @cancel.prevent="open=false"
    @close="open=false"
  >
    <header>
      <strong class="pass-wordmark">PASS</strong>
      <span class="pass-demo-label">본인확인</span>
      <button class="ui-icon-button" type="button" aria-label="PASS 인증 닫기" @click="open=false">
          <UiIcon name="close" />
        </button>
    </header>
    <section v-if="stage===0">
      <p class="pass-eyebrow">ROUTEL 회원가입 · 휴대폰 본인확인</p>
      <h2>이용 중인 통신사를<br />선택해 주세요</h2>
      <div class="pass-carriers" role="group" aria-label="통신사 선택">
        <button
          v-for="item in ['SKT','KT','LG U+','SKT 알뜰폰','KT 알뜰폰','LG U+ 알뜰폰']"
          :key="item"
          type="button"
          :aria-pressed="carrier===item"
          @click="carrier=item"
        >
          {{item}}
        </button>
      </div>
      <form @submit.prevent="request">
        <label>이름<input v-model="name" aria-label="PASS 이름" autocomplete="name" required />
        </label>
        <label>생년월일<BirthDateInput v-model="birthDate" required />
        </label>
        <div class="pass-phone">
          <span>전화번호 인증</span>
          <input :value="phone" type="text" aria-label="인증 전화번호" disabled aria-describedby="pass-phone-lock-notice" />
        </div>
        <p id="pass-phone-lock-notice" class="phone-lock-notice">이 화면에서는 전화번호를 변경할 수 없습니다. 다른 번호를 사용하려면 인증창을 닫고 이전 화면에서 수정해 주세요.</p>
        <div class="pass-agreements">
          <label class="pass-agree-all">
            <input v-model="allAgreed" type="checkbox" />필수 항목 전체 동의</label>
          <label
            v-for="(text,index) in ['본인확인 서비스 이용약관','개인정보 수집 및 이용','고유식별정보 처리','통신사 이용약관']"
            :key="text"
          >
            <input v-model="terms[index]" type="checkbox" />[필수] {{text}}
          </label>
        </div>
        <button
          class="pass-primary"
          type="submit"
          :disabled="!carrier||!name.trim()||!birthDate||!allAgreed"
        >PASS 인증 요청</button>
      </form>
    </section>
    <section v-else-if="stage===1" class="pass-waiting">
      <div class="pass-phone-icon" aria-hidden="true">
        <span>PASS</span>
        <b>✓</b>
      </div>
      <h2>PASS 앱에서<br />인증을 진행해 주세요</h2>
      <p>{{carrier}} · {{phone}}</p>
      <ol>
        <li>휴대전화로 전달된 인증 요청 확인</li>
        <li>PASS 앱에서 비밀번호 또는 생체인증</li>
        <li>인증 후 아래 완료 확인 선택</li>
      </ol>
      <p class="pass-time">남은 시간 <strong role="timer" aria-live="off">
          {{Math.floor(remaining/60)}}:{{String(remaining%60).padStart(2,'0')}}
        </strong>
      </p>
      <p v-if="!remaining" role="status">요청 시간이 만료되었습니다. 다시 요청해 주세요.</p>
      <button class="pass-primary" type="button" :disabled="!remaining" @click="stage=2;stop()">인증 완료 확인</button>
      <button class="pass-text-button" type="button" @click="request">인증 다시 요청</button>
      <button class="pass-text-button" type="button" @click="stage=0;stop()">통신사 다시 선택</button>
    </section>
    <section v-else class="pass-waiting">
      <div class="pass-complete" aria-hidden="true">✓</div>
      <h2>본인인증 화면 확인이<br />완료되었습니다</h2>
      <p>{{name}}님, 회원정보 입력을 계속해 주세요.</p>
      <button class="pass-primary" type="button" @click="finish">회원가입 계속하기</button>
    </section>
    <footer>PASS 연동 전 화면입니다. 실제 인증 요청·본인확인·회원가입은 진행되지 않습니다.</footer>
  </dialog>
</template>
<style scoped src="../styles/components/pass-signup-verification.css"></style>

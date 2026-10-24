<script setup lang="ts">
import PassSignupVerification from '../components/PassSignupVerification.vue'
import { computed, reactive, ref, watch } from 'vue'
import { useVerificationTimer, formatRemaining } from '../composables/useVerificationTimer'
import { useRoute, useRouter } from 'vue-router'
import BackendLayout from '../components/BackendLayout.vue'
import DeadlineTimer from '../components/DeadlineTimer.vue'
import ServiceFields from '../components/ServiceFields.vue'
import { backend, queryString } from '../services/backend'
import { loginManager } from '../services/managerSession'
import { loginCustomer } from '../services/customerSession'
import { useApp } from '../stores/app'
import { loginDestination } from '../services/access'
import PostcodeSearch from '../components/PostcodeSearch.vue'
import BirthDateInput from '../components/BirthDateInput.vue'
import VerificationCodeInput from '../components/VerificationCodeInput.vue'
import { developmentSmsCode } from '../services/developmentSms'
import EmailInput from '../components/EmailInput.vue'
import PhoneInput from '../components/PhoneInput.vue'
import NationalitySelect from '../components/NationalitySelect.vue'
import { formatPhone } from '../utils/phone'
const app = useApp()
import type { Field } from '../data/backendResources'
const route = useRoute(),
  router = useRouter(),
  manager = computed(() => route.path.includes('admin-')),
  kind = computed(() =>
    route.path.includes('signup') ||
    /^\/customer-app\/screen-[234]$/.test(route.path) ||
    route.meta.auth === 'signup'
      ? 'signup'
      : route.path.includes('password-reset')
        ? 'reset'
        : 'login',
  ),
  base = computed(() => (manager.value ? '/api/managers' : '/api/customers')),
  step = ref(1),
  busy = ref(false),
  error = ref(''),
  message = ref(''),
  values = reactive<Record<string, any>>({}),
  verified = ref(false)
const mobilePassSignup = computed(
    () => !manager.value && kind.value === 'signup',
  ),
  passMode = ref(route.path.startsWith('/customer-app/')),
  passOpen = ref(false),
  passDemoVerified = ref(false)
function completePass(identity: { name: string; birthDate: string }) {
  values.name = identity.name
  values.birthDate = identity.birthDate
  passDemoVerified.value = true
  verified.value = true
  markVerified()
  step.value = 3
  message.value = 'PASS 인증 화면 확인을 완료했습니다. 회원정보 입력을 계속해 주세요.'
}
const {
  codeSeconds,
  resendSeconds,
  verifiedSeconds,
  issued,
  verified: markVerified,
  clear: clearTimers,
  refresh: refreshTimers,
} = useVerificationTimer()
const maskedPhone = ref(''),
  approvalEstimate = ref(0)
const rememberId = ref(false)
let firstInvalid: EventTarget | null = null
function focusInvalid(event: Event) {
  event.preventDefault()
  if (firstInvalid) return
  firstInvalid = event.target
  const input = event.target as HTMLElement
  input.scrollIntoView({ block: 'center', behavior: 'smooth' })
  input.focus({ preventScroll: true })
  setTimeout(() => { firstInvalid = null }, 0)
}
const rememberedIdKey = computed(
  () => `routel-remembered-login-id:${manager.value ? 'manager' : 'customer'}`,
)
watch(
  () => [kind.value, rememberedIdKey.value],
  () => {
    if (kind.value !== 'login') return
    let saved = ''
    try {
      saved = localStorage.getItem(rememberedIdKey.value) || ''
    } catch {}
    values.loginId = saved
    values.password = ''
    rememberId.value = !!saved
  },
  { immediate: true },
)
function forgetId() {
  if (!rememberId.value)
    try {
      localStorage.removeItem(rememberedIdKey.value)
    } catch {}
}
function saveLoginId() {
  try {
    if (rememberId.value)
      localStorage.setItem(rememberedIdKey.value, String(values.loginId || '').trim())
    else localStorage.removeItem(rememberedIdKey.value)
  } catch {}
}
const timerExpired = computed(
  () =>
    kind.value !== 'login' &&
    ((step.value === 2 && codeSeconds.value === 0) ||
      (step.value === 3 && verifiedSeconds.value === 0)),
)
function restartVerification() {
  if (mobilePassSignup.value && passMode.value) {
    passDemoVerified.value = false
    verified.value = false
    clearTimers()
    step.value = 1
    return
  }
  values.code = ''
  verified.value = false
  step.value = 2
  error.value = ''
  message.value = '인증번호를 다시 받아 주세요.'
}
const signupFields: Field[] = [
  { key: 'loginId', label: '아이디', required: true },
  { key: 'password', label: '비밀번호', required: true },
  { key: 'passwordConfirm', label: '비밀번호 확인', required: true },
  { key: 'name', label: '이름', required: true },
  { key: 'birthDate', label: '생년월일', type: 'date', required: true },
  { key: 'phone', label: '휴대전화', required: true },
  { key: 'email', label: '이메일', type: 'email' },
  { key: 'emergency', label: '비상 연락처' },
  { key: 'zipcode', label: '우편번호' },
  { key: 'roadAddress', label: '도로명 주소' },
  { key: 'detailAddress', label: '상세 주소' },
]
const fields = computed<Field[]>(() =>
  kind.value === 'login'
    ? [
        { key: 'loginId', label: '아이디', required: true },
        { key: 'password', label: '비밀번호', required: true },
      ]
    : kind.value === 'reset'
      ? step.value === 1
        ? [
            { key: 'loginId', label: '아이디', required: true },
            { key: 'phone', label: '휴대전화', required: true },
          ]
        : step.value === 2
          ? [{ key: 'code', label: '인증번호', required: true }]
          : [
              { key: 'newPassword', label: '새 비밀번호', required: true },
              { key: 'newPasswordConfirm', label: '새 비밀번호 확인', required: true },
            ]
      : manager.value
        ? step.value === 1
          ? [
              { key: 'name', label: '이름', required: true },
              { key: 'birthDate', label: '생년월일', type: 'date', required: true },
              { key: 'phone', label: '휴대전화', required: true },
            ]
          : step.value === 2
            ? [{ key: 'code', label: '인증번호', required: true }]
            : signupFields.slice(0, 3)
        : step.value === 1
          ? [{ key: 'phone', label: '휴대전화', required: true }]
          : step.value === 2
            ? [{ key: 'code', label: '인증번호', required: true }]
            : signupFields.filter((field) => field.key !== 'phone' || passDemoVerified.value),
)
const duplicateMessage = reactive<Record<string, string>>({}),
  checked = reactive<Record<string, string>>({}),
  checking = ref('')
const fieldGroups = computed(() =>
  kind.value === 'signup' && step.value === 3
    ? [
        {
          label: '\ud544\uc218 \uc785\ub825\uc0ac\ud56d',
          fields: fields.value.filter((f) => f.required),
        },
        {
          label: '\uc120\ud0dd \uc785\ub825\uc0ac\ud56d',
          fields: fields.value.filter((f) => !f.required),
        },
      ].filter((g) => g.fields.length)
    : [{ label: '', fields: fields.value }],
)
async function ensureUnique(field: string) {
  const value = String(values[field] || '').trim()
  if (!value) return
  if (checked[field] === value) return
  const result = await backend(
    '/api/customers/check-' +
      (field === 'loginId' ? 'login-id' : field) +
      queryString({ [field]: value }),
  )
  if (!result.available) {
    duplicateMessage[field] = '\uc774\ubbf8 \uc0ac\uc6a9 \uc911\uc785\ub2c8\ub2e4.'
    throw new Error(
      (field === 'phone'
        ? '\ud734\ub300\uc804\ud654'
        : field === 'email'
          ? '\uc774\uba54\uc77c'
          : '\uc544\uc774\ub514') +
        '\uc774(\uac00) \uc774\ubbf8 \ub4f1\ub85d\ub418\uc5b4 \uc788\uc2b5\ub2c8\ub2e4.',
    )
  }
  checked[field] = value
  duplicateMessage[field] = '\uc0ac\uc6a9 \uac00\ub2a5\ud569\ub2c8\ub2e4.'
}
async function checkDuplicate(field: string) {
  checking.value = field
  duplicateMessage[field] = ''
  try {
    await ensureUnique(field)
  } catch (e) {
    duplicateMessage[field] = (e as Error).message
  } finally {
    checking.value = ''
  }
}
function authLink(mode: 'login' | 'signup' | 'reset') {
  const mobile = route.path.includes('-app/')
  if (mobile) {
    const prefix = manager.value ? '/admin-app/' : '/customer-app/'
    return (
      prefix +
      (mode === 'login'
        ? 'screen-1'
        : mode === 'reset'
          ? 'password-reset'
          : manager.value
            ? 'signup'
            : 'screen-2')
    )
  }
  return manager.value
    ? '/admin-' + (mode === 'reset' ? 'password-reset' : mode)
    : '/' + (mode === 'reset' ? 'password-reset' : mode)
}
async function submit() {
  if (busy.value) return
  if (mobilePassSignup.value && passMode.value && step.value === 1) {
    error.value = ''
    message.value = ''
    passOpen.value = true
    return
  }
  refreshTimers()
  if (timerExpired.value) {
    error.value =
      step.value === 2
        ? '인증번호가 만료되었습니다. 다시 받아 주세요.'
        : '인증 유효 시간이 만료되었습니다. 다시 인증해 주세요.'
    return
  }
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    if (mobilePassSignup.value && passMode.value && passDemoVerified.value && step.value === 3) {
      if (values.password !== values.passwordConfirm)
        throw new Error('비밀번호 확인이 일치하지 않습니다.')
      clearTimers()
      step.value = 4
      message.value =
        '회원가입 화면 화면 확인이 완료되었습니다. 실제 계정은 생성되지 않았습니다. 실제 가입은 문자 인증으로 진행해 주세요.'
      return
    }

    if (kind.value === 'login') {
      if (manager.value) await loginManager(values.loginId, values.password)
      else await loginCustomer(values.loginId, values.password)
      saveLoginId()
      const next = loginDestination(route.query.next, manager.value)
      if (next) {
        await router.push(next)
        return
      }
      await router.push(
        manager.value
          ? route.path.startsWith('/admin-app/')
            ? '/admin-app/screen-3'
            : '/admin-dashboard'
          : route.path.startsWith('/customer-app/')
            ? '/customer-app/screen-5'
            : '/',
      )
      return
    }
    if (kind.value === 'reset') {
      if (step.value === 1) {
        const timing = await backend(base.value + '/password-reset/request', {
          method: 'POST',
          body: { loginId: values.loginId, phone: values.phone },
        })
        issued(timing)
        maskedPhone.value = timing?.maskedPhone || ''
        step.value = 2
        message.value = '인증번호를 발송했습니다.'
        const code = await developmentSmsCode(values.phone || '')
        if (code) { values.code = code; message.value = '개발용 인증번호를 자동으로 입력했습니다.' }
      } else if (step.value === 2) {
        await backend(base.value + '/password-reset/verify', {
          method: 'POST',
          body: { code: values.code },
        })
        markVerified()
        step.value = 3
      } else {
        if (values.newPassword !== values.newPasswordConfirm)
          throw new Error('새 비밀번호 확인이 일치하지 않습니다.')
        await backend(base.value + '/password-reset/confirm', {
          method: 'POST',
          body: { newPassword: values.newPassword, newPasswordConfirm: values.newPasswordConfirm },
        })
        await router.push(authLink('login'))
      }
      return
    }
    if (step.value === 1) {
      if (!manager.value) await ensureUnique('phone')
      const timing = await backend(
        base.value + (manager.value ? '/signup/employee-check' : '/phone-code/send'),
        {
          method: 'POST',
          body: manager.value
            ? { name: values.name, birthDate: values.birthDate, phone: values.phone }
            : { phone: values.phone },
        },
      )
      issued(timing)
      maskedPhone.value = timing?.maskedPhone || ''
      step.value = 2
      message.value = '인증번호를 발송했습니다.'
      const code = await developmentSmsCode(values.phone || '')
      if (code) { values.code = code; message.value = '개발용 인증번호를 자동으로 입력했습니다.' }
    } else if (step.value === 2) {
      await backend(
        base.value + (manager.value ? '/signup/phone-code/verify' : '/phone-code/verify'),
        {
          method: 'POST',
          body: manager.value ? { code: values.code } : { phone: values.phone, code: values.code },
        },
      )
      markVerified()
      verified.value = true
      step.value = 3
    } else {
      if (!verified.value) throw new Error('휴대전화 인증이 필요합니다.')
      if (values.password !== values.passwordConfirm)
        throw new Error('비밀번호 확인이 일치하지 않습니다.')
      if (!manager.value) {
        await ensureUnique('loginId')
        await ensureUnique('email')
      }
      const body = manager.value
        ? {
            loginId: values.loginId,
            password: values.password,
            passwordConfirm: values.passwordConfirm,
          }
        : Object.fromEntries(signupFields.map((field) => [field.key, values[field.key] || null]))
      await backend(base.value + '/signup', { method: 'POST', body })
      if (manager.value) approvalEstimate.value = Date.now() + 60_000
      message.value = manager.value
        ? '가입 신청이 접수되었습니다. 약 1분 후 자동 승인되며 서버 처리에 따라 더 걸릴 수 있습니다.'
        : '회원가입이 완료되었습니다. 로그인해 주세요.'
      clearTimers()
      step.value = 4
    }
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function checkId() {
  error.value = ''
  try {
    const result = await backend(
      base.value + '/check-login-id' + queryString({ loginId: values.loginId }),
    )
    message.value = result.available ? '사용 가능한 아이디입니다.' : '이미 사용 중인 아이디입니다.'
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function resend() {
  refreshTimers()
  if (busy.value || resendSeconds.value > 0) return
  busy.value = true
  error.value = ''
  try {
    const timing = await backend(
      base.value +
        (kind.value === 'reset'
          ? '/password-reset/request'
          : manager.value
            ? '/signup/phone-code/resend'
            : '/phone-code/send'),
      {
        method: 'POST',
        body:
          kind.value === 'reset'
            ? { loginId: values.loginId, phone: values.phone }
            : manager.value
              ? undefined
              : { phone: values.phone },
      },
    )
    issued(timing)
    values.code = ''
    message.value = '인증번호를 다시 발송했습니다.'
    const code = await developmentSmsCode(values.phone || '')
    if (code) { values.code = code; message.value = '개발용 인증번호를 자동으로 입력했습니다.' }
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <component :is="manager?BackendLayout:'div'"
    ><section class="service-workspace auth-service"
      ><h1
        >{{manager?'관리자':'고객'}} {{kind==='login'?'로그인':kind==='signup'?'회원가입':'비밀번호 재설정'}}</h1
      ><p v-if="kind==='signup'" class="required-guide"
        ><span>*</span> 표시는 필수 입력 항목입니다.</p
      ><p v-if="kind!=='login'&&step<4">{{step}} / 3단계</p
      ><p v-if="error" class="service-error" role="alert">{{error}}</p
      ><p v-if="message" class="service-message" role="status">{{message}}</p
      ><form v-if="step<4" :class="{'signup-form':kind==='signup','login-form':kind==='login'}" @invalid.capture="focusInvalid" @submit.prevent="submit"
        ><section v-if="mobilePassSignup&&step===1" class="signup-verification-choice"
          ><h2>휴대전화 본인인증</h2><p>회원가입에 사용할 인증 방식을 선택해 주세요.</p
          ><div role="group" aria-label="회원가입 인증 방식"
            ><button type="button" :aria-pressed="passMode" @click="passMode=true"
              >PASS 인증 <small>시연</small></button
            ><button type="button" :aria-pressed="!passMode" @click="passMode=false"
              >문자 인증</button
            ></div
          ><small v-if="passMode"
            >PASS 연동 전 화면입니다. 실제 가입은 문자 인증을 선택해 주세요.</small
          ></section
        ><section v-for="group in fieldGroups" :key="group.label" class="auth-field-group"
          ><h2 v-if="group.label">{{group.label}}</h2
          ><div class="service-form-fields"
            ><div
              v-for="field in group.fields"
              :key="field.key"
              :class="['auth-field','auth-field-'+field.key,{'inline-field':field.key==='zipcode'||(kind==='signup'&&step===3&&field.key==='loginId')}]"
              ><span class="service-field-label"
                >{{field.label
                }}<small
                  v-if="kind==='signup'&&field.required"
                  class="required-star"
                  aria-label="필수"
                  >*</small
                ></span
              ><NationalitySelect
                v-if="!manager&&kind==='signup'&&field.key==='phone'"
                v-model="app.signupNationality"
                name="customer-signup-nationality"
              /><input v-if="field.key==='phone'&&passDemoVerified" :value="values.phone" type="text" :aria-label="field.label" disabled title="인증된 전화번호는 변경할 수 없습니다."
              /><PhoneInput
                v-else-if="['phone','emergency'].includes(field.key)"
                v-model="values[field.key]"
                :label="field.label"
                :required="field.required"
                :disabled="busy"
                :international="!manager"
                @update:model-value="duplicateMessage[field.key]=''"
              /><BirthDateInput
                v-else-if="field.key==='birthDate'"
                v-model="values[field.key]"
                :required="field.required"
                :disabled="busy"
              /><VerificationCodeInput
                v-else-if="field.key==='code'"
                v-model="values[field.key]"
                :label="field.label"
                :required="field.required"
                :disabled="busy"
              /><EmailInput
                v-else-if="field.key==='email'"
                v-model="values[field.key]"
                :label="field.label"
                :required="field.required"
                :disabled="busy"
              /><PasswordInput
                v-else-if="/password/i.test(field.key)"
                v-model="values[field.key]"
                :match-value="field.key==='passwordConfirm' ? (values.password || '') : field.key==='newPasswordConfirm' ? (values.newPassword || '') : undefined"
                :aria-label="field.label"
                :type="/password/i.test(field.key)?'password':field.type||'text'"
                :required="field.required"
                :autocomplete="field.key==='password'?(kind==='login'?'current-password':'new-password'):field.key==='detailAddress'?'address-line2':undefined"
                :disabled="busy"
                :maxlength="field.key==='code'?10:100"
                @input="duplicateMessage[field.key]=''"
              /><input
                v-else
                @blur="['phone','emergency'].includes(field.key)&&(values[field.key]=formatPhone(values[field.key]))"
                :inputmode="field.key==='code'?'numeric':['phone','emergency'].includes(field.key)?'tel':undefined"
                v-model="values[field.key]"
                :aria-label="field.label"
                :type="/password/i.test(field.key)?'password':field.type||'text'"
                :required="field.required"
                :autocomplete="field.key==='password'?(kind==='login'?'current-password':'new-password'):field.key==='detailAddress'?'address-line2':undefined"
                :disabled="busy"
                :maxlength="field.key==='code'?10:100"
                @input="duplicateMessage[field.key]=''"
              /><button
                v-if="kind==='signup'&&step===3&&field.key==='loginId'"
                type="button"
                :disabled="busy||!values.loginId"
                @click="checkId"
                >중복 확인</button
              ><PostcodeSearch
                v-if="field.key==='zipcode'"
                :disabled="busy"
                @select="address=>{values.zipcode=address.zipcode;values.roadAddress=address.roadAddress;values.detailAddress=''}"
              /><template v-if="kind==='signup'&&!manager&&['phone','email'].includes(field.key)"
                ><button
                  type="button"
                  class="duplicate-check"
                  :disabled="busy||!!checking||!values[field.key]"
                  @click="checkDuplicate(field.key)"
                  >{{checking===field.key?'확인 중…':'중복 확인'}}</button
                ><small
                  v-if="duplicateMessage[field.key]"
                  role="status"
                  >{{duplicateMessage[field.key]}}</small
                ></template
              ></div
            ></div
          ></section
        ><label v-if="kind==='login'" class="remember-login-id"><input v-model="rememberId" aria-label="아이디 저장" type="checkbox" :disabled="busy" @change="forgetId" />아이디 저장</label
        ><section
          v-if="kind!=='login'&&step===2"
          class="verification-timing"
          aria-label="휴대전화 인증 시간"
          ><p v-if="maskedPhone">{{maskedPhone}} 번호로 발송했습니다.</p
          ><p v-if="codeSeconds>0"
            >인증번호 입력 남은 시간 <strong role="timer" aria-live="off">{{formatRemaining(codeSeconds)}}</strong></p
          ><p v-else role="status" class="service-error"
            >인증번호가 만료되었습니다. 인증번호를 다시 받아 주세요.</p
          ><button type="button" :disabled="busy||resendSeconds>0" @click="resend"
            >인증번호 다시 받기</button
          ></section
        ><section
          v-if="step===3&&(kind==='reset'||(kind==='signup'&&verifiedSeconds===0))"
          class="verification-timing"
          ><p v-if="verifiedSeconds>0"
            >{{kind==='signup'?'가입 완료':'비밀번호 변경'}}까지 남은 시간 <strong role="timer" aria-live="off">{{formatRemaining(verifiedSeconds)}}</strong></p
          ><template v-else
            ><p role="status" class="service-error"
              >인증 유효 시간이 만료되었습니다. 다시 인증해 주세요.</p
            ><button type="button" :disabled="busy" @click="restartVerification"
              >다시 인증하기</button
            ></template
          ></section
        ><div class="auth-buttons"
          ><button
            type="submit"
            :disabled="busy||timerExpired"
            >{{busy?'처리 중…':mobilePassSignup&&passMode?(step===1?'PASS로 본인인증':step===3?'가입 신청':'다음'):kind==='login'?'로그인':step===3?(kind==='signup'?'가입 신청':'비밀번호 변경'):'다음'}}</button
          ></div
        ></form
      ><PassSignupVerification
        v-if="mobilePassSignup"
        v-model="passOpen"
        :phone="values.phone||''"
        :name="values.name"
        :birth-date="values.birthDate"
        @complete="completePass"
      /><section v-if="manager&&step===4" class="verification-timing"
        ><p
          >자동 승인 예상 대기 <DeadlineTimer
            :deadline="approvalEstimate"
            expired-label="승인 예상 시간이 지났습니다. 로그인으로 승인 여부를 확인해 주세요." /></p
        ><small>서버가 15초 간격으로 승인 대상을 확인합니다.</small></section
      ><nav class="auth-links"
        ><RouterLink :to="authLink('login')">로그인</RouterLink
        ><RouterLink v-if="kind!=='signup'" :to="authLink('signup')">회원가입</RouterLink
        ><RouterLink
          v-if="!manager"
          :to="route.path.startsWith('/customer-app/')?'/customer-app/find-id':'/find-id'"
          >아이디 찾기</RouterLink
        ><RouterLink :to="authLink('reset')">비밀번호 찾기</RouterLink></nav
      ></section
    ></component
  >
</template>
<style scoped src="../styles/pages/service-auth.css"></style>

<style scoped src="../styles/pages/service-auth-2.css"></style>

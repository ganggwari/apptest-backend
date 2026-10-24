<script setup lang="ts">
import { ref } from 'vue'
defineProps<{ required?: boolean; disabled?: boolean; label?: string }>()
const value = defineModel<string>({ default: '' }),
  choice = ref('')
function select() {
  if (choice.value) value.value = (value.value?.split('@')[0] || '') + '@' + choice.value
}
</script>
<template>
  <span class="email-input">
    <input
      v-model="value"
      type="email"
      :aria-label="label||'이메일'"
      autocomplete="email"
      :required="required"
      :disabled="disabled"
      placeholder="example@domain.com"
    />
    <select v-model="choice" aria-label="이메일 도메인 선택" :disabled="disabled" @change="select">
      <option value="">직접 입력</option>
      <option
        v-for="item in ['naver.com','gmail.com','daum.net','hanmail.net','nate.com']"
        :key="item"
        :value="item"
      >
        {{item}}
      </option>
    </select>
  </span>
</template>
<style scoped src="../styles/components/email-input.css"></style>

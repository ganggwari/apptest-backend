<script setup lang="ts">
import { computed, ref, useId } from 'vue'
defineOptions({ inheritAttrs: false })
const value = defineModel<string>()
const props = defineProps<{ matchValue?: string }>()
const errorId = useId()
const mismatch = computed(() => props.matchValue !== undefined && !!value.value && value.value !== props.matchValue)
const visible = ref(false)
</script>

<template>
  <span class="password-input">
    <input v-bind="$attrs" v-model="value" :type="visible?'text':'password'" :aria-invalid="mismatch || undefined" :aria-describedby="mismatch ? [$attrs['aria-describedby'], errorId].filter(Boolean).join(' ') : $attrs['aria-describedby'] as string | undefined" />
  <button
      class="password-visibility"
      type="button"
      :disabled="!!$attrs.disabled"
      :aria-label="visible?'비밀번호 숨기기':'비밀번호 보기'"
      :aria-pressed="visible"
      @click="visible=!visible"
    >
      <UiIcon :name="visible?'eye':'eyeOff'" />
    </button>
  </span>
  <small v-if="mismatch" :id="errorId" class="password-match-error" role="alert">비밀번호가 일치하지 않습니다.</small>
</template>

<style src="../styles/components/password-input.css"></style>

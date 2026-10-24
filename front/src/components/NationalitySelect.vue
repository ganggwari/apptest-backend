<script setup lang="ts">
import '../styles/nationality-select.css'
const model = defineModel<'domestic' | 'foreign'>({ default: 'domestic' })
const props = defineProps<{ name: string }>()
</script>
<template>
  <div class="nationality-select" role="group" aria-label="내국인·외국인 선택">
    <label class="nationality-option">
      <input v-model="model" :name="props.name" type="radio" value="domestic" />
      <span>내국인</span>
    </label>
    <label class="nationality-option">
      <input v-model="model" :name="props.name" type="radio" value="foreign" />
      <span>외국인</span>
    </label>
  </div>
</template>

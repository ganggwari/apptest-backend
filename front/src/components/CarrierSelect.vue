<script setup lang="ts">
const model = defineModel<string>({ default: '' })
const carriers = [
  { value: 'SKT', label: 'SKT' },
  { value: 'KT', label: 'KT' },
  { value: 'LGU', label: 'LG U+' },
  { value: 'SKT_MVNO', label: 'SKT 알뜰폰' },
  { value: 'KT_MVNO', label: 'KT 알뜰폰' },
  { value: 'LGU_MVNO', label: 'LG U+ 알뜰폰' },
]
</script>
<template>
  <label class="carrier-select-label">통신사<select v-model="model" class="carrier-select" aria-label="통신사 선택" required>
      <option value="" disabled>통신사를 선택해 주세요</option>
      <option v-for="carrier in carriers" :key="carrier.value" :value="carrier.value">
        {{carrier.label}}
      </option>
    </select>
  </label>
</template>

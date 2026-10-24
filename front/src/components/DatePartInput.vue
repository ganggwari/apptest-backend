<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue'
defineOptions({ inheritAttrs: false })
const props = defineProps<{ options: string[]; disabled?: boolean }>()
const value = defineModel<string>({ default: '' })
const id = useId(), open = ref(false), index = ref(0), input = ref<HTMLInputElement>(), list = ref<HTMLElement>()
function show() {
  if (props.disabled) return
  index.value = Math.max(0, props.options.indexOf(value.value))
  open.value = true
  void nextTick(() => list.value?.children[index.value]?.scrollIntoView({ block:'nearest' }))
}
function choose(option: string) { value.value = option; open.value = false }
function toggle() { const wasOpen = open.value; input.value?.focus(); if (wasOpen) open.value = false; else show() }
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape' || event.key === 'Tab') { open.value = false; return }
  if (event.key === 'Enter' && open.value) { event.preventDefault(); const option = props.options[index.value]; if (option) choose(option); return }
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
  event.preventDefault()
  if (!open.value) { show(); return }
  index.value = Math.max(0, Math.min(props.options.length - 1, index.value + (event.key === 'ArrowDown' ? 1 : -1)))
  void nextTick(() => list.value?.children[index.value]?.scrollIntoView({ block:'nearest' }))
}
watch(() => props.disabled, disabled => { if (disabled) open.value = false })
</script>
<template>
  <span class="date-part-input" @focusout="event => { if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node)) open=false }">
    <input ref="input" v-bind="$attrs" v-model="value" autocomplete="off" :disabled="disabled" role="combobox" aria-autocomplete="none" :aria-expanded="open" :aria-controls="id" :aria-activedescendant="open ? `${id}-${index}` : undefined" @focus="show" @click="show" @input="show" @keydown="keydown" />
    <button type="button" class="date-part-toggle" tabindex="-1" :disabled="disabled" aria-label="목록 열기" @mousedown.prevent @click="toggle"><svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true"><path d="m4 6 4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
    <span v-show="open" :id="id" ref="list" role="listbox" :aria-label="`${$attrs['aria-label']} 선택`" class="date-part-list">
      <span v-for="(option, position) in options" :id="`${id}-${position}`" :key="option" role="option" :aria-selected="value===option" :class="{'date-part-active':index===position}" @mousedown.prevent @click="choose(option)">{{option}}</span>
    </span>
  </span>
</template>
<style scoped src="../styles/components/date-part-input.css"></style>

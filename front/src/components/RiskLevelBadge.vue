<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ value?: unknown }>()
const levels: Record<string, [string, string]> = {
  SAFE: ['안전', 'safe'],
  NORMAL: ['안전', 'safe'],
  WATCH: ['관심', 'watch'],
  CAUTION: ['주의', 'caution'],
  WARNING: ['주의', 'caution'],
  ALERT: ['경계', 'alert'],
  SEVERE: ['심각', 'severe'],
  DANGER: ['위험', 'alert'],
  '정상': ['안전', 'safe'],
  '안전': ['안전', 'safe'],
  '관심': ['관심', 'watch'],
  '주의': ['주의', 'caution'],
  '경고': ['주의', 'caution'],
  '경계': ['경계', 'alert'],
  '심각': ['심각', 'severe'],
  '위험': ['위험', 'alert'],
}
const level = computed(
  () => levels[String(props.value || '').toUpperCase()] || ['미확인', 'unknown'],
)
</script>
<template>
  <span class="risk-level-badge" :class="'risk-level-badge--'+level[1]">{{level[0]}}</span>
</template>
<style scoped src="../styles/components/risk-level-badge.css"></style>

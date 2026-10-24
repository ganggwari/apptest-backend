<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
const props = defineProps<{ message: string }>(),
  visible = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
watch(
  () => props.message,
  (value) => {
    clearTimeout(timer)
    visible.value = !!value
    if (value) timer = setTimeout(() => (visible.value = false), 5000)
  },
)
onBeforeUnmount(() => clearTimeout(timer))
</script>
<template>
  <Teleport to="body"
    ><div v-if="visible" class="feedback-toast" role="status"
      ><span aria-hidden="true">✓</span>{{message
      }}<button class="ui-icon-button" type="button" aria-label="알림 닫기" @click="visible=false">
          <UiIcon name="close" />
        </button></div
    ></Teleport
  >
</template>
<style scoped src="../styles/components/feedback-toast.css"></style>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ServiceAccount from './service-account.vue'
import ServiceResource from './backend-resource.vue'
const route = useRoute(),
  resource = computed(() => String(route.params.section) === 'inquiries')
</script>
<template>
  <ServiceResource v-if="resource" />
  <ServiceAccount v-else />
</template>

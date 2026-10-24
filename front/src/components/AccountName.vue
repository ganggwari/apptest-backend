<script setup lang="ts">
import { useApp } from '../stores/app'
const app = useApp()
</script>
<template><RouterLink :to="app.account?.role==='customer'?'/my-home':'/admin-profile'">{{app.account?.name||'관리자'}}님</RouterLink></template>

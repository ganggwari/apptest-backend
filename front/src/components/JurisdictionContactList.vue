<script setup lang="ts">
import { jurisdictionContacts } from '../data/jurisdictionContacts'
const phoneLink = (phone: string) => 'tel:' + phone.replace(/[^\d+]/g, '')
</script>
<template>
  <ul class="jurisdiction-phone-list" aria-label="관할 담당자 전화번호 목록">
    <li v-for="contact in jurisdictionContacts" :key="contact.id">
      <div><strong>{{contact.name}}</strong><small>{{contact.organization}}</small></div>
      <span v-if="contact.placeholder" class="contact-placeholder">{{contact.phone}}<small>임시 번호</small></span>
      <a v-else-if="contact.phone" :href="phoneLink(contact.phone)" :aria-label="contact.name+' '+contact.phone+' 전화 걸기'">{{contact.phone}}</a>
      <span v-else class="contact-unregistered">연락처 미등록</span>
    </li>
  </ul>
</template>
<style scoped src="../styles/components/jurisdiction-contact-list.css"></style>

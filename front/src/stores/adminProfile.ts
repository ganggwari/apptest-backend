import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useApp } from './app'
export const busanDistricts = [
  '중구',
  '서구',
  '동구',
  '영도구',
  '부산진구',
  '동래구',
  '남구',
  '북구',
  '해운대구',
  '사하구',
  '금정구',
  '강서구',
  '연제구',
  '수영구',
  '사상구',
  '기장군',
]
const defaults = { name: '○○○', district: '동구', department: '', email: '', phone: '' }
export const useAdminProfile = defineStore('adminProfile', () => {
  const app = useApp()
  const savedProfile = ref({ ...defaults })
  try {
    const value = JSON.parse(localStorage.getItem('routel-admin-profile') || 'null')
    if (value && busanDistricts.includes(value.district))
      savedProfile.value = {
        ...defaults,
        ...Object.fromEntries(
          Object.keys(defaults)
            .filter((key) => typeof value[key] === 'string')
            .map((key) => [key, value[key]]),
        ),
      }
  } catch {}
  const profile = computed(() => ({
    ...savedProfile.value,
    name: app.account?.role === 'REGIONAL_ADMIN' ? app.account.name : '관리자',
  }))
  const region = computed(() => app.account?.role === 'REGIONAL_ADMIN'
    ? app.account.regions?.map(region => region.name).join(' · ') || '담당 지역 미지정'
    : '담당 지역 미지정')
  function save(value: typeof defaults) {
    localStorage.setItem('routel-admin-profile', JSON.stringify(value))
    savedProfile.value = { ...value }
  }
  return { profile, region, save }
})

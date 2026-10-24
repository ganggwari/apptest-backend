<script setup lang="ts">
import type { Field } from '../data/backendResources'
import PostcodeSearch from './PostcodeSearch.vue'
import PhoneInput from './PhoneInput.vue'
import { formatPhone } from '../utils/phone'
defineProps<{
  fields: Field[]
  values: Record<string, any>
  choices: Record<string, { value: string; label: string }[]>
  disabled?: boolean
}>()
</script>
<template>
  <div class="service-form-fields"
    ><p v-if="fields.some(field=>field.required)" class="required-guide"
      ><span>*</span> 표시는 필수 입력 항목입니다.</p
    ><label
      v-for="field in fields"
      :key="field.key"
      :class="[{wide:field.type==='textarea'||field.type==='lines', 'postcode-field':field.key==='zipcode','checkbox-field':field.type==='checkbox'}, 'field-type-'+(field.type||'text')]"
      ><span class="service-field-label"
        >{{field.label
        }}<small v-if="field.required" class="required-label" aria-label="필수">*</small></span
      ><textarea
        v-if="field.type==='textarea'||field.type==='lines'"
        v-model="values[field.key]"
        :aria-label="field.label"
        :required="field.required"
        :disabled="disabled"
        maxlength="10000"
      ></textarea
      ><select
        v-else-if="field.type==='select'||field.type==='multi'"
        v-model="values[field.key]"
        :aria-label="field.label"
        :required="field.required"
        :multiple="field.type==='multi'"
        :disabled="disabled"
        ><option v-if="field.type!=='multi'" value="">선택</option
        ><option
          v-for="option in choices[field.key]||field.options?.map(([value,label])=>({value,label}))||[]"
          :key="option.value"
          :value="option.value"
          >{{option.label}}</option
        ></select
      ><input
        v-else-if="field.type==='checkbox'"
        v-model="values[field.key]"
        :aria-label="field.label"
        type="checkbox"
        :disabled="disabled" /><PhoneInput
        v-else-if="['phone','emergency'].includes(field.key)"
        v-model="values[field.key]"
        :label="field.label"
        :required="field.required"
        :disabled="disabled" /><input
        v-else
        @blur="['phone','emergency'].includes(field.key)&&(values[field.key]=formatPhone(values[field.key]))"
        :inputmode="['phone','emergency'].includes(field.key)?'tel':undefined"
        v-model="values[field.key]"
        :aria-label="field.label"
        :type="field.type||'text'"
        :required="field.required"
        :disabled="disabled"
        :step="field.type==='number'?'any':undefined"
        :min="field.type==='number'?0:undefined"
        maxlength="500"
        :autocomplete="field.key==='detailAddress'?'address-line2':undefined" /><PostcodeSearch
        v-if="field.key==='zipcode'"
        :disabled="disabled"
        @select="address=>{values.zipcode=address.zipcode;values.roadAddress=address.roadAddress;values.detailAddress=''}" /></label
  ></div>
</template>

<style scoped src="../styles/components/service-fields.css"></style>

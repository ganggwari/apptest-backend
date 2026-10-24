<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useApp } from '../stores/app'
import CustomerAppPage from '../components/CustomerAppPage.vue'
const route = useRoute(),
  app = useApp(),
  query = ref(''),
  isFaq = computed(() => route.path.endsWith('/faq'))
const findId = computed(() => route.path.endsWith('/find-id')),
  phone = ref(''),
  message = ref('')
const faqs = [
  {
    question: '차량은 어떻게 등록하나요?',
    answer:
      '로그인 후 앱 마이페이지의 내 차량에서 등록할 수 있습니다. 실제 저장은 서버 연동 후 제공됩니다.',
  },
  {
    question: '알림이 오지 않아요',
    answer: '앱 마이페이지의 알림 수신 설정과 휴대폰의 앱 알림 권한을 확인해 주세요.',
  },
  {
    question: '휴대폰 번호를 바꾸고 싶어요',
    answer:
      '앱 마이페이지의 회원정보 수정에서 변경합니다. 본인 인증은 인증 서비스 연결 후 제공됩니다.',
  },
]
const questions = computed(() =>
  [...faqs, ...app.faqs].filter((item) => `${item.question} ${item.answer}`.includes(query.value)),
)
const notices = computed(() =>
  app.notices.filter((item) => `${item.title} ${item.body}`.includes(query.value)),
)
</script>
<template>
  <CustomerAppPage :title="findId?'아이디 찾기':isFaq?'FAQ':'공지사항'">
    <form
      v-if="findId"
      class="app-account-form"
      @submit.prevent="message='휴대폰 인증 서비스 연결 후 아이디를 확인할 수 있습니다.'"
    >
      <label>가입한 휴대폰 번호<input v-model="phone" type="tel" autocomplete="tel" required />
      </label>
      <button class="app-account-primary">아이디 확인</button>
      <p role="status">{{message}}</p>
    </form>
    <template v-else>
      <input
        v-model.trim="query"
        type="search"
        :aria-label="isFaq?'FAQ 검색':'공지사항 검색'"
        placeholder="검색어를 입력하세요"
      />
      <template v-if="isFaq">
        <details v-for="item in questions" :key="item.question">
          <summary>{{item.question}}</summary>
          <p>{{item.answer}}</p>
        </details>
        <p v-if="!questions.length">검색 결과가 없습니다.</p>
      </template>
      <template v-else>
        <details v-for="item in notices" :key="item.id">
          <summary>{{item.title}}</summary>
          <p>{{item.body}}</p>
        </details>
        <p v-if="!notices.length">
          {{query?'검색 결과가 없습니다.':'등록된 공지사항이 없습니다.'}}
        </p>
      </template>
    </template>
  </CustomerAppPage>
</template>

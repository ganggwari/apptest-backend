import { backend, queryString } from './backend'

// 개발 서버가 명시적으로 공개한 테스트 코드만 사용한다. 운영 빌드에서는 요청하지 않는다.
export async function developmentSmsCode(phone: string): Promise<string> {
  if (!import.meta.env.DEV || !phone) return ''
  await new Promise<void>(resolve => setTimeout(resolve, 2000))
  try {
    const data = await backend<{ phone: string; code: string }>('/api/dev/sms/last' + queryString({ phone }))
    return /^\d{6}$/.test(data?.code || '') ? data.code : ''
  } catch {
    return ''
  }
}

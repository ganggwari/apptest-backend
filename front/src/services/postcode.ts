let loading: Promise<any> | undefined
export type PostalAddress = { zipcode: string; roadAddress: string }
export function loadPostcode(): Promise<any> {
  if (window.kakao?.Postcode) return Promise.resolve(window.kakao.Postcode)
  if (loading) return loading
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'
    script.async = true
    const timer = window.setTimeout(() => fail(), 12000)
    function fail() {
      clearTimeout(timer)
      script.remove()
      loading = undefined
      reject(
        new Error('주소 검색을 불러오지 못했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.'),
      )
    }
    script.onerror = fail
    script.onload = () => {
      clearTimeout(timer)
      if (window.kakao?.Postcode) resolve(window.kakao.Postcode)
      else fail()
    }
    document.head.append(script)
  })
  return loading
}

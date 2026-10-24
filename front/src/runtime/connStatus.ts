// 실시간 연결 상태(SSE / WebSocket)를 화면 상단에 띄우는 작은 표시줄. 시연·테스트용.
// ?nodemo=1 이면 숨김. SSE 상태는 useBackendEvents.ts 가 report 로 알려준다.
type Sse = 'idle' | 'connecting' | 'open' | 'lost'
type Ws = 'idle' | 'connecting' | 'open' | 'closed' | 'unsupported'
const st = { sse: 'idle' as Sse, lastAt: 0, lastName: '', retry: 0, ws: 'idle' as Ws, wsAt: 0, url: '' }
const listeners = new Set<() => void>()
const poke = () => listeners.forEach((f) => f())

export function reportSse(state: Sse, extra?: { event?: string; retry?: number; url?: string }) {
  st.sse = state
  if (extra?.url) st.url = extra.url
  if (typeof extra?.retry === 'number') st.retry = extra.retry
  if (extra?.event) {
    st.lastAt = Date.now()
    st.lastName = extra.event
  }
  poke()
}

// WebSocket(내비 전용 /ws/navigation) 확인용. 앱 기능은 SSE 로 동작하며 WS 는 선택 항목이다.
let sock: WebSocket | undefined
export function probeWs() {
  try {
    if (typeof WebSocket === 'undefined') {
      st.ws = 'unsupported'
      return poke()
    }
    sock?.close()
    st.ws = 'connecting'
    poke()
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const s = new WebSocket(`${proto}//${location.host}/ws/navigation`)
    sock = s
    window.setTimeout(() => {
      if (sock === s && st.ws === 'connecting') ((st.ws = 'closed'), s.close(), poke())
    }, 6000)
    s.onopen = () => ((st.ws = 'open'), (st.wsAt = Date.now()), poke())
    s.onmessage = () => ((st.wsAt = Date.now()), poke())
    s.onclose = () => (sock === s && ((st.ws = 'closed'), poke()))
    s.onerror = () => (sock === s && ((st.ws = 'closed'), poke()))
  } catch {
    st.ws = 'closed'
    poke()
  }
}

const ago = (t: number) => {
  if (!t) return '-'
  const s = Math.max(0, Math.round((Date.now() - t) / 1000))
  return s < 60 ? `${s}초 전` : `${Math.floor(s / 60)}분 전`
}

export function installConnStatus() {
  try {
    if (new URLSearchParams(location.search).has('nodemo')) return
    if (document.getElementById('conn-status')) return
    const bar = document.createElement('div')
    bar.id = 'conn-status'
    bar.style.cssText =
      'position:fixed;top:0;left:50%;transform:translateX(-50%);z-index:2147483001;display:flex;gap:8px;align-items:center;' +
      'padding:3px 10px;border-radius:0 0 10px 10px;font:11px/1.5 system-ui,sans-serif;color:#fff;box-shadow:0 2px 6px #0004;' +
      'max-width:96vw;white-space:nowrap;overflow:hidden;cursor:pointer;opacity:.92'
    const dot = (c: string) => `<i style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${c};margin-right:4px"></i>`
    let compact = false
    const render = () => {
      const stale = st.lastAt && Date.now() - st.lastAt > 40000
      let sseTxt: string, col: string, bg: string
      if (st.sse === 'open' && !stale) {
        sseTxt = `SSE 연결됨 · 마지막 수신 ${ago(st.lastAt)}${st.lastName ? ` (${st.lastName})` : ''}`
        col = '#4ade80'
        bg = '#14532d'
      } else if (st.sse === 'open' || st.sse === 'connecting') {
        sseTxt = st.sse === 'connecting' ? `SSE 연결 중…${st.retry ? ` (재시도 ${st.retry})` : ''}` : `SSE 수신 끊김? 마지막 ${ago(st.lastAt)}`
        col = '#fbbf24'
        bg = '#78350f'
      } else if (st.sse === 'lost') {
        sseTxt = `SSE 끊김 · 재연결 중${st.retry ? ` (${st.retry}회)` : ''}`
        col = '#f87171'
        bg = '#7f1d1d'
      } else {
        sseTxt = 'SSE 대기'
        col = '#9ca3af'
        bg = '#374151'
      }
      const wsMap: Record<Ws, [string, string]> = {
        idle: ['WS 미확인', '#9ca3af'],
        connecting: ['WS 연결 중', '#fbbf24'],
        open: ['WS 연결됨', '#4ade80'],
        closed: ['WS 안 됨(내비 전용·선택)', '#fbbf24'],
        unsupported: ['WS 미지원 브라우저', '#9ca3af'],
      }
      const [wsTxt, wsCol] = wsMap[st.ws]
      bar.style.background = bg
      bar.innerHTML = compact
        ? dot(col) + (st.sse === 'open' && !stale ? 'SSE' : 'SSE!')
        : `<span>${dot(col)}${sseTxt}</span><span style="opacity:.5">|</span><span>${dot(wsCol)}${wsTxt}</span>`
    }
    bar.onclick = () => ((compact = !compact), render())
    bar.ondblclick = () => probeWs()
    listeners.add(render)
    setInterval(render, 1000)
    render()
    document.body.appendChild(bar)
  } catch {
    /* 표시줄 실패가 앱을 막지 않게 */
  }
}

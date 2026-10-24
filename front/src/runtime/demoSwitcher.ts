// 테스트용 계정 전환 위젯: 화면 구석의 작은 버튼 -> 고객/관리자 딸깍 로그인. ?nodemo=1 이면 숨김.
// 시드 계정의 비밀번호는 아이디와 같다. 로그인 후 전체 새로고침으로 세션을 다시 읽는다.
const MANAGERS: [string, string][] = [
  ['manager03', '동구·중구 등 (초량·북항·충장)'],
  ['manager01', '사상구'],
  ['manager02', '해운대·금정·연제·수영'],
  ['manager04', '북구·강서'],
  ['manager05', '부산진·동래·기장'],
]
const CUSTOMERS = ['customer01', 'customer02', 'customer03']

async function login(kind: 'managers' | 'customers', id: string): Promise<boolean> {
  try {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => null)
    const r = await fetch(`/api/${kind}/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginId: id, password: id }),
    })
    return r.ok
  } catch {
    return false
  }
}

async function whoAmI(): Promise<string> {
  for (const [k, n] of [['managers', '관리자'], ['customers', '고객']] as const) {
    try {
      const r = await fetch(`/api/${k}/me`, { credentials: 'include' })
      if (r.ok) {
        const j = await r.json()
        return `${n} · ${j?.data?.loginId || j?.data?.name || ''}`
      }
    } catch {
      /* ignore */
    }
  }
  return '로그인 안 됨'
}

export function installDemoSwitcher() {
  try {
    if (new URLSearchParams(location.search).has('nodemo')) return
    if (document.getElementById('demo-switcher')) return
    const root = document.createElement('div')
    root.id = 'demo-switcher'
    root.style.cssText = 'position:fixed;left:8px;bottom:84px;z-index:2147483000;font:13px system-ui,sans-serif'
    const btn = document.createElement('button')
    btn.textContent = '⇄ 계정'
    btn.style.cssText =
      'border:0;border-radius:99px;padding:8px 12px;background:#173d62;color:#fff;font-weight:700;opacity:.82;box-shadow:0 2px 8px #0004'
    const panel = document.createElement('div')
    panel.style.cssText =
      'display:none;margin-bottom:6px;background:#fff;color:#1f2937;border-radius:12px;padding:10px;box-shadow:0 4px 16px #0004;width:230px;max-height:60vh;overflow:auto'
    const mk = (label: string, fn: () => void, bg = '#0f4c81') => {
      const b = document.createElement('button')
      b.textContent = label
      b.style.cssText = `display:block;width:100%;text-align:left;margin:4px 0;border:0;border-radius:8px;padding:8px 10px;background:${bg};color:#fff;font-weight:600`
      b.onclick = fn
      return b
    }
    const go = async (kind: 'managers' | 'customers', id: string, dest: string) => {
      status.textContent = '로그인 중…'
      if (await login(kind, id)) location.href = dest
      else status.textContent = '로그인 실패(시드 DB·서버 확인)'
    }
    const status = document.createElement('div')
    status.style.cssText = 'font-size:12px;color:#6b7280;margin-bottom:6px'
    status.textContent = '확인 중…'
    panel.appendChild(status)
    const h1 = document.createElement('div')
    h1.textContent = '고객 앱'
    h1.style.cssText = 'font-weight:800;margin-top:4px'
    panel.appendChild(h1)
    CUSTOMERS.forEach((id) => panel.appendChild(mk(id, () => go('customers', id, '/customer-app/screen-1'), '#2e9e5b')))
    const h2 = document.createElement('div')
    h2.textContent = '관리자 앱'
    h2.style.cssText = 'font-weight:800;margin-top:8px'
    panel.appendChild(h2)
    MANAGERS.forEach(([id, d]) => panel.appendChild(mk(`${id} — ${d}`, () => go('managers', id, '/admin-app/screen-1'))))
    const h3 = document.createElement('div')
    h3.textContent = '지도 엔진'
    h3.style.cssText = 'font-weight:800;margin-top:8px'
    panel.appendChild(h3)
    const setMap = (v: string) => {
      try {
        v === 'auto' ? localStorage.removeItem('puf-map') : localStorage.setItem('puf-map', v)
      } catch {
        /* ignore */
      }
      location.reload()
    }
    panel.appendChild(mk('자동 (카카오 → 안 되면 OSM)', () => setMap('auto'), '#6b7280'))
    panel.appendChild(mk('OSM(OpenStreetMap) 강제', () => setMap('osm'), '#6b7280'))
    panel.appendChild(mk('카카오만', () => setMap('kakao'), '#6b7280'))
    panel.appendChild(
      mk(
        '로그아웃',
        async () => {
          await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => null)
          location.href = '/'
        },
        '#6b7280',
      ),
    )
    btn.onclick = async () => {
      const open = panel.style.display === 'none'
      panel.style.display = open ? 'block' : 'none'
      if (open) status.textContent = '현재: ' + (await whoAmI()) + ' · 지도: ' + (((window as any).__mapEngine as string) || '미사용')
    }
    root.append(panel, btn)
    document.body.appendChild(root)
  } catch {
    /* 위젯 실패가 앱을 막지 않게 */
  }
}

// 새 알림이 왔을 때 화면이 열려 있는 동안 내는 소리 + 진동 (앱/PWA 공통).
// 화면이 닫혀 있을 때의 소리·진동은 OS 알림(푸시)이 담당한다.
const KEY = 'puf-alert-cue'
let ctx: AudioContext | undefined
let unlocked = false

function unlock() {
  if (unlocked) return
  try {
    const AC = window.AudioContext || (window as any).webkitAudioContext
    if (!AC) return
    ctx = ctx || new AC()
    void ctx.resume()
    unlocked = true
  } catch {
    /* 소리를 못 내는 환경 */
  }
}
if (typeof window !== 'undefined')
  for (const ev of ['pointerdown', 'keydown', 'touchstart'])
    window.addEventListener(ev, unlock, { once: false, passive: true })

export function alertCueEnabled(): boolean {
  try {
    return localStorage.getItem(KEY) !== 'off'
  } catch {
    return true
  }
}
export function setAlertCueEnabled(on: boolean) {
  try {
    localStorage.setItem(KEY, on ? 'on' : 'off')
  } catch {
    /* ignore */
  }
}

function beep(at: number, freq: number, dur: number) {
  if (!ctx) return
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = 'sine'
  osc.frequency.value = freq
  gain.gain.setValueAtTime(0.0001, at)
  gain.gain.exponentialRampToValueAtTime(0.35, at + 0.02)
  gain.gain.exponentialRampToValueAtTime(0.0001, at + dur)
  osc.connect(gain).connect(ctx.destination)
  osc.start(at)
  osc.stop(at + dur + 0.02)
}

/** 띠-띠-띠 소리와 진동. 사용자가 화면을 한 번이라도 만진 뒤부터 소리가 난다(브라우저 정책). */
export function playAlertCue() {
  if (!alertCueEnabled()) return
  try {
    navigator.vibrate?.([300, 120, 300, 120, 600])
  } catch {
    /* 진동 미지원 */
  }
  try {
    if (!ctx || ctx.state !== 'running') return
    const t = ctx.currentTime
    beep(t, 880, 0.18)
    beep(t + 0.26, 880, 0.18)
    beep(t + 0.52, 1175, 0.32)
  } catch {
    /* 소리 미지원 */
  }
}

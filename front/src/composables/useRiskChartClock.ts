import { onBeforeUnmount, ref } from 'vue'
// Local display clock only: no API polling and no synthetic measured samples.
export function useRiskChartClock() {
  const now = ref(Date.now()),
    timer = setInterval(() => {
      now.value = Date.now()
    }, 10000)
  onBeforeUnmount(() => clearInterval(timer))
  return now
}

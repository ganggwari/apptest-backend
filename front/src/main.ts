import { registerPwa } from './runtime/pwa'
import { installDemoSwitcher } from './runtime/demoSwitcher'
import { installConnStatus } from './runtime/connStatus'
import { installErrorVisibility } from './runtime/errorVisibility'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import PasswordInput from './components/PasswordInput.vue'
import UiIcon from './components/UiIcon.vue'
import AccountName from './components/AccountName.vue'
import router from './router'
import './styles/shared.css'
import './styles/meeting-layout.css'
import './styles/mypage.css'
import './styles/customer-navigation.css'
import './styles/full-width.css'
import './styles/responsive-fixes.css'
import './styles/admin-controls.css'
import './styles/dark-mode.css'
import './styles/admin-header.css'
import './styles/customer-qa.css'
import './styles/customer-responsive.css'
import './styles/customer-map-viewport.css'
import './styles/admin-restored.css'
import './styles/app-restored.css'
import './styles/ui-icons.css'
import './styles/visual-consistency.css'
import './styles/mobile-layout.css'
import './styles/theme-switch.css'
import './styles/auth-fields.css'
import './styles/reference-finish.css'
import './styles/customer-premium.css'
import './styles/water-dashboard.css'
import './styles/admin-interface.css'
import './styles/overlay-polish.css'
import './styles/interface-motion.css'
import './styles/interface-refinements.css'
createApp(App)
  .component('PasswordInput', PasswordInput)
  .component('UiIcon', UiIcon)
  .component('AccountName', AccountName)
  .use(createPinia())
  .use(router)
  .mount('#app')

registerPwa()
installDemoSwitcher()
installConnStatus()
const errorRoot = document.getElementById('app')
if (errorRoot) {
  const disposeErrorVisibility = installErrorVisibility(errorRoot)
  import.meta.hot?.dispose(disposeErrorVisibility)
}

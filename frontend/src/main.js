import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import './styles/animation.css'
const app=createApp(App)
// Another tab changing accounts must not leave this tab displaying the old role.
window.addEventListener('storage', event => { if (event.key === 'attendance_token' || event.key === null) location.reload() })
Object.entries(Icons).forEach(([name,component])=>app.component(name,component))
app.use(createPinia()).use(router).use(ElementPlus,{size:'default'}).mount('#app')

import { createApp } from 'vue'
import './assets/main.scss'
import '../migration/edu-theme-minimal.css'
import '../migration/app-router-transition.css'
import './assets/teacher-dark-overrides.scss'
import './assets/student-dark-overrides.scss'
import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
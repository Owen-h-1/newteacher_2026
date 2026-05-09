import { createRouter, createWebHistory } from 'vue-router'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import TeacherLayout from '@/components/layout/TeacherLayout.vue'
import StudentLayout from '@/components/layout/StudentLayout.vue'

// 公共页面
import Login from '@/views/public/Login.vue'
import Register from '@/views/public/Register.vue'
import Welcome from '@/views/public/Welcome.vue'
import CourseSelect from '@/views/public/CourseSelect.vue'
import AIDigitalHuman from '@/views/public/AIDigitalHuman.vue'

// ===== 教师端页面 =====
import Dashboard from '@/views/teacher/Dashboard.vue'
import PPTUpload from '@/views/teacher/PPTUpload.vue'
import AICourseware from '@/views/teacher/AICourseware.vue'
import AITeachingDesign from '@/views/teacher/AITeachingDesign.vue'
import SignIn from '@/views/teacher/SignIn.vue'
import StudentDataBoard from '@/views/teacher/StudentDataBoard.vue'
import StudentManage from '@/views/teacher/StudentManage.vue'
import PublishTask from '@/views/teacher/PublishTask.vue'
import HomeworkManage from '@/views/teacher/HomeworkManage.vue'

// ===== 学生端页面 =====
import LearningHub from '@/views/student/LearningHub.vue'
import DoubaoQA from '@/views/student/DoubaoQA.vue'
import SelfTest from '@/views/student/SelfTest.vue'
import Homework from '@/views/student/Homework.vue'
import HomeworkPractice from '@/views/student/HomeworkPractice.vue'
import JoinClass from '@/views/student/JoinClass.vue'
import SmartPPTSpeaker from '@/views/student/SmartPPTSpeaker.vue'

const routes = [
  // 公共路由（纯净布局）
  { path: '/welcome', component: Welcome },
  { path: '/login', component: Login, meta: { layout: AuthLayout } },
  { path: '/register', component: Register, meta: { layout: AuthLayout } },
  { path: '/course-select', component: CourseSelect, meta: { layout: AuthLayout } },
  { path: '/ai-digital-human', component: AIDigitalHuman }, // 全屏版

  // 教师端（嵌套在 TeacherLayout 下）
  {
    path: '/teacher',
    component: TeacherLayout,
    children: [
      { path: 'dashboard', component: Dashboard },
      { path: 'ppt-upload', component: PPTUpload },
      { path: 'ai-courseware', component: AICourseware },
      { path: 'teaching-design', component: AITeachingDesign },
      { path: 'signin', component: SignIn },
      { path: 'student-data', component: StudentDataBoard },
      { path: 'student-manage', component: StudentManage },
      { path: 'publish', component: PublishTask },
      { path: 'homework-manage', component: HomeworkManage },
      { path: '', redirect: 'dashboard' }
    ]
  },
  // 学生端（嵌套在 StudentLayout 下）
  {
    path: '/student',
    component: StudentLayout,
    children: [
      { path: 'learning', component: LearningHub },
      { path: 'analysis', component: DoubaoQA },
      { path: 'selftest', component: SelfTest },
      { path: 'homework', component: Homework },
      { path: 'homework/:id/practice', component: HomeworkPractice },
      { path: 'join-class', component: JoinClass },
      { path: 'smart-ppt', component: SmartPPTSpeaker },
      { path: '', redirect: 'learning' }
    ]
  },
{ path: '/', redirect: '/welcome' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})


export default router
import { createRouter,createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Layout from '../layout/AppLayout.vue'
const routes=[
 {path:'/login',component:()=>import('../views/LoginView.vue'),meta:{public:true,title:'登录'}},
 {path:'/',component:Layout,redirect:'/dashboard',children:[
  {path:'dashboard',component:()=>import('../views/DashboardView.vue'),meta:{title:'数据看板',roles:['ADMIN','TEACHER','STUDENT']}},
  {path:'users',component:()=>import('../views/UserView.vue'),meta:{title:'账号管理',roles:['ADMIN']}},
  {path:'students',component:()=>import('../views/StudentView.vue'),meta:{title:'学生管理',roles:['ADMIN']}},
  {path:'teachers',component:()=>import('../views/TeacherView.vue'),meta:{title:'教师管理',roles:['ADMIN']}},
  {path:'classes',component:()=>import('../views/ClassView.vue'),meta:{title:'班级管理',roles:['ADMIN']}},
  {path:'courses',component:()=>import('../views/CourseView.vue'),meta:{title:'课程管理',roles:['ADMIN']}},
  {path:'teaching',component:()=>import('../views/TeachingView.vue'),meta:{title:'课程安排',roles:['ADMIN','TEACHER']}},
  {path:'attendance/tasks',component:()=>import('../views/AttendanceTaskView.vue'),meta:{title:'考勤任务',roles:['ADMIN','TEACHER']}},
  {path:'attendance/entry/:id',component:()=>import('../views/AttendanceEntryView.vue'),meta:{title:'考勤录入',roles:['ADMIN','TEACHER'],hidden:true}},
  {path:'attendance/records',component:()=>import('../views/AttendanceRecordView.vue'),meta:{title:'考勤记录',roles:['ADMIN','TEACHER']}},
  {path:'leave/apply',component:()=>import('../views/LeaveApplyView.vue'),meta:{title:'请假申请',roles:['STUDENT']}},
  {path:'leave/approval',component:()=>import('../views/LeaveApprovalView.vue'),meta:{title:'请假审批',roles:['ADMIN','TEACHER']}},
  {path:'my/attendance',component:()=>import('../views/MyAttendanceView.vue'),meta:{title:'我的考勤',roles:['STUDENT']}},
  {path:'student/schedule',component:()=>import('../views/TodayScheduleView.vue'),meta:{title:'课程 / 今日安排',roles:['STUDENT']}},
  {path:'student/notifications',component:()=>import('../views/NotificationCenterView.vue'),meta:{title:'通知中心',roles:['STUDENT']}},
  {path:'student/profile',component:()=>import('../views/ProfileView.vue'),meta:{title:'个人中心',roles:['STUDENT']}}
 ]},
 {path:'/:pathMatch(.*)*',component:()=>import('../views/NotFoundView.vue'),meta:{public:true,title:'404'}}
]
const router=createRouter({history:createWebHistory(),routes})
router.beforeEach(to=>{const auth=useAuthStore();if(to.meta.public)return auth.token&&to.path==='/login'?'/dashboard':true;if(!auth.token)return {path:'/login',query:{redirect:to.fullPath}};if(to.meta.roles&&!to.meta.roles.includes(auth.role))return '/dashboard';return true})
router.afterEach(to=>document.title=`${to.meta.title||'系统'} - 学生考勤管理系统`)
export default router
export { routes }

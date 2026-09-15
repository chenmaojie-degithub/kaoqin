<template>
  <div class="page student-dashboard">
    <section class="student-hero" data-reveal>
      <div><p>{{ greeting }}</p><h1>{{ auth.user?.realName }}，欢迎回来</h1><span>今天是 {{ todayText }}，你的学习与考勤动态都在这里。</span></div>
      <div class="check-state" :class="data.todayStatus?.toLowerCase()">
        <el-icon><CircleCheckFilled /></el-icon>
        <div><small>今日签到状态</small><strong>{{ statusText[data.todayStatus] || (loading ? '加载中' : '暂无安排') }}</strong></div>
      </div>
    </section>

    <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
    <template v-else>
      <AppSkeleton v-if="loading" type="dashboard" class="dashboard-skeleton" />
      <el-row v-else :gutter="16" class="summary-row stagger-list">
        <el-col v-for="metric in metrics" :key="metric.key" :xs="12" :md="6">
          <el-card shadow="never"><span>{{ metric.label }}</span><strong>{{ metric.display.value }}</strong><small>{{ metric.unit }}</small></el-card>
        </el-col>
      </el-row>

      <el-row v-if="!loading" :gutter="16" class="content-row" data-reveal>
        <el-col :xs="24" :lg="14">
          <el-card class="page-card"><template #header><div class="card-head"><b>最近考勤</b><el-button link type="primary" @click="$router.push('/my/attendance')">查看全部 <el-icon><ArrowRight /></el-icon></el-button></div></template>
            <AppState v-if="!data.recentAttendance?.length" title="暂无考勤记录" description="完成课程签到后，最近记录会显示在这里" />
            <TransitionGroup v-else name="list-rise" tag="div">
              <div v-for="row in data.recentAttendance" :key="`${row.taskId}-${row.attendanceDate}`" class="activity">
                <div class="date-box"><b>{{ row.attendanceDate?.slice(8) }}</b><span>{{ row.attendanceDate?.slice(5,7) }}月</span></div>
                <div class="activity-main"><b>{{ row.courseName || '课程考勤' }}</b><span>{{ row.attendanceDate }} · {{ row.remark || '无备注' }}</span></div>
                <StatusBadge :status="row.status" />
              </div>
            </TransitionGroup>
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="10">
          <el-card class="page-card"><template #header><div class="card-head"><b>最新通知</b><el-button link type="primary" @click="$router.push('/student/notifications')">通知中心 <el-icon><ArrowRight /></el-icon></el-button></div></template>
            <AppState v-if="!data.latestNotifications?.length" title="暂无通知" description="老师发布的新通知会显示在这里" />
            <TransitionGroup v-else name="list-rise" tag="div">
              <div v-for="(item,index) in data.latestNotifications" :key="`${item.type}-${item.time}-${index}`" class="notice">
                <span class="notice-dot" :class="item.type.toLowerCase()"></span>
                <div><b>{{ item.title }}</b><p>{{ item.content }}</p><small>{{ formatTime(item.time) }}</small></div>
              </div>
            </TransitionGroup>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../utils/request'
import { useAuthStore } from '../stores/auth'
import { useCountUp } from '../composables/useCountUp'
import { useReveal } from '../composables/useReveal'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'

const auth = useAuthStore()
const data = ref({})
const loading = ref(false)
const error = ref(false)
const now = new Date()
const greeting = computed(() => now.getHours() < 12 ? '早上好' : now.getHours() < 18 ? '下午好' : '晚上好')
const todayText = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(now)
const statusText = { NO_TASK: '今日无考勤', PENDING: '待签到', NORMAL: '已正常签到', LATE: '迟到', EARLY_LEAVE: '早退', LEAVE: '已请假', ABSENT: '缺勤' }
const metricDefinitions = [
  { key: 'todayCourses', label: '今日考勤课程', unit: '门', fallback: 0, decimals: 0 },
  { key: 'monthlyRate', label: '本月出勤率', unit: '%', fallback: 100, decimals: 1 },
  { key: 'pendingLeaves', label: '待处理请假', unit: '条', fallback: 0, decimals: 0 },
  { key: 'latestNotifications', label: '最新通知', unit: '条', fallback: 0, decimals: 0, array: true }
]
const metrics = metricDefinitions.map(metric => ({
  ...metric,
  display: useCountUp(computed(() => {
    const value = data.value[metric.key]
    return metric.array ? (value?.length || 0) : (value ?? metric.fallback)
  }), { duration: 700, decimals: metric.decimals })
}))

const formatTime = value => value ? value.replace('T', ' ').slice(0, 16) : ''
async function load() {
  loading.value = true
  error.value = false
  try {
    data.value = await request.get('/student-portal/dashboard')
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

useReveal()
onMounted(load)
</script>

<style scoped>
.student-dashboard{max-width:1400px;margin:auto}.student-hero{min-height:190px;padding:34px 42px;border-radius:20px;display:flex;align-items:center;justify-content:space-between;color:#fff;background:radial-gradient(circle at 80% 15%,rgba(125,211,252,.35),transparent 28%),linear-gradient(125deg,#102a56,#1d66c2 65%,#438cf3);box-shadow:0 18px 42px rgba(31,91,180,.2);overflow:hidden;position:relative}.student-hero::after{content:"";position:absolute;right:-60px;bottom:-115px;width:260px;height:260px;border:1px solid rgba(255,255,255,.18);border-radius:50%;transition:transform var(--duration-hero) var(--ease-out)}.student-hero:hover::after{transform:scale(1.12) translate(-5px,-5px)}.student-hero>div{position:relative;z-index:1}.student-hero p{margin:0 0 8px;color:#b9dcff}.student-hero h1{margin:0 0 12px;font-size:30px}.student-hero span{color:#d8eaff}.check-state{display:flex;align-items:center;gap:14px;padding:20px 26px;border:1px solid rgba(255,255,255,.28);border-radius:16px;background:rgba(255,255,255,.12);backdrop-filter:blur(12px);transition:transform var(--duration-normal) var(--ease-out),background var(--duration-normal)}.check-state:hover{transform:translateY(-3px);background:rgba(255,255,255,.17)}.check-state .el-icon{font-size:42px}.check-state div{display:flex;flex-direction:column;gap:5px}.check-state strong{font-size:20px}.check-state.pending,.check-state.absent,.check-state.late{color:#ffe18b}.dashboard-skeleton{margin-top:16px}.summary-row{margin-top:16px}.summary-row .el-card{height:112px;border:0;border-radius:14px}.summary-row :deep(.el-card__body){height:100%;display:flex;align-items:baseline;gap:5px;padding:26px}.summary-row span{color:#758197;margin-right:auto}.summary-row strong{font-size:30px;color:#18365f}.summary-row small{color:#8090a7}.content-row{margin-top:16px}.content-row .el-card{min-height:360px}.card-head{display:flex;align-items:center;justify-content:space-between}.card-head .el-button .el-icon{transition:transform var(--duration-fast)}.card-head .el-button:hover .el-icon{transform:translateX(3px)}.activity{display:flex;align-items:center;gap:14px;padding:13px 0;border-bottom:1px solid #edf1f7;transition:background var(--duration-fast),transform var(--duration-fast)}.activity:hover{background:rgba(var(--accent-rgb),.035);transform:translateX(3px)}.date-box{width:52px;height:52px;border-radius:12px;background:#edf5ff;color:#266bd5;display:flex;flex-direction:column;align-items:center;justify-content:center}.date-box b{font-size:19px}.date-box span{font-size:11px}.activity-main{display:flex;flex:1;flex-direction:column;gap:6px}.activity-main span,.notice p,.notice small{font-size:13px;color:#8995a8}.notice{display:flex;gap:12px;padding:13px 0;border-bottom:1px solid #edf1f7;transition:transform var(--duration-fast)}.notice:hover{transform:translateX(3px)}.notice-dot{flex:0 0 9px;width:9px;height:9px;margin-top:6px;border-radius:50%;background:#3478f6;box-shadow:0 0 0 4px rgba(52,120,246,.1)}.notice-dot.leave{background:#22a06b}.notice-dot.attendance{background:#e45353}.notice p{margin:6px 0}.notice small{display:block}@media(max-width:700px){.student-hero{padding:24px;align-items:flex-start;flex-direction:column;gap:22px}.check-state{width:100%}.student-hero h1{font-size:24px}.summary-row :deep(.el-card__body){padding:18px}.summary-row strong{font-size:24px}}
</style>

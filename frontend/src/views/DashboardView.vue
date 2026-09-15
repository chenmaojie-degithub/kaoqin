<template>
  <StudentDashboardView v-if="auth.role === 'STUDENT'" />
  <div v-else class="page dashboard-page">
    <section class="welcome" data-reveal>
      <div><h2>今日考勤概览</h2><p>实时数据来自考勤数据库，正常记录由任务名单自动统计</p></div>
      <div class="rate"><span>综合出勤率</span><strong>{{ rateDisplay }}%</strong></div>
    </section>

    <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
    <template v-else>
      <AppSkeleton v-if="loading" type="dashboard" />
      <el-row v-else :gutter="18" class="metric-grid stagger-list">
        <el-col v-for="card in cards" :key="card.key" :xs="12" :md="6">
          <el-card class="metric page-card">
            <div class="metric-icon" :style="{ background: card.bg, color: card.color }"><el-icon><component :is="card.icon" /></el-icon></div>
            <div><span>{{ card.label }}</span><strong>{{ card.display.value }}</strong></div>
          </el-card>
        </el-col>
      </el-row>

      <template v-if="!loading">
        <el-row :gutter="18" class="charts" data-reveal>
          <el-col :xs="24" :lg="16"><el-card class="page-card"><template #header>最近七天考勤趋势</template><div ref="trendEl" class="chart" /></el-card></el-col>
          <el-col :xs="24" :lg="8"><el-card class="page-card"><template #header>状态占比</template><div ref="pieEl" class="chart" /></el-card></el-col>
        </el-row>
        <el-card class="page-card" data-reveal><template #header>各班级出勤率</template><div ref="barEl" class="chart" /></el-card>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'
import { useAuthStore } from '../stores/auth'
import { useCountUp } from '../composables/useCountUp'
import { useReveal } from '../composables/useReveal'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StudentDashboardView from './StudentDashboardView.vue'

const auth = useAuthStore()
const data = ref({ today: {}, trend: [], classRates: [] })
const loading = ref(false)
const error = ref(false)
const trendEl = ref()
const pieEl = ref()
const barEl = ref()
const charts = []

const cardDefinitions = [
  { key: 'normal', label: '今日正常', icon: 'CircleCheck', bg: '#e9f8ef', color: '#20a162' },
  { key: 'late', label: '今日迟到', icon: 'Timer', bg: '#fff5df', color: '#e6a23c' },
  { key: 'leave', label: '今日请假', icon: 'Document', bg: '#eaf3ff', color: '#337ecc' },
  { key: 'absent', label: '今日缺勤', icon: 'CircleClose', bg: '#fff0f0', color: '#e45656' }
]
const cards = cardDefinitions.map(card => ({
  ...card,
  display: useCountUp(computed(() => data.value.today?.[card.key] || 0), { duration: 650 })
}))
const rateDisplay = useCountUp(computed(() => data.value.attendanceRate || 0), { duration: 780, decimals: 1 })

function disposeCharts() {
  charts.splice(0).forEach(chart => chart.dispose())
}

function render() {
  disposeCharts()
  if (!trendEl.value || !pieEl.value || !barEl.value) return
  const names = { NORMAL: '正常', LATE: '迟到', EARLY_LEAVE: '早退', LEAVE: '请假', ABSENT: '缺勤' }
  const baseAnimation = { animationDuration: 650, animationEasing: 'cubicOut', animationDurationUpdate: 380 }
  const trend = echarts.init(trendEl.value)
  const pie = echarts.init(pieEl.value)
  const bar = echarts.init(barEl.value)
  charts.push(trend, pie, bar)
  trend.setOption({ ...baseAnimation, tooltip: { trigger: 'axis' }, legend: { data: ['应到', '出勤'] }, xAxis: { type: 'category', data: data.value.trend.map(x => x.date.slice(5)) }, yAxis: { type: 'value' }, series: [{ name: '应到', type: 'line', smooth: true, showSymbol: false, data: data.value.trend.map(x => x.total) }, { name: '出勤', type: 'line', smooth: true, showSymbol: false, areaStyle: { opacity: .12 }, data: data.value.trend.map(x => x.present) }] })
  pie.setOption({ ...baseAnimation, tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['45%', '70%'], label: { formatter: '{b}\n{d}%' }, data: Object.entries(data.value.statusDistribution || {}).map(([key, value]) => ({ name: names[key] || key, value })) }] })
  bar.setOption({ ...baseAnimation, tooltip: {}, xAxis: { type: 'category', data: data.value.classRates.map(x => x.name) }, yAxis: { type: 'value', max: 100 }, series: [{ type: 'bar', barWidth: 32, itemStyle: { color: '#3478f6', borderRadius: [6, 6, 0, 0] }, data: data.value.classRates.map(x => x.rate) }] })
}

async function load() {
  if (auth.role === 'STUDENT') return
  loading.value = true
  error.value = false
  try {
    data.value = await request.get('/statistics/dashboard')
    await nextTick()
    render()
  } catch {
    error.value = true
  } finally {
    loading.value = false
    await nextTick()
    if (!error.value) render()
  }
}

const resize = () => charts.forEach(chart => chart.resize())
useReveal()
onMounted(() => { load(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); disposeCharts() })
</script>

<style scoped>
.welcome{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;padding:22px 26px;border-radius:14px;color:#fff;background:radial-gradient(circle at 82% 5%,rgba(255,255,255,.22),transparent 28%),linear-gradient(120deg,#174d99,#3478f6);overflow:hidden;position:relative}.welcome::after{content:"";position:absolute;width:170px;height:170px;right:-45px;bottom:-105px;border:1px solid rgba(255,255,255,.22);border-radius:50%;transition:transform var(--duration-hero) var(--ease-out)}.welcome:hover::after{transform:scale(1.18)}.welcome h2,.welcome p{margin:0}.welcome p{margin-top:8px;color:#d8e8ff}.rate{display:flex;flex-direction:column;align-items:flex-end;position:relative;z-index:1}.rate strong{font-size:30px}.metric-grid{margin-bottom:18px}.metric :deep(.el-card__body){display:flex;align-items:center;gap:15px}.metric-icon{width:50px;height:50px;border-radius:13px;display:grid;place-items:center;font-size:24px;transition:transform var(--duration-normal) var(--ease-out)}.metric:hover .metric-icon{transform:scale(1.08) rotate(-3deg)}.metric span{display:block;color:#7d899b}.metric strong{font-size:27px}.charts{margin-bottom:18px}.chart{height:320px}@media(max-width:700px){.welcome{align-items:flex-start;gap:18px;flex-direction:column}.rate{align-items:flex-start}.chart{height:270px}}
</style>

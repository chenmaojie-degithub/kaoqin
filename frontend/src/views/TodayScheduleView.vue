<template>
  <div class="page">
    <div class="page-title" data-reveal>
      <div><h2>课程 / 今日安排</h2><p v-if="calendar">{{ calendar.academicYear }}学年 {{ calendar.semester }} · 第{{ selectedWeek }}周 · {{ weekDateText }}</p></div>
      <div class="student-week-nav"><el-button circle :disabled="selectedWeek <= 1" @click="previousWeek(false)"><el-icon><ArrowLeft /></el-icon></el-button><el-select v-model="selectedWeek" aria-label="查看周次"><el-option v-for="item in weekOptions" :key="item" :label="`第 ${item} 周`" :value="item" /></el-select><el-button circle :disabled="selectedWeek >= (calendar?.totalWeeks || 1)" @click="nextWeek(false)"><el-icon><ArrowRight /></el-icon></el-button><el-button text type="primary" @click="goCurrentWeek">回到本周</el-button></div>
    </div>
    <el-alert v-if="semesterStateText" :title="semesterStateText" type="warning" show-icon :closable="false" class="semester-alert" />
    <el-card class="page-card weekly-card" data-reveal>
      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading" :rows="5" />
      <WeeklyScheduleGrid v-else-if="classId" :assignments="assignments" :class-id="classId" :editable="false" :selected-week="selectedWeek" :week-dates="weekDates" :today="calendar?.today" :now="now" />
      <AppState v-else title="暂无课程安排" description="当前班级还没有设置课程时间" />
    </el-card>
    <el-card class="page-card schedule-card" data-reveal>
      <template #header><div class="card-head"><strong>今天的课程</strong><span>{{ todayText }}</span></div></template>
      <AppState v-if="!loading && !todayRows.length" title="今天没有课程安排" description="可以切换周次查看完整课表" action="查看我的考勤" @action="$router.push('/my/attendance')" />
      <TransitionGroup v-else name="list-rise" tag="div">
        <div v-for="row in todayRows" :key="row.courseName + row.startTime" class="schedule-item"><div class="time"><strong>{{ row.startTime?.slice(0, 5) }}</strong><span>{{ row.endTime?.slice(0, 5) }}</span></div><div class="line"></div><div class="course"><h3>{{ row.courseName }}</h3><p><el-icon><User /></el-icon>{{ row.teacherName }}　<el-icon><Location /></el-icon>{{ row.location }}</p></div><el-tag :type="row.requiresAttendance ? 'danger' : row.taskStatus === 'CLOSED' ? 'success' : 'info'" size="large">{{ row.requiresAttendance ? '需要签到' : row.taskStatus === 'CLOSED' ? '考勤已结束' : '无需签到' }}</el-tag></div>
      </TransitionGroup>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../utils/request'
import { useReveal } from '../composables/useReveal'
import { useSemesterCalendar } from '../composables/useSemesterCalendar'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import WeeklyScheduleGrid from '../components/WeeklyScheduleGrid.vue'

const assignments = ref([]), todayRows = ref([]), loading = ref(false), error = ref(false)
const { calendar, selectedWeek, now, weekOptions, weekDates, weekDateText, semesterStateText, loadSemesterCalendar, previousWeek, nextWeek, goCurrentWeek } = useSemesterCalendar()
const classId = computed(() => assignments.value[0]?.classId)
const todayText = computed(() => calendar.value?.today ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date(`${calendar.value.today}T12:00:00`)) : '')

async function load() {
  loading.value = true; error.value = false
  try { [assignments.value, todayRows.value] = await Promise.all([request.get('/teaching'), request.get('/student-portal/today-schedule'), loadSemesterCalendar()]).then(([courses, today]) => [courses, today]) }
  catch { error.value = true }
  finally { loading.value = false }
}
useReveal(); onMounted(load)
</script>

<style scoped>
.page-title{margin-bottom:18px;display:flex;align-items:center;justify-content:space-between;gap:18px}.page-title h2,.page-title p{margin:0}.page-title p{margin-top:7px;color:#8492a6}.student-week-nav{display:flex;align-items:center;gap:8px}.student-week-nav .el-select{width:112px}.semester-alert{margin-bottom:16px}.weekly-card{margin-bottom:18px}.schedule-card{min-height:240px}.card-head{display:flex;align-items:center;justify-content:space-between}.card-head span{color:#8492a6;font-size:13px}.schedule-item{display:flex;align-items:center;gap:20px;padding:20px 10px;border-bottom:1px solid #edf1f6;transition:transform var(--duration-normal),background var(--duration-normal)}.schedule-item:hover{transform:translateX(4px);background:rgba(var(--accent-rgb),.03)}.time{width:58px;display:flex;flex-direction:column;color:#245fae}.time strong{font-size:20px}.time span{font-size:12px;color:#95a0b2}.line{width:4px;height:48px;border-radius:3px;background:#3478f6}.course{flex:1}.course h3,.course p{margin:0}.course p{display:flex;align-items:center;margin-top:9px;color:#7e8a9c;font-size:14px}.course .el-icon{margin-right:4px}@media(max-width:700px){.page-title{align-items:flex-start;flex-direction:column}.student-week-nav{flex-wrap:wrap}.schedule-item{gap:10px}.course p{flex-wrap:wrap}.line{display:none}}
</style>

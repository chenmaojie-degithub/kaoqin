<template>
  <div class="page attendance-page">
    <div class="toolbar"><div><h2>我的考勤</h2><span class="muted">正常记录由已完成的考勤任务自动生成</span></div><div class="spacer" /><el-radio-group v-model="mode"><el-radio-button value="calendar">日历</el-radio-button><el-radio-button value="list">列表</el-radio-button></el-radio-group></div>
    <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
    <template v-else>
      <AppSkeleton v-if="loading" type="dashboard" />
      <el-row v-else :gutter="14" class="stats stagger-list"><el-col v-for="item in stats" :key="item.key" :xs="8" :sm="4"><el-card shadow="never"><span>{{ item.label }}</span><b>{{ item.display.value }}</b></el-card></el-col></el-row>
      <Transition v-if="!loading" name="content-swap" mode="out-in">
        <el-card v-if="mode === 'calendar'" key="calendar" class="page-card calendar-card"><el-calendar v-model="selectedDate"><template #date-cell="{ data: cell }"><div class="calendar-cell"><span>{{ Number(cell.day.slice(8)) }}</span><button v-for="row in byDate[cell.day] || []" :key="row.taskId" type="button" class="course-dot" :class="row.status.toLowerCase()" @click.stop="showDetail(row)">{{ row.courseName }} · {{ labels[row.status] }}</button></div></template></el-calendar></el-card>
        <el-card v-else key="list" class="page-card"><el-table :data="rows" stripe @row-click="showDetail"><el-table-column prop="attendanceDate" label="日期" sortable /><el-table-column prop="courseName" label="课程" /><el-table-column label="状态"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column><el-table-column prop="remark" label="备注"><template #default="{ row }">{{ row.remark || '—' }}</template></el-table-column><template #empty><AppState title="暂无考勤记录" description="完成课程签到后，记录会显示在这里" /></template></el-table></el-card>
      </Transition>
    </template>
    <AttendanceDetailDrawer v-model="drawerVisible" :record="activeRecord" />
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../utils/request'
import { useCountUp } from '../composables/useCountUp'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import AttendanceDetailDrawer from '../components/AttendanceDetailDrawer.vue'
const rows = ref([]), loading = ref(false), error = ref(false), mode = ref('calendar'), selectedDate = ref(new Date()), drawerVisible = ref(false), activeRecord = ref(null)
const labels = { NORMAL: '正常', LATE: '迟到', EARLY_LEAVE: '早退', LEAVE: '请假', ABSENT: '缺勤' }
const counts = computed(() => rows.value.reduce((map, row) => (map[row.status] = (map[row.status] || 0) + 1, map), {}))
const stats = Object.entries(labels).map(([key, label]) => ({ key, label, display: useCountUp(computed(() => counts.value[key] || 0), { duration: 650 }) }))
const byDate = computed(() => rows.value.reduce((map, row) => ((map[row.attendanceDate] ??= []).push(row), map), {}))
function showDetail(row) { activeRecord.value = row; drawerVisible.value = true }
async function load() { loading.value = true; error.value = false; try { rows.value = await request.get('/attendance/records') } catch { error.value = true } finally { loading.value = false } }
onMounted(load)
</script>
<style scoped>.attendance-page{max-width:1400px;margin:auto}.toolbar h2{margin:0 0 7px}.stats{margin-bottom:14px}.stats .el-card{border:0;border-radius:12px}.stats :deep(.el-card__body){display:flex;align-items:center;justify-content:space-between}.stats span{color:#7d899b}.stats b{font-size:24px;color:#234f89}.calendar-card :deep(.el-calendar-day){height:105px;padding:5px}.calendar-card :deep(.is-selected){color:inherit}.calendar-cell{height:100%;overflow:auto}.calendar-cell>span{display:block;margin:3px 5px;font-weight:600}.course-dot{display:block;width:100%;margin:3px 0;padding:3px 5px;border:0;border-radius:5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-align:left;cursor:pointer;background:#eaf8f1;color:#21875f;font-size:11px;transition:transform var(--duration-fast),filter var(--duration-fast)}.course-dot:hover{transform:translateX(2px);filter:saturate(1.25)}.course-dot.absent{background:#fff0f0;color:#cf4545}.course-dot.late,.course-dot.early_leave{background:#fff6df;color:#b4780c}.course-dot.leave{background:#edf3fb;color:#55718f}:deep(.el-table__row){cursor:pointer}.content-swap-enter-active,.content-swap-leave-active{transition:opacity var(--duration-normal),transform var(--duration-normal)}.content-swap-enter-from{opacity:0;transform:translateY(5px)}.content-swap-leave-to{opacity:0;transform:translateY(-3px)}@media(max-width:700px){.calendar-card :deep(.el-calendar-day){height:78px}.course-dot{font-size:0}.course-dot:after{content:'●';font-size:10px}}</style>

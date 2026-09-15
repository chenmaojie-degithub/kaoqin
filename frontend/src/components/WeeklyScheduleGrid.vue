<template>
  <div class="schedule-wrap">
    <div class="schedule-grid">
      <div class="corner"><b>节次</b><span>时间</span></div>
      <div v-for="day in displayDays" :key="day.value" class="day-head" :class="{ today: day.isToday, outside: day.inSemester === false }" :style="{ gridColumn: day.value + 1 }">
        <b>星期{{ day.label }}</b><span>{{ day.display || (day.value > 5 ? '周末' : '教学日') }}</span><em v-if="day.isToday">今天</em>
      </div>
      <template v-for="(block, blockIndex) in PERIOD_BLOCKS" :key="block.label">
        <div class="time-cell" :style="{ gridRow: blockIndex + 2 }"><b>{{ block.label }}</b><span>{{ block.start.slice(0, 5) }}-{{ block.end.slice(0, 5) }}</span></div>
        <button v-for="day in displayDays" :key="`${block.label}-${day.value}`" type="button" class="empty-cell" :class="{ today: day.isToday, outside: day.inSemester === false }" :style="{ gridColumn: day.value + 1, gridRow: blockIndex + 2 }" :disabled="!editable || day.inSemester === false" :title="editable ? `安排周${day.label}第${block.label}节` : ''" @click="$emit('create', { weekday: day.value, startTime: block.start, endTime: block.end })"><el-icon v-if="editable && day.inSemester !== false"><Plus /></el-icon></button>
      </template>
      <article v-for="item in positionedSchedules" :key="item.id" class="course-block" :class="[`course-${item.courseState}`, { muted: item.inSemester === false }]" :style="item.style">
        <div class="course-title"><b>{{ item.courseName }}</b><span>{{ item.teacherName }}</span></div>
        <el-tag v-if="item.courseState" class="course-state" size="small" effect="dark" :type="item.courseState === 'current' ? 'success' : item.courseState === 'ended' ? 'info' : ''">{{ stateNames[item.courseState] }}</el-tag>
        <small><el-icon><Location /></el-icon>{{ item.location }}</small>
        <small>第 {{ item.periodText }} 节 · {{ item.startTime.slice(0, 5) }}-{{ item.endTime.slice(0, 5) }}</small>
        <small>{{ item.startWeek === item.endWeek ? `仅第${item.startWeek}周` : `第${item.startWeek}-${item.endWeek}周` }}</small>
        <button v-if="editable" type="button" class="edit" title="编辑课程安排" @click.stop="$emit('edit', item)"><el-icon><Edit /></el-icon></button>
        <button v-if="editable" type="button" class="remove" title="删除时间安排" @click.stop="$emit('remove', item)"><el-icon><Close /></el-icon></button>
      </article>
    </div>
    <div class="legend"><span><i></i>已安排课程</span><span><i class="today-dot"></i>今天</span><span>最小单位为连续两节，可跨多个课时块</span></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { PERIOD_BLOCKS, WEEK_DAYS, timeToMinutes } from '../constants/schedule'

const props = defineProps({ assignments: { type: Array, default: () => [] }, classId: [Number, String], editable: Boolean, selectedWeek: { type: Number, default: 1 }, weekDates: { type: Array, default: () => [] }, today: String, now: { type: Date, default: () => new Date() } })
defineEmits(['create', 'edit', 'remove'])
const stateNames = { ended: '已结束', current: '进行中', upcoming: '未开始' }
const displayDays = computed(() => WEEK_DAYS.map(day => ({ ...day, ...(props.weekDates.find(item => item.value === day.value) || {}) })))
const courseState = (schedule, day) => {
  if (!day?.isToday) return ''
  const currentMinutes = props.now.getHours() * 60 + props.now.getMinutes()
  if (currentMinutes < timeToMinutes(schedule.startTime)) return 'upcoming'
  if (currentMinutes <= timeToMinutes(schedule.endTime)) return 'current'
  return 'ended'
}
const positionedSchedules = computed(() => props.assignments
  .filter(row => String(row.classId) === String(props.classId))
  .flatMap(row => (row.schedules || []).filter(schedule => (schedule.startWeek ?? 1) <= props.selectedWeek && (schedule.endWeek ?? Number.MAX_SAFE_INTEGER) >= props.selectedWeek).map(schedule => {
    const startMinutes = timeToMinutes(schedule.startTime), endMinutes = timeToMinutes(schedule.endTime)
    const startIndex = Math.max(0, PERIOD_BLOCKS.findIndex((block, index) => startMinutes >= timeToMinutes(block.start) && (index === PERIOD_BLOCKS.length - 1 || startMinutes < timeToMinutes(PERIOD_BLOCKS[index + 1].start))))
    let endIndex = PERIOD_BLOCKS.findIndex(block => endMinutes <= timeToMinutes(block.end))
    if (endIndex < startIndex) endIndex = startIndex
    const day = displayDays.value.find(item => item.value === Number(schedule.weekday))
    return { ...schedule, startWeek: schedule.startWeek ?? 1, endWeek: schedule.endWeek ?? 1, courseClassId: row.id, courseName: row.courseName, teacherName: row.teacherName, classId: row.classId, inSemester: day?.inSemester, courseState: courseState(schedule, day), periodText: `${startIndex * 2 + 1}-${(endIndex + 1) * 2}`, style: { gridColumn: Number(schedule.weekday) + 1, gridRow: `${startIndex + 2} / span ${endIndex - startIndex + 1}` } }
  })))
</script>

<style scoped>
.schedule-wrap{overflow:auto;padding-bottom:4px}.schedule-grid{min-width:1040px;display:grid;grid-template-columns:92px repeat(7,minmax(126px,1fr));grid-template-rows:68px repeat(5,104px);position:relative;border:1px solid #e4eaf3;border-radius:16px;overflow:hidden;background:rgba(255,255,255,.5)}.corner,.day-head,.time-cell{position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;background:#f6f9fe;color:#33425b;border-right:1px solid #e6ebf3;border-bottom:1px solid #e6ebf3}.corner{grid-column:1;grid-row:1}.corner span,.day-head span,.time-cell span{font-size:11px;color:#93a0b3}.day-head{grid-row:1}.day-head.today{background:rgba(var(--accent-rgb),.1);color:var(--accent)}.day-head em{position:absolute;right:7px;top:5px;padding:2px 5px;border-radius:6px;background:var(--accent);color:#fff;font-size:9px;font-style:normal}.day-head.outside{opacity:.48}.time-cell{grid-column:1}.time-cell b{color:var(--accent)}.empty-cell{position:relative;border:0;border-right:1px solid #edf1f7;border-bottom:1px solid #edf1f7;background:transparent;color:transparent;cursor:pointer;transition:background var(--duration-fast),color var(--duration-fast)}.empty-cell.today{background:rgba(var(--accent-rgb),.035)}.empty-cell.outside{background:rgba(140,150,165,.04)}.empty-cell:not(:disabled):hover{z-index:1;background:rgba(var(--accent-rgb),.075);color:rgba(var(--accent-rgb),.6)}.empty-cell:disabled{cursor:default}.course-block{position:relative;z-index:2;margin:5px;padding:12px;display:flex;flex-direction:column;gap:5px;overflow:hidden;border:1px solid rgba(var(--accent-rgb),.22);border-radius:13px;background:linear-gradient(145deg,rgba(var(--accent-rgb),.92),rgba(var(--accent-rgb),.7));color:#fff;box-shadow:0 8px 22px rgba(var(--accent-rgb),.18);transition:transform var(--duration-fast),box-shadow var(--duration-fast)}.course-block:hover{transform:translateY(-2px);box-shadow:0 12px 26px rgba(var(--accent-rgb),.28)}.course-block.course-current{border:2px solid #35d69f;box-shadow:0 0 0 3px rgba(53,214,159,.15),0 12px 28px rgba(var(--accent-rgb),.3)}.course-block.course-ended{filter:saturate(.65);opacity:.74}.course-block.course-upcoming{border-color:rgba(255,255,255,.6)}.course-block.muted{opacity:.35}.course-title{display:flex;flex-direction:column;gap:2px;padding-right:50px}.course-block b{font-size:14px}.course-block span,.course-block small{font-size:11px;color:rgba(255,255,255,.83)}.course-block small{display:flex;align-items:center;gap:3px}.course-state{position:absolute;right:7px;bottom:7px}.edit,.remove{position:absolute;top:8px;width:25px;height:25px;display:grid;place-items:center;border:0;border-radius:8px;background:rgba(8,25,55,.25);color:#fff;cursor:pointer;opacity:0;transition:opacity var(--duration-fast),background var(--duration-fast)}.edit{right:39px}.remove{right:8px}.course-block:hover .edit,.course-block:hover .remove{opacity:1}.edit:hover{background:rgba(255,255,255,.25)}.remove:hover{background:#e65353}.legend{min-width:1040px;padding:12px 4px 2px;display:flex;align-items:center;gap:24px;color:#8592a6;font-size:12px}.legend span{display:flex;align-items:center;gap:7px}.legend span:last-child{margin-left:auto}.legend i{width:9px;height:9px;border-radius:3px;background:var(--accent)}.legend .today-dot{background:rgba(var(--accent-rgb),.12);border:1px solid var(--accent)}@media(max-width:700px){.schedule-grid{grid-template-columns:76px repeat(7,118px)}.course-block{padding:9px}.legend span:last-child{margin-left:0}}
</style>

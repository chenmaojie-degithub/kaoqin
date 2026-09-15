<template>
  <div class="page">
    <el-card class="page-card">
      <div class="toolbar teaching-toolbar">
        <div><h3 class="section-title">课程与教学班安排</h3><span class="muted">按两节课时块排课，同一班级的上课时间不可重叠</span></div>
        <el-select v-if="viewMode === 'grid'" v-model="classFilter" placeholder="选择班级" style="width:190px"><el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-radio-group v-model="viewMode"><el-radio-button value="grid">周课表</el-radio-button><el-radio-button value="list">安排列表</el-radio-button></el-radio-group>
        <div class="spacer" />
        <el-button v-if="auth.role === 'ADMIN'" type="primary" @click="openAssignment"><el-icon><Plus /></el-icon>新增课程分配</el-button>
      </div>

      <section v-if="viewMode === 'grid' && calendar" class="semester-panel">
        <div class="semester-title"><strong>{{ calendar.academicYear }}学年 {{ calendar.semester }}</strong><span>第{{ selectedWeek }}周 · {{ weekDateText }}</span></div>
        <el-tag v-if="semesterStateText" type="warning" effect="light">{{ semesterStateText }}</el-tag>
        <div class="week-range"><span>查看范围</span><el-select :model-value="startWeek" aria-label="开始周" @change="changeStartWeek"><el-option v-for="item in weekOptions" :key="`start-${item}`" :label="`第 ${item} 周`" :value="item" /></el-select><span>至</span><el-select :model-value="endWeek" aria-label="结束周" @change="changeEndWeek"><el-option v-for="item in weekOptions" :key="`end-${item}`" :label="`第 ${item} 周`" :value="item" :disabled="item < startWeek" /></el-select></div>
        <div class="week-navigation"><el-button circle :disabled="selectedWeek <= startWeek" @click="previousWeek()"><el-icon><ArrowLeft /></el-icon></el-button><el-select v-model="selectedWeek" aria-label="当前显示周"><el-option v-for="item in weekOptions.filter(item => item >= startWeek && item <= endWeek)" :key="`display-${item}`" :label="`第 ${item} 周`" :value="item" /></el-select><el-button circle :disabled="selectedWeek >= endWeek" @click="nextWeek()"><el-icon><ArrowRight /></el-icon></el-button><el-button text type="primary" @click="goCurrentWeek">回到本周</el-button></div>
      </section>

      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading && !rows.length" />
      <Transition v-else name="content-swap" mode="out-in">
        <div v-if="viewMode === 'grid'" key="grid">
          <el-alert v-if="hasLegacyConflicts" title="检测到历史冲突安排" description="当前班级已有两条课程占用相同时间。请切换到“安排列表”核对并删除错误记录；新的冲突安排已被系统禁止。" type="error" show-icon :closable="false" class="conflict-alert" />
          <WeeklyScheduleGrid v-if="classFilter" :assignments="rows" :class-id="classFilter" :editable="canEditSchedule" :selected-week="selectedWeek" :week-dates="weekDates" :today="calendar?.today" :now="now" @create="openGridSchedule" @edit="openEditSchedule" @remove="removeSchedule" />
          <AppState v-else title="暂无可排课班级" description="请先创建课程、班级和教师的关联" />
        </div>
        <el-table v-else key="list" v-loading="loading" :data="rows" stripe>
          <el-table-column prop="courseName" label="课程" /><el-table-column prop="className" label="班级" /><el-table-column prop="teacherName" label="教师" /><el-table-column prop="academicYear" label="学年" /><el-table-column prop="semester" label="学期" />
          <el-table-column label="上课安排" min-width="340"><template #default="{ row }"><TransitionGroup name="list-rise" tag="div"><div v-for="item in row.schedules" :key="item.id" class="schedule-line"><span>第{{ item.startWeek }}-{{ item.endWeek }}周 · 周{{ week[item.weekday] }} {{ item.startTime?.slice(0, 5) }}-{{ item.endTime?.slice(0, 5) }} · {{ item.location }}</span><span><el-button v-if="canEditSchedule" link type="primary" @click="openEditSchedule({ ...item, courseClassId: row.id, courseName: row.courseName, teacherName: row.teacherName, classId: row.classId })">编辑</el-button><el-button v-if="canEditSchedule" link type="danger" @click="removeSchedule({ ...item, courseName: row.courseName })">删除</el-button></span></div></TransitionGroup><span v-if="!row.schedules.length" class="muted">暂未设置</span></template></el-table-column>
          <el-table-column v-if="canEditSchedule" label="操作" width="160"><template #default="{ row }"><el-button link type="primary" @click="openSchedule(row)">排课</el-button><el-button v-if="auth.role === 'ADMIN'" link type="danger" :loading="deletingId === row.id" @click="removeAssignment(row)">删除关联</el-button></template></el-table-column>
          <template #empty><AppState title="暂无课程安排" description="管理员可新增课程、班级和教师的关联" /></template>
        </el-table>
      </Transition>
    </el-card>

    <el-dialog v-model="visible" title="分配课程" width="520px" append-to-body align-center>
      <el-form :model="form" label-width="90px"><el-form-item label="课程" required><el-select v-model="form.courseId" style="width:100%"><el-option v-for="item in courses" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="班级" required><el-select v-model="form.classId" style="width:100%"><el-option v-for="item in classes" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="教师" required><el-select v-model="form.teacherId" style="width:100%"><el-option v-for="item in teachers" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="学年"><el-input v-model="form.academicYear" /></el-form-item><el-form-item label="学期"><el-input v-model="form.semester" /></el-form-item></el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveAssignment">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="scheduleVisible" :title="editingSchedule ? '编辑课程安排' : '安排上课时间'" width="560px" append-to-body align-center>
      <el-alert title="课时规则" description="最小单位为连续两节，例如1-2节；也可以连续安排1-4节或1-6节。" type="info" show-icon :closable="false" class="schedule-tip" />
      <el-form :model="scheduleForm" label-width="90px">
        <el-form-item label="教学课程" required><el-select v-model="selected" style="width:100%"><el-option v-for="item in selectableAssignments" :key="item.id" :label="`${item.courseName} / ${item.className} / ${item.teacherName}`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="星期" required><el-select v-model="scheduleForm.weekday" style="width:100%"><el-option v-for="(value, key) in week" :key="key" :label="`星期${value}`" :value="Number(key)" /></el-select></el-form-item>
        <el-form-item label="开始课时" required><el-select v-model="scheduleForm.startTime" style="width:100%" @change="normalizeEndTime"><el-option v-for="item in periodBlocks" :key="item.start" :label="`第${item.label}节（${item.start.slice(0, 5)}开始）`" :value="item.start" /></el-select></el-form-item>
        <el-form-item label="结束课时" required><el-select v-model="scheduleForm.endTime" style="width:100%"><el-option v-for="item in endBlockOptions" :key="item.end" :label="`至第${item.label}节（${item.end.slice(0, 5)}结束）`" :value="item.end" /></el-select></el-form-item>
        <el-form-item label="地点" required><el-input v-model="scheduleForm.location" placeholder="例如：东区13-403软件测试实训室" /></el-form-item>
        <el-form-item v-if="editingSchedule" label="修改方式" required><el-radio-group v-model="editMode"><el-radio-button value="ALL">整个范围</el-radio-button><el-radio-button value="CURRENT_WEEK" :disabled="!currentWeekInOriginal">仅第{{ selectedWeek }}周</el-radio-button><el-radio-button value="FROM_CURRENT_WEEK" :disabled="!currentWeekInOriginal">从第{{ selectedWeek }}周起</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="生效周次" required><div class="effective-weeks"><el-select v-model="scheduleForm.startWeek" :disabled="editingSchedule && editMode !== 'ALL'" @change="normalizeScheduleWeeks"><el-option v-for="item in weekOptions" :key="`form-start-${item}`" :label="`第 ${item} 周`" :value="item" /></el-select><span>至</span><el-select v-model="scheduleForm.endWeek" :disabled="editingSchedule && editMode !== 'ALL'"><el-option v-for="item in weekOptions" :key="`form-end-${item}`" :label="`第 ${item} 周`" :value="item" :disabled="item < scheduleForm.startWeek" /></el-select></div><div v-if="editingSchedule && editMode !== 'ALL'" class="edit-mode-tip">{{ editMode === 'CURRENT_WEEK' ? `仅替换第${selectedWeek}周，前后周次保留原课程` : `第${selectedWeek}周起替换，之前周次保留原课程` }}</div></el-form-item>
      </el-form>
      <template #footer><el-button @click="scheduleVisible = false">取消</el-button><el-button type="primary" :loading="scheduleSaving" @click="saveSchedule">{{ editingSchedule ? '保存修改' : '保存安排' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import { useAuthStore } from '../stores/auth'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import WeeklyScheduleGrid from '../components/WeeklyScheduleGrid.vue'
import { PERIOD_BLOCKS, timeToMinutes } from '../constants/schedule'
import { useSemesterCalendar } from '../composables/useSemesterCalendar'

const auth = useAuthStore()
const rows = ref([]), loading = ref(false), error = ref(false), visible = ref(false), scheduleVisible = ref(false), editingSchedule = ref(null), editMode = ref('ALL')
const courses = ref([]), classes = ref([]), teachers = ref([]), selected = ref(), classFilter = ref(), viewMode = ref('grid')
const saving = ref(false), scheduleSaving = ref(false), deletingId = ref(null)
const week = { 1: '一', 2: '二', 3: '三', 4: '四', 5: '五', 6: '六', 7: '日' }
const periodBlocks = PERIOD_BLOCKS
const { calendar, selectedWeek, startWeek, endWeek, now, weekOptions, weekDates, weekDateText, semesterStateText, loadSemesterCalendar, changeStartWeek, changeEndWeek, previousWeek, nextWeek, goCurrentWeek } = useSemesterCalendar()
const form = reactive({ courseId: '', classId: '', teacherId: '', academicYear: '2026-2027', semester: '第一学期' })
const scheduleForm = reactive({ weekday: 1, startTime: periodBlocks[0].start, endTime: periodBlocks[0].end, location: '', startWeek: 1, endWeek: 1 })
const classOptions = computed(() => [...new Map(rows.value.map(row => [row.classId, { id: row.classId, name: row.className }])).values()])
const selectableAssignments = computed(() => rows.value.filter(row => !classFilter.value || String(row.classId) === String(classFilter.value)))
const endBlockOptions = computed(() => { const index = periodBlocks.findIndex(item => item.start === scheduleForm.startTime); return periodBlocks.slice(Math.max(0, index)) })
const toMinutes = timeToMinutes
const canEditSchedule = computed(() => ['ADMIN', 'TEACHER'].includes(auth.role))
const selectedClassSchedules = computed(() => rows.value.filter(row => String(row.classId) === String(classFilter.value)).flatMap(row => (row.schedules || []).map(item => ({ ...item, courseClassId: row.id }))))
const hasLegacyConflicts = computed(() => selectedClassSchedules.value.filter(item => item.startWeek <= selectedWeek.value && item.endWeek >= selectedWeek.value).some((item, index, list) => list.some((other, otherIndex) => index !== otherIndex && Number(item.weekday) === Number(other.weekday) && toMinutes(item.startTime) < toMinutes(other.endTime) && toMinutes(item.endTime) > toMinutes(other.startTime))))
const currentWeekInOriginal = computed(() => !editingSchedule.value || (selectedWeek.value >= editingSchedule.value.startWeek && selectedWeek.value <= editingSchedule.value.endWeek))

async function load() {
  loading.value = true; error.value = false
  try {
    const semesterPromise = calendar.value ? Promise.resolve(calendar.value) : loadSemesterCalendar()
    const [, semester] = await Promise.all([request.get('/teaching').then(data => { rows.value = data }), semesterPromise])
    form.academicYear = semester.academicYear
    form.semester = semester.semester
    if (!classOptions.value.some(item => String(item.id) === String(classFilter.value))) classFilter.value = classOptions.value[0]?.id
  } catch { error.value = true } finally { loading.value = false }
}
async function openAssignment() {
  loading.value = true
  try { [courses.value, classes.value, teachers.value] = await Promise.all([request.get('/courses'), request.get('/classes'), request.get('/teachers').then(data => data.records)]); visible.value = true }
  finally { loading.value = false }
}
async function saveAssignment() {
  if (!form.courseId || !form.classId || !form.teacherId) return ElMessage.warning('请选择课程、班级和教师')
  saving.value = true
  try { await request.post('/teaching', form); ElMessage.success({ message: '课程分配成功', showClose: true }); visible.value = false; await load(); classFilter.value = form.classId }
  finally { saving.value = false }
}
function resetScheduleTime(payload = {}) {
  scheduleForm.weekday = payload.weekday || 1
  scheduleForm.startTime = payload.startTime || periodBlocks[0].start
  scheduleForm.endTime = payload.endTime || periodBlocks[0].end
  scheduleForm.location = payload.location || ''
  scheduleForm.startWeek = payload.startWeek || selectedWeek.value
  scheduleForm.endWeek = payload.endWeek || selectedWeek.value
}
function openSchedule(row) { editingSchedule.value = null; editMode.value = 'ALL'; classFilter.value = row.classId; selected.value = row.id; resetScheduleTime(); scheduleVisible.value = true }
function openGridSchedule(payload) { editingSchedule.value = null; editMode.value = 'ALL'; selected.value = selectableAssignments.value[0]?.id; resetScheduleTime(payload); scheduleVisible.value = true }
function openEditSchedule(item) { editingSchedule.value = item; editMode.value = 'ALL'; classFilter.value = item.classId; selected.value = item.courseClassId; resetScheduleTime(item); scheduleVisible.value = true }
function normalizeEndTime() { if (!endBlockOptions.value.some(item => item.end === scheduleForm.endTime)) scheduleForm.endTime = endBlockOptions.value[0]?.end }
function normalizeScheduleWeeks() { if (scheduleForm.endWeek < scheduleForm.startWeek) scheduleForm.endWeek = scheduleForm.startWeek }
function effectiveEditWeeks() {
  if (!editingSchedule.value || editMode.value === 'ALL') return [scheduleForm.startWeek, scheduleForm.endWeek]
  return editMode.value === 'CURRENT_WEEK' ? [selectedWeek.value, selectedWeek.value] : [selectedWeek.value, editingSchedule.value.endWeek]
}
function hasLocalConflict() {
  const assignment = rows.value.find(row => row.id === selected.value)
  if (!assignment) return false
  const [rangeStart, rangeEnd] = effectiveEditWeeks()
  return rows.value.filter(row => row.classId === assignment.classId).some(row => (row.schedules || []).some(item => item.id !== editingSchedule.value?.id && Number(item.weekday) === Number(scheduleForm.weekday) && item.startWeek <= rangeEnd && item.endWeek >= rangeStart && toMinutes(item.startTime) < toMinutes(scheduleForm.endTime) && toMinutes(item.endTime) > toMinutes(scheduleForm.startTime)))
}
async function saveSchedule() {
  if (!selected.value) return ElMessage.warning('请选择教学课程')
  if (!scheduleForm.location.trim()) return ElMessage.warning('请填写上课地点')
  if (scheduleForm.endWeek < scheduleForm.startWeek) return ElMessage.warning('结束周不能早于开始周')
  if (editingSchedule.value && editMode.value !== 'ALL' && !currentWeekInOriginal.value) return ElMessage.warning('当前查看周不在该课程原生效范围内')
  if (hasLocalConflict()) return ElMessage.warning('该班级在所选周次和时间段已有课程')
  scheduleSaving.value = true
  try {
    if (editingSchedule.value) await request.put(`/teaching/${selected.value}/schedules/${editingSchedule.value.id}`, scheduleForm, { params: { mode: editMode.value, currentWeek: selectedWeek.value } })
    else await request.post(`/teaching/${selected.value}/schedules`, scheduleForm)
    ElMessage.success({ message: editingSchedule.value ? '课程安排已更新' : '排课成功', showClose: true }); scheduleVisible.value = false; await load()
  }
  finally { scheduleSaving.value = false }
}
async function removeSchedule(item) {
  await ElMessageBox.confirm(`确定删除“${item.courseName}”的这段上课时间吗？`, '删除课程时间', { type: 'warning' })
  await request.delete(`/teaching/schedules/${item.id}`)
  ElMessage.success({ message: '课程时间已删除', showClose: true })
  await load()
}
async function removeAssignment(row) {
  await ElMessageBox.confirm('确定删除该教学安排吗？', '确认', { type: 'warning' })
  deletingId.value = row.id
  try { await request.delete(`/teaching/${row.id}`); ElMessage.success({ message: '教学安排已删除', showClose: true }); await load() }
  finally { deletingId.value = null }
}
onMounted(load)
</script>

<style scoped>
.teaching-toolbar{flex-wrap:wrap}.teaching-toolbar .section-title{margin:0 0 5px}.semester-panel{display:flex;align-items:center;gap:18px;margin:18px 0;padding:14px 16px;border:1px solid rgba(var(--accent-rgb),.13);border-radius:14px;background:linear-gradient(120deg,rgba(var(--accent-rgb),.07),rgba(255,255,255,.75))}.semester-title{display:flex;flex-direction:column;gap:4px;min-width:210px}.semester-title span{color:#77859a;font-size:13px}.week-range,.week-navigation,.effective-weeks{display:flex;align-items:center;gap:8px}.effective-weeks{width:100%}.effective-weeks .el-select{flex:1}.edit-mode-tip{margin-top:7px;color:#8492a6;font-size:12px}.week-range>span{color:#7f8b9d;font-size:12px}.week-range .el-select,.week-navigation .el-select{width:112px}.week-navigation{margin-left:auto}.schedule-line{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:3px 0;transition:transform var(--duration-fast)}.schedule-line:hover{transform:translateX(3px);color:var(--accent)}.schedule-tip,.conflict-alert{margin-bottom:18px}.content-swap-enter-active,.content-swap-leave-active{transition:opacity var(--duration-normal),transform var(--duration-normal)}.content-swap-enter-from{opacity:0;transform:translateY(5px)}.content-swap-leave-to{opacity:0;transform:translateY(-3px)}@media(max-width:1100px){.semester-panel{flex-wrap:wrap}.week-navigation{margin-left:0}}@media(max-width:700px){.semester-panel,.week-range,.week-navigation{align-items:flex-start}.week-range,.week-navigation{flex-wrap:wrap}.semester-title{width:100%}}
</style>

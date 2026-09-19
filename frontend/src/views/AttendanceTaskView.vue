<template>
  <div class="page">
    <el-card class="page-card">
      <div class="toolbar"><el-select v-model="filter" clearable placeholder="筛选课程" style="width:260px" @change="load"><el-option v-for="item in teaching" :key="item.id" :label="`${item.courseName} / ${item.className}`" :value="item.id" /></el-select><el-button :loading="loading" @click="load">查询</el-button><div class="spacer" /><el-button type="primary" @click="visible = true"><el-icon><Plus /></el-icon>创建考勤</el-button></div>
      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading && !rows.length" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column prop="id" label="任务编号" width="100" /><el-table-column label="课程/班级" min-width="180"><template #default="{ row }">{{ label(row.courseClassId) }}</template></el-table-column><el-table-column prop="attendanceDate" label="考勤日期" /><el-table-column prop="remark" label="备注"><template #default="{ row }">{{ row.remark || '—' }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column><el-table-column label="操作" width="280"><template #default="{ row }"><el-button link type="primary" @click="$router.push(`/attendance/entry/${row.id}`)">录入</el-button><el-button v-if="row.status === 'OPEN'" link type="success" :loading="openingId === row.id" @click="openSign(row)">开启签到</el-button><el-button v-if="row.status === 'OPEN'" link :loading="closingId === row.id" @click="close(row)">停止考勤</el-button></template></el-table-column>
        <template #empty><AppState title="暂无考勤任务" description="点击右上角创建本次课程考勤" /></template>
      </el-table>
    </el-card>
    <el-dialog v-model="visible" title="创建考勤任务" width="500px" append-to-body align-center><el-form :model="form" label-width="90px"><el-form-item label="教学班" required><el-select v-model="form.courseClassId" style="width:100%"><el-option v-for="item in teaching" :key="item.id" :label="`${item.courseName} / ${item.className}`" :value="item.id" /></el-select></el-form-item><el-form-item label="日期" required><el-date-picker v-model="form.attendanceDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="creating" @click="create">创建</el-button></template></el-dialog>
    <el-dialog v-model="signVisible" title="课堂密码签到" width="460px" append-to-body align-center @closed="stopTimers">
      <div v-if="signData" class="sign-panel">
        <StatusBadge :status="signData.status" />
        <div class="sign-code">{{ signData.signCode }}</div>
        <div class="countdown" :class="{ urgent: signRemaining <= 10 }">{{ signData.status === 'OPEN' ? `${signRemaining} 秒后失效` : signData.status === 'EXPIRED' ? '签到码已过期' : '签到已结束' }}</div>
        <el-progress :percentage="Math.round(signRemaining / 60 * 100)" :show-text="false" :status="signRemaining <= 10 ? 'exception' : ''" />
        <div class="sign-count"><strong>{{ signData.signedCount }}</strong><span>/ {{ signData.totalCount }} 人已签到</span></div>
      </div>
      <template #footer><el-button @click="signVisible = false">关闭面板</el-button><el-button v-if="signData?.status === 'OPEN'" type="danger" plain :loading="closingSession" @click="closeSign">提前结束签到</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'
const rows = ref([]), teaching = ref([]), loading = ref(false), visible = ref(false), filter = ref(), error = ref(false), creating = ref(false), closingId = ref(null)
const signVisible = ref(false), signData = ref(null), signRemaining = ref(0), openingId = ref(null), closingSession = ref(false)
let countdownTimer, pollTimer, serverOffset = 0
const form = reactive({ courseClassId: '', attendanceDate: new Date().toISOString().slice(0, 10), remark: '' })
const label = id => { const item = teaching.value.find(value => value.id === id); return item ? `${item.courseName} / ${item.className}` : `#${id}` }
async function load() { loading.value = true; error.value = false; try { rows.value = await request.get('/attendance/tasks', { params: { courseClassId: filter.value || undefined } }) } catch { error.value = true } finally { loading.value = false } }
async function create() { creating.value = true; try { await request.post('/attendance/tasks', form); ElMessage.success({ message: '考勤任务已创建', showClose: true }); visible.value = false; await load() } finally { creating.value = false } }
async function close(row) { closingId.value = row.id; try { await request.put(`/attendance/tasks/${row.id}/status`, null, { params: { status: 'CLOSED' } }); row.status = 'CLOSED'; ElMessage.success({ message: '考勤已停止，20分钟内仍可修改', showClose: true }) } finally { closingId.value = null } }
function syncSession(data) { signData.value = data; serverOffset = Date.parse(data.serverTime) - Date.now(); tick() }
function tick() { if (!signData.value) return; signRemaining.value = Math.max(0, Math.ceil((Date.parse(signData.value.expiresAt) - Date.now() - serverOffset) / 1000)); if (!signRemaining.value && signData.value.status === 'OPEN') signData.value.status = 'EXPIRED' }
function stopTimers() { clearInterval(countdownTimer); clearInterval(pollTimer); countdownTimer = pollTimer = null }
function startTimers() { stopTimers(); countdownTimer = setInterval(tick, 250); pollTimer = setInterval(refreshSession, 2000) }
async function openSign(row) { openingId.value = row.id; try { syncSession(await request.post('/attendance/sessions', { taskId: row.id })); signVisible.value = true; startTimers() } finally { openingId.value = null } }
async function refreshSession() { if (!signVisible.value || !signData.value?.sessionId) return; try { syncSession(await request.get(`/attendance/sessions/${signData.value.sessionId}`)) } catch { stopTimers() } }
async function closeSign() { closingSession.value = true; try { syncSession(await request.post(`/attendance/sessions/${signData.value.sessionId}/close`)); stopTimers(); ElMessage.success('签到已结束') } finally { closingSession.value = false } }
onMounted(async () => { try { teaching.value = await request.get('/teaching') } finally { await load() } })
onBeforeUnmount(stopTimers)
</script>
<style scoped>.sign-panel{text-align:center;padding:8px 14px 20px}.sign-code{margin:20px 0 4px;color:#245fae;font-size:58px;font-weight:800;letter-spacing:12px;font-variant-numeric:tabular-nums}.countdown{margin-bottom:18px;color:#60708a}.countdown.urgent{color:#e45353;font-weight:700}.sign-count{margin-top:24px}.sign-count strong{margin-right:6px;color:#245fae;font-size:30px}.sign-count span{color:#7f8b9e}</style>

<template>
  <div class="page">
    <el-card class="page-card">
      <div class="toolbar"><el-select v-model="filter" clearable placeholder="筛选课程" style="width:260px" @change="load"><el-option v-for="item in teaching" :key="item.id" :label="`${item.courseName} / ${item.className}`" :value="item.id" /></el-select><el-button :loading="loading" @click="load">查询</el-button><div class="spacer" /><el-button type="primary" @click="visible = true"><el-icon><Plus /></el-icon>创建考勤</el-button></div>
      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading && !rows.length" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column prop="id" label="任务编号" width="100" /><el-table-column label="课程/班级" min-width="180"><template #default="{ row }">{{ label(row.courseClassId) }}</template></el-table-column><el-table-column prop="attendanceDate" label="考勤日期" /><el-table-column prop="remark" label="备注"><template #default="{ row }">{{ row.remark || '—' }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column><el-table-column label="操作" width="190"><template #default="{ row }"><el-button link type="primary" @click="$router.push(`/attendance/entry/${row.id}`)">录入</el-button><el-button v-if="row.status === 'OPEN'" link :loading="closingId === row.id" @click="close(row)">停止考勤</el-button></template></el-table-column>
        <template #empty><AppState title="暂无考勤任务" description="点击右上角创建本次课程考勤" /></template>
      </el-table>
    </el-card>
    <el-dialog v-model="visible" title="创建考勤任务" width="500px" append-to-body align-center><el-form :model="form" label-width="90px"><el-form-item label="教学班" required><el-select v-model="form.courseClassId" style="width:100%"><el-option v-for="item in teaching" :key="item.id" :label="`${item.courseName} / ${item.className}`" :value="item.id" /></el-select></el-form-item><el-form-item label="日期" required><el-date-picker v-model="form.attendanceDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="creating" @click="create">创建</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'
const rows = ref([]), teaching = ref([]), loading = ref(false), visible = ref(false), filter = ref(), error = ref(false), creating = ref(false), closingId = ref(null)
const form = reactive({ courseClassId: '', attendanceDate: new Date().toISOString().slice(0, 10), remark: '' })
const label = id => { const item = teaching.value.find(value => value.id === id); return item ? `${item.courseName} / ${item.className}` : `#${id}` }
async function load() { loading.value = true; error.value = false; try { rows.value = await request.get('/attendance/tasks', { params: { courseClassId: filter.value || undefined } }) } catch { error.value = true } finally { loading.value = false } }
async function create() { creating.value = true; try { await request.post('/attendance/tasks', form); ElMessage.success({ message: '考勤任务已创建', showClose: true }); visible.value = false; await load() } finally { creating.value = false } }
async function close(row) { closingId.value = row.id; try { await request.put(`/attendance/tasks/${row.id}/status`, null, { params: { status: 'CLOSED' } }); row.status = 'CLOSED'; ElMessage.success({ message: '考勤已停止，20分钟内仍可修改', showClose: true }) } finally { closingId.value = null } }
onMounted(async () => { try { teaching.value = await request.get('/teaching') } finally { await load() } })
</script>

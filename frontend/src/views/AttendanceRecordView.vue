<template>
  <div class="page">
    <el-card class="page-card">
      <div class="toolbar">
        <el-date-picker v-model="date" value-format="YYYY-MM-DD" placeholder="选择考勤日期" clearable @change="load" />
        <el-button type="primary" :loading="loading" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading && !rows.length" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column prop="attendanceDate" label="考勤日期" min-width="120" />
        <el-table-column prop="studentNo" label="学号" min-width="130" />
        <el-table-column prop="studentName" label="学生姓名" min-width="110" />
        <el-table-column prop="courseName" label="课程" min-width="140"><template #default="{ row }">{{ row.courseName || `课程 #${row.courseId}` }}</template></el-table-column>
        <el-table-column label="考勤状态" min-width="110"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160"><template #default="{ row }">{{ row.remark || '—' }}</template></el-table-column>
        <template #empty><AppState title="暂无异常考勤记录" description="当前筛选范围内没有迟到、早退、请假或缺勤记录" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '../utils/request'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'
const rows = ref([]), date = ref(''), loading = ref(false), error = ref(false)
async function load() {
  loading.value = true
  error.value = false
  try { rows.value = await request.get('/attendance/records', { params: { date: date.value || undefined } }) }
  catch { error.value = true }
  finally { loading.value = false }
}
onMounted(load)
</script>

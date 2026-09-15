<template>
  <div class="page"><el-card class="page-card">
    <div class="toolbar"><el-select v-model="status" clearable placeholder="审批状态" @change="load"><el-option label="待审批" value="PENDING" /><el-option label="已通过" value="APPROVED" /><el-option label="已拒绝" value="REJECTED" /></el-select><el-button :loading="loading" @click="load">查询</el-button></div>
    <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
    <AppSkeleton v-else-if="loading && !rows.length" />
    <el-table v-else v-loading="loading" :data="rows" stripe>
      <el-table-column prop="studentId" label="学生ID" /><el-table-column prop="startTime" label="开始时间" min-width="160" /><el-table-column prop="endTime" label="结束时间" min-width="160" /><el-table-column prop="reason" label="原因" min-width="150" /><el-table-column label="状态"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column><el-table-column label="操作" width="150"><template #default="{ row }"><template v-if="row.status === 'PENDING'"><el-button link type="success" :loading="processingId === row.id" @click="approve(row, 'APPROVED')">通过</el-button><el-button link type="danger" :disabled="processingId === row.id" @click="approve(row, 'REJECTED')">拒绝</el-button></template><span v-else>已处理</span></template></el-table-column>
      <template #empty><AppState title="暂无待审批申请" description="新的请假申请会显示在这里" /></template>
    </el-table>
  </el-card></div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'
const rows = ref([]), status = ref('PENDING'), loading = ref(false), error = ref(false), processingId = ref(null)
async function load() { loading.value = true; error.value = false; try { rows.value = await request.get('/leaves', { params: { status: status.value || undefined } }) } catch { error.value = true } finally { loading.value = false } }
async function approve(row, value) { const { value: comment } = await ElMessageBox.prompt('可填写审批意见', value === 'APPROVED' ? '确认通过' : '确认拒绝', { inputPlaceholder: '审批意见（可选）' }); processingId.value = row.id; try { await request.put(`/leaves/${row.id}/approval`, { status: value, comment }); row.status = value; ElMessage.success({ message: '审批完成', showClose: true }); if (status.value === 'PENDING') rows.value = rows.value.filter(item => item.id !== row.id) } finally { processingId.value = null } }
onMounted(load)
</script>

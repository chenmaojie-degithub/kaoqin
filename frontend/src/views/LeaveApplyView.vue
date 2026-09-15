<template>
  <div class="page">
    <el-card class="page-card">
      <div class="toolbar"><h3 class="section-title">我的请假申请</h3><div class="spacer" /><el-button type="primary" @click="visible = true"><el-icon><Plus /></el-icon>提交请假</el-button></div>
      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading && !rows.length" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column prop="startTime" label="开始时间" min-width="160" /><el-table-column prop="endTime" label="结束时间" min-width="160" /><el-table-column prop="reason" label="请假原因" min-width="160" /><el-table-column label="审批状态"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column><el-table-column prop="approvalComment" label="审批意见"><template #default="{ row }">{{ row.approvalComment || '—' }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="row.status === 'PENDING'" link type="danger" :loading="cancelingId === row.id" @click="cancel(row)">撤销</el-button><span v-else>—</span></template></el-table-column>
        <template #empty><AppState title="暂无请假申请" description="有需要时可点击右上角提交请假" /></template>
      </el-table>
    </el-card>
    <el-dialog v-model="visible" title="提交请假申请" width="520px" append-to-body align-center><el-form ref="formRef" :model="form" :rules="rules" label-width="90px" status-icon><el-form-item label="时间范围" prop="range"><el-date-picker v-model="form.range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" style="width:100%" /></el-form-item><el-form-item label="请假原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">提交</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'
const rows = ref([]), loading = ref(false), saving = ref(false), visible = ref(false), formRef = ref(), error = ref(false), cancelingId = ref(null)
const form = reactive({ range: [], reason: '' })
const rules = { range: [{ required: true, message: '请选择请假时间', trigger: 'change' }], reason: [{ required: true, message: '请填写原因', trigger: 'blur' }] }
async function load() { loading.value = true; error.value = false; try { rows.value = await request.get('/leaves') } catch { error.value = true } finally { loading.value = false } }
async function save() { await formRef.value.validate(); saving.value = true; try { await request.post('/leaves', { startTime: form.range[0], endTime: form.range[1], reason: form.reason }); ElMessage.success({ message: '申请已提交', showClose: true }); visible.value = false; form.range = []; form.reason = ''; await load() } finally { saving.value = false } }
async function cancel(row) { await ElMessageBox.confirm('确定撤销这条请假申请吗？', '撤销确认', { type: 'warning' }); cancelingId.value = row.id; try { await request.delete(`/leaves/${row.id}`); ElMessage.success({ message: '申请已撤销', showClose: true }); await load() } finally { cancelingId.value = null } }
onMounted(load)
</script>

<template>
  <div class="page">
    <el-card class="page-card">
      <div class="toolbar"><div><h3 class="section-title">批量录入考勤</h3><span class="muted">任务 #{{ $route.params.id }}，只保存缺勤等异常，正常学生不产生考勤记录</span></div><div class="spacer" /><el-tag v-if="!editable" type="danger">已超过修改期限</el-tag><el-tag v-else-if="editableUntil" type="warning">可修改至 {{ formatTime(editableUntil) }}</el-tag><el-button :disabled="!editable" @click="allNormal">全部正常</el-button><el-button type="primary" :disabled="!editable || !dirtyCount" :loading="saving" @click="save">{{ saved ? '已保存' : dirtyCount ? `保存 ${dirtyCount} 项修改` : '没有待保存修改' }}</el-button></div>
      <el-alert v-if="editableUntil && editable" title="考勤已停止，停止后20分钟内仍可更改；到期后任何角色都不能修改。" type="warning" show-icon :closable="false" class="tip" />
      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading && !rows.length" :rows="8" />
      <el-table v-else v-loading="loading" :data="rows" stripe :row-class-name="rowClassName">
        <el-table-column prop="studentNo" label="学号" /><el-table-column prop="name" label="姓名" />
        <el-table-column label="考勤状态" width="250"><template #default="{ row }"><StatusBadge v-if="row.status === 'LEAVE'" status="LEAVE" /><el-radio-group v-else v-model="row.status" :disabled="!editable"><el-radio-button value="NORMAL">正常</el-radio-button><el-radio-button value="ABSENT">缺勤</el-radio-button></el-radio-group></template></el-table-column>
        <el-table-column label="备注"><template #default="{ row }"><el-input v-model="row.remark" :disabled="!editable || row.status === 'LEAVE'" :placeholder="row.status === 'ABSENT' ? '可填写缺勤原因' : '正常无需备注'" /></template></el-table-column>
        <template #empty><AppState title="暂无学生名单" description="请检查该考勤任务关联的课程班级" /></template>
      </el-table>
    </el-card>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
import StatusBadge from '../components/StatusBadge.vue'
const route = useRoute(), rows = ref([]), loading = ref(false), saving = ref(false), error = ref(false), saved = ref(false), original = ref(new Map())
let savedTimer
const editable = computed(() => rows.value.length ? rows.value[0].editable : true)
const editableUntil = computed(() => rows.value[0]?.editableUntil)
const dirtyIds = computed(() => new Set(rows.value.filter(row => { const before = original.value.get(row.studentId); return before && (before.status !== row.status || before.remark !== (row.remark || '')) }).map(row => row.studentId)))
const dirtyCount = computed(() => dirtyIds.value.size)
const formatTime = value => value ? value.replace('T', ' ').slice(0, 16) : ''
function allNormal() { rows.value.forEach(item => { if (item.status !== 'LEAVE') { item.status = 'NORMAL'; item.remark = '' } }) }
async function load() { loading.value = true; error.value = false; try { const data = await request.get(`/attendance/tasks/${route.params.id}/students`); rows.value = data.map(row => ({ ...row, remark: row.remark || '', status: row.status === 'LEAVE' ? 'LEAVE' : row.status === 'ABSENT' ? 'ABSENT' : 'NORMAL' })); original.value = new Map(rows.value.map(row => [row.studentId, { status: row.status, remark: row.remark }])) } catch { error.value = true } finally { loading.value = false } }
async function save() { saving.value = true; try { const payload = rows.value.filter(row => row.status !== 'LEAVE').map(({ studentId, status, remark }) => ({ studentId, status, remark })); await request.put(`/attendance/tasks/${route.params.id}/records`, payload); saved.value = true; ElMessage.success({ message: `考勤保存成功，仅记录 ${payload.filter(x => x.status === 'ABSENT').length} 条缺勤异常`, showClose: true }); await load(); clearTimeout(savedTimer); savedTimer = setTimeout(() => { saved.value = false }, 1100) } finally { saving.value = false } }
const rowClassName = ({ row }) => dirtyIds.value.has(row.studentId) ? 'row-dirty' : ''
onMounted(load)
onBeforeUnmount(() => clearTimeout(savedTimer))
</script>
<style scoped>.section-title{margin:0 0 6px}.tip{margin-bottom:16px}:deep(.row-dirty td.el-table__cell){background:rgba(230,162,60,.075)!important;transition:background var(--duration-normal)}</style>

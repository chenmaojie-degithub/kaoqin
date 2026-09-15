<template>
  <div class="page">
    <el-card class="page-card">
      <div class="toolbar">
        <el-input v-model="keyword" clearable :placeholder="`搜索${title}`" style="width:240px" @keyup.enter="searchNow"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button type="primary" :loading="loading" @click="searchNow">查询</el-button>
        <div class="grow" /><el-button type="primary" @click="open()"><el-icon><Plus /></el-icon>新增{{ title }}</el-button>
      </div>

      <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
      <AppSkeleton v-else-if="loading && !rows.length" />
      <el-table v-else v-loading="loading" :data="rows" stripe :row-class-name="rowClassName">
        <el-table-column type="index" width="60" label="#" />
        <el-table-column v-for="field in tableFields" :key="field.key" :prop="field.key" :label="field.label" :min-width="field.width || 110">
          <template v-if="field.type === 'status'" #default="{ row }"><el-tag :type="row[field.key] === 1 ? 'success' : 'info'">{{ row[field.key] === 1 ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="150"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" :loading="deletingId === row.id" @click="remove(row)">删除</el-button></template></el-table-column>
        <template #empty><AppState :title="keyword ? '没有匹配结果' : `暂无${title}数据`" :description="keyword ? '换一个关键词试试' : `点击右上角新增${title}`" /></template>
      </el-table>
      <div v-if="paged && !error" class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" layout="total,prev,pager,next" :total="total" @change="load" /></div>
    </el-card>

    <el-dialog v-model="visible" :title="`${editing ? '编辑' : '新增'}${title}`" width="560px" destroy-on-close append-to-body align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px" status-icon>
        <el-form-item v-for="field in formFields" :key="field.key" :label="field.label" :prop="field.key">
          <el-select v-if="field.type === 'select'" v-model="form[field.key]" style="width:100%"><el-option v-for="option in options[field.key] || field.options" :key="option.value" :label="option.label" :value="option.value" /></el-select>
          <el-input-number v-else-if="field.type === 'number'" v-model="form[field.key]" :min="0" style="width:100%" />
          <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.key]" value-format="YYYY-MM-DD" style="width:100%" />
          <el-input v-else v-model="form[field.key]" :type="field.type === 'textarea' ? 'textarea' : field.type === 'password' ? 'password' : 'text'" :show-password="field.type === 'password'" :placeholder="field.placeholder || `请输入${field.label}`" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import AppSkeleton from './AppSkeleton.vue'
import AppState from './AppState.vue'

const props = defineProps({ title: String, endpoint: String, fields: Array, paged: { type: Boolean, default: true }, keywordParam: { type: String, default: 'keyword' } })
const rows = ref([]), loading = ref(false), saving = ref(false), visible = ref(false), editing = ref(null), keyword = ref(''), page = ref(1), size = ref(10), total = ref(0), form = reactive({}), formRef = ref(), options = reactive({})
const error = ref(false), deletingId = ref(null), recentId = ref(null)
let searchTimer, savedTimer
const tableFields = computed(() => props.fields.filter(field => field.table !== false))
const formFields = computed(() => props.fields.filter(field => field.form !== false))
const rules = computed(() => Object.fromEntries(formFields.value.filter(field => field.required).map(field => [field.key, [{ required: true, message: `请填写${field.label}`, trigger: 'blur' }]])))

async function loadOptions() {
  for (const field of formFields.value.filter(item => item.optionsEndpoint)) {
    const data = await request.get(field.optionsEndpoint)
    options[field.key] = (data.records || data).map(item => ({ label: item[field.optionLabel || 'name'], value: item.id }))
  }
}
async function load() {
  loading.value = true
  error.value = false
  try {
    const params = props.paged ? { page: page.value, size: size.value } : {}
    if (keyword.value) params[props.keywordParam] = keyword.value
    const data = await request.get(props.endpoint, { params })
    rows.value = data.records || data
    total.value = data.total || rows.value.length
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
function searchNow() { clearTimeout(searchTimer); page.value = 1; load() }
function open(row) {
  editing.value = row?.id || null
  for (const field of formFields.value) form[field.key] = row?.[field.key] ?? field.default ?? ''
  if (row && props.title === '学生' && !form.username) form.username = row.studentNo
  if (row && props.title === '教师' && !form.username) form.username = row.teacherNo
  visible.value = true
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    editing.value ? await request.put(`${props.endpoint}/${editing.value}`, form) : await request.post(props.endpoint, form)
    recentId.value = editing.value
    ElMessage.success({ message: '保存成功', showClose: true })
    visible.value = false
    await load()
    clearTimeout(savedTimer)
    savedTimer = setTimeout(() => { recentId.value = null }, 1200)
  } finally { saving.value = false }
}
async function remove(row) {
  await ElMessageBox.confirm(`确定删除“${row.name || row.realName || row.username}”吗？`, '删除确认', { type: 'warning' })
  deletingId.value = row.id
  try {
    await request.delete(`${props.endpoint}/${row.id}`)
    ElMessage.success({ message: '删除成功', showClose: true })
    await load()
  } finally { deletingId.value = null }
}
const rowClassName = ({ row }) => row.id === recentId.value ? 'row-just-saved' : ''
watch(keyword, () => { clearTimeout(searchTimer); searchTimer = setTimeout(searchNow, 380) })
onMounted(async () => { try { await loadOptions() } catch { /* 表单选项失败不阻断列表 */ } await load() })
onBeforeUnmount(() => { clearTimeout(searchTimer); clearTimeout(savedTimer) })
</script>

<style scoped>
:deep(.row-just-saved td.el-table__cell){animation:row-saved 1.15s ease-out}@keyframes row-saved{0%{background:rgba(52,120,246,.18)}100%{background:transparent}}
</style>

<template>
  <div class="page">
    <el-card class="page-card">
      <template #header><div class="toolbar"><b>数据同步 · {{ data.nodeId || '未配置节点' }}</b><div class="spacer" /><el-button :loading="loading" @click="load">刷新状态</el-button><el-button type="primary" :disabled="!data.enabled || data.running || !!action" :loading="action === 'push'" @click="run('push')">手动 Push</el-button><el-button type="primary" :disabled="!data.enabled || data.running || !!action" :loading="action === 'pull'" @click="run('pull')">手动 Pull</el-button></div></template>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="!data.enabled" title="同步未启用，请由部署人员配置本机后端" type="info" :closable="false" />
      <el-table :data="data.peers || []" v-loading="loading">
        <el-table-column prop="nodeId" label="节点" /><el-table-column prop="url" label="地址" min-width="220" /><el-table-column prop="pending" label="待推送事件" width="120" />
        <el-table-column label="最近通信" min-width="320"><template #default="{ row }"><div v-for="state in row.directions" :key="state.direction"><el-tag :type="state.last_error ? 'danger' : state.last_success_at ? 'success' : 'info'">{{ state.direction }} · {{ state.last_error ? '失败' : state.last_success_at ? '最近成功' : '尚未连接' }}</el-tag> {{ state.last_success_at || '—' }} <span v-if="state.last_error">{{ state.last_error }} · 重试 {{ state.retry_at }}</span></div></template></el-table-column>
      </el-table>
      <p>已保存事件：{{ data.eventCount || 0 }} <span v-for="item in data.inbox || []" :key="item.status"> · {{ item.status }}：{{ item.count }}</span></p>
      <el-table :data="data.recentIssues || []"><el-table-column prop="event_id" label="最近待处理/冲突事件" min-width="300" /><el-table-column prop="status" label="状态" /><el-table-column prop="error_code" label="原因" min-width="240" /><el-table-column prop="updated_at" label="最近处理时间" /></el-table>
    </el-card>
  </div>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import request from '../utils/request'
const data = ref({}), loading = ref(false), action = ref(''), error = ref('')
let timer, disposed = false
async function load() {
  if (loading.value || disposed) return
  loading.value = true
  try { const value = await request.get('/sync/admin/status', { silent: true }); if (!disposed) { data.value = value; error.value = '' } }
  catch { error.value = '无法读取同步状态，请稍后刷新' }
  finally { loading.value = false }
}
async function run(direction) {
  if (action.value) return
  action.value = direction
  try { await request.post(`/sync/admin/${direction}`, null, { timeout: 130000 }); await load() }
  catch { error.value = '同步触发失败，请查看节点通信状态' }
  finally { action.value = '' }
}
onMounted(() => { load(); timer = setInterval(load, 5000) })
onBeforeUnmount(() => { disposed = true; clearInterval(timer) })
</script>

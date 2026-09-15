<template>
  <div class="page"><el-card class="page-card"><template #header><div class="head"><div><h3>通知中心</h3><span>考勤通知、审批结果与异常提醒 · {{ unreadCount }} 条未读</span></div><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></div></template>
    <AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" />
    <AppSkeleton v-else-if="loading && !rows.length" :rows="5" />
    <AppState v-else-if="!rows.length" title="暂无通知" description="新的考勤或审批消息会显示在这里" />
    <TransitionGroup v-else name="list-rise" tag="div">
      <button v-for="item in rows" :key="item.key" type="button" class="notice-row" :class="{ unread: !isRead(item) }" @click="markRead(item)"><div class="icon" :class="item.type.toLowerCase()"><el-icon><component :is="icons[item.type]" /></el-icon></div><div class="body"><div><b>{{ item.title }}</b><span v-if="!isRead(item)" class="unread-dot">未读</span><el-tag size="small" effect="plain">{{ typeText[item.type] }}</el-tag></div><p>{{ item.content }}</p><span>{{ format(item.time) }}</span></div><el-icon class="arrow"><ArrowRight /></el-icon></button>
    </TransitionGroup>
  </el-card></div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../utils/request'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
const rows = ref([]), loading = ref(false), error = ref(false)
const storageKey = 'attendance-read-notifications'
function loadReadKeys() { try { const saved = JSON.parse(localStorage.getItem(storageKey) || '[]'); return new Set(Array.isArray(saved) ? saved : []) } catch { return new Set() } }
const readKeys = ref(loadReadKeys())
const icons = { TASK: 'Bell', LEAVE: 'DocumentChecked', ATTENDANCE: 'Warning' }, typeText = { TASK: '考勤通知', LEAVE: '请假审批', ATTENDANCE: '异常提醒' }
const format = value => value ? value.replace('T', ' ').slice(0, 16) : ''
const isRead = item => readKeys.value.has(item.key)
const unreadCount = computed(() => rows.value.filter(item => !isRead(item)).length)
function markRead(item) { if (isRead(item)) return; readKeys.value = new Set([...readKeys.value, item.key]); localStorage.setItem(storageKey, JSON.stringify([...readKeys.value])) }
async function load() { loading.value = true; error.value = false; try { const data = await request.get('/student-portal/notifications'); rows.value = data.map(item => ({ ...item, key: `${item.type}|${item.title}|${item.time}` })) } catch { error.value = true } finally { loading.value = false } }
onMounted(load)
</script>
<style scoped>.head{display:flex;align-items:center;justify-content:space-between}.head h3,.head span{margin:0}.head span{display:block;margin-top:6px;color:#8b96a8;font-size:13px}.notice-row{position:relative;width:100%;display:flex;align-items:center;gap:15px;padding:18px 8px;border:0;border-bottom:1px solid #edf1f6;background:transparent;text-align:left;color:inherit;cursor:pointer;transition:background var(--duration-fast),transform var(--duration-fast)}.notice-row:hover{background:rgba(var(--accent-rgb),.035);transform:translateX(3px)}.notice-row.unread{background:rgba(var(--accent-rgb),.025)}.icon{width:44px;height:44px;flex:0 0 44px;border-radius:13px;display:grid;place-items:center;background:#eaf3ff;color:#3478f6;font-size:20px}.icon.leave{background:#eaf8f1;color:#1f9d68}.icon.attendance{background:#fff0f0;color:#dd5050}.body{flex:1}.body>div{display:flex;align-items:center;gap:9px}.body p{margin:8px 0;color:#566176}.body span{color:#9aa5b5;font-size:12px}.unread-dot{padding:2px 6px;border-radius:8px;background:#3478f6!important;color:#fff!important}.arrow{color:#aab4c3;transition:transform var(--duration-fast)}.notice-row:hover .arrow{transform:translateX(3px)}</style>

<template>
  <el-drawer :model-value="modelValue" title="考勤详情" size="min(420px, 92vw)" append-to-body @close="$emit('update:modelValue', false)">
    <div v-if="record" class="detail-drawer">
      <div class="detail-hero"><div><small>考勤状态</small><StatusBadge :status="record.status" /></div><el-icon><Calendar /></el-icon></div>
      <dl><div><dt>课程</dt><dd>{{ record.courseName || `课程 #${record.courseId || '—'}` }}</dd></div><div><dt>日期</dt><dd>{{ record.attendanceDate || '—' }}</dd></div><div v-if="record.studentNo"><dt>学号</dt><dd>{{ record.studentNo }}</dd></div><div v-if="record.studentName"><dt>姓名</dt><dd>{{ record.studentName }}</dd></div><div><dt>备注</dt><dd>{{ record.remark || '无备注' }}</dd></div></dl>
      <el-alert title="此处仅展示系统已有的考勤数据" description="签到时间等未由当前接口提供的信息不会在此虚构显示。" type="info" :closable="false" show-icon />
    </div>
  </el-drawer>
</template>
<script setup>
import StatusBadge from './StatusBadge.vue'
defineProps({ modelValue: Boolean, record: Object })
defineEmits(['update:modelValue'])
</script>
<style scoped>
.detail-hero{display:flex;align-items:center;justify-content:space-between;padding:22px;border-radius:16px;background:linear-gradient(135deg,rgba(var(--accent-rgb),.1),rgba(var(--accent-rgb),.025));border:1px solid rgba(var(--accent-rgb),.1)}.detail-hero>div{display:flex;flex-direction:column;align-items:flex-start;gap:9px}.detail-hero small{color:#7d899b}.detail-hero>.el-icon{font-size:38px;color:rgba(var(--accent-rgb),.45)}dl{margin:24px 0}dl>div{display:grid;grid-template-columns:85px 1fr;gap:12px;padding:14px 4px;border-bottom:1px solid #edf1f7}dt{color:#8995a8}dd{margin:0;color:#29384f}
</style>

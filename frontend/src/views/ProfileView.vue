<template>
  <div class="page profile-page"><el-row :gutter="18"><el-col :xs="24" :lg="15"><el-card class="page-card"><template #header><b>个人资料</b></template><AppState v-if="error" type="error" action="重新加载" :loading="loading" @action="load" /><AppSkeleton v-else-if="loading" type="profile" /><el-descriptions v-else :column="2" border><el-descriptions-item label="姓名">{{ profile.name }}</el-descriptions-item><el-descriptions-item label="学号">{{ profile.studentNo }}</el-descriptions-item><el-descriptions-item label="班级">{{ profile.className }}</el-descriptions-item><el-descriptions-item label="登录账号">{{ profile.username }}</el-descriptions-item><el-descriptions-item label="性别">{{ profile.gender || '未填写' }}</el-descriptions-item><el-descriptions-item label="入学日期">{{ profile.enrollmentDate || '未填写' }}</el-descriptions-item><el-descriptions-item label="手机">{{ profile.phone || '未填写' }}</el-descriptions-item><el-descriptions-item label="邮箱">{{ profile.email || '未填写' }}</el-descriptions-item></el-descriptions></el-card></el-col><el-col :xs="24" :lg="9"><el-card class="page-card security"><template #header><b>账号安全</b></template><el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon><el-form-item label="当前密码" prop="currentPassword"><el-input v-model="form.currentPassword" type="password" show-password /></el-form-item><el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password /></el-form-item><el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password /></el-form-item><el-button type="primary" :loading="saving" @click="changePassword">修改密码</el-button><el-button type="danger" plain @click="logout">退出登录</el-button></el-form></el-card></el-col></el-row></div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { useAuthStore } from '../stores/auth'
import AppSkeleton from '../components/AppSkeleton.vue'
import AppState from '../components/AppState.vue'
const auth = useAuthStore(), router = useRouter(), profile = ref({}), loading = ref(false), saving = ref(false), formRef = ref(), error = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const confirm = (rule, value, callback) => value !== form.newPassword ? callback(new Error('两次输入的新密码不一致')) : callback()
const rules = { currentPassword: [{ required: true, message: '请输入当前密码' }], newPassword: [{ required: true, message: '请输入新密码' }, { min: 6, max: 50, message: '密码长度为6-50位' }], confirmPassword: [{ required: true, message: '请再次输入新密码' }, { validator: confirm }] }
async function changePassword() { await formRef.value.validate(); saving.value = true; try { await request.put('/student-portal/password', { currentPassword: form.currentPassword, newPassword: form.newPassword }); ElMessage.success({ message: '密码修改成功，请重新登录', showClose: true }); await auth.logout(); router.replace('/login') } finally { saving.value = false } }
async function logout() { await auth.logout(); router.replace('/login') }
async function load() { loading.value = true; error.value = false; try { profile.value = await request.get('/student-portal/profile') } catch { error.value = true } finally { loading.value = false } }
onMounted(load)
</script>
<style scoped>.profile-page{max-width:1200px;margin:auto}.security{min-height:350px}.security :deep(.el-input__wrapper){transition:box-shadow var(--duration-fast),transform var(--duration-fast)}.security :deep(.el-input__wrapper.is-focus){transform:translateY(-1px)}@media(max-width:1200px){.security{margin-top:18px}}</style>

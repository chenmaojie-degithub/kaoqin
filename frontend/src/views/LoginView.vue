<template>
  <div class="login">
    <KageBackground />
    <div class="login-scrim" />
    <main class="login-content">
      <section class="intro">
        <div class="eyebrow"><span /> ATTENDANCE · 2026</div>
        <div class="logo"><el-icon><Calendar /></el-icon></div>
        <h1>学生考勤<br>管理系统</h1>
        <p>清晰掌握每一次课堂出勤，让教学管理更高效。</p>
        <div class="intro-line"><i /> 实时记录 · 精准统计 · 权限清晰</div>
      </section>

      <el-card class="login-card">
        <div class="card-mark"><span /> SECURE ACCESS</div>
        <h2>欢迎登录</h2>
        <p class="muted">请使用系统分配的账号进入</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" prefix-icon="User" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" prefix-icon="Lock" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="submit" @click="submit">登录系统</el-button>
        </el-form>
        <div class="demo">测试账号：admin / teacher / student，密码 123456</div>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KageBackground from '../components/KageBackground.vue'
import { useAuthStore } from '../stores/auth'

const form = reactive({ username: 'admin', password: '123456' })
const formRef = ref()
const loading = ref(false)
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login(form)
    router.replace(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login{position:relative;min-height:100%;height:100%;overflow:hidden;isolation:isolate;background:#03070c}
.login-scrim{position:absolute;inset:0;z-index:1;pointer-events:none;background:linear-gradient(90deg,rgba(2,7,12,.18),rgba(2,7,12,.08) 48%,rgba(2,5,9,.5)),linear-gradient(180deg,rgba(0,0,0,.1),rgba(0,2,5,.3))}
.login-content{position:relative;z-index:2;width:min(1240px,calc(100% - 80px));min-height:100%;margin:0 auto;display:grid;grid-template-columns:minmax(0,1fr) minmax(360px,440px);align-items:center;gap:clamp(48px,9vw,150px);padding:60px 0}
.intro{color:#edf4f5;max-width:570px;text-shadow:0 2px 28px rgba(0,0,0,.8)}
.eyebrow,.card-mark,.intro-line{display:flex;align-items:center;gap:10px;font-size:10px;letter-spacing:.22em;text-transform:uppercase}
.eyebrow{margin-bottom:30px;color:rgba(222,235,235,.68)}
.eyebrow span,.card-mark span{width:6px;height:6px;border-radius:50%;background:#ed3b2f;box-shadow:0 0 14px rgba(237,59,47,.9)}
.logo{width:66px;height:66px;display:grid;place-items:center;border:1px solid rgba(223,236,237,.24);border-radius:2px;color:#f1f7f6;font-size:31px;background:rgba(8,20,29,.32);backdrop-filter:blur(8px)}
.intro h1{margin:25px 0 17px;font-size:clamp(42px,5vw,68px);line-height:1.08;letter-spacing:.015em;font-weight:500}
.intro p{max-width:430px;margin:0;color:rgba(220,232,232,.76);font-size:17px;line-height:1.8}
.intro-line{margin-top:36px;color:rgba(197,215,216,.52);letter-spacing:.12em}.intro-line i{width:54px;height:1px;background:linear-gradient(90deg,#e23b30,rgba(226,59,48,.08))}
.login-card{width:100%;color:#edf4f5;border:1px solid rgba(218,232,235,.15);border-radius:16px;background:rgba(7,16,25,.84);box-shadow:0 30px 90px rgba(0,0,0,.48),inset 0 1px 0 rgba(255,255,255,.05);backdrop-filter:blur(22px) saturate(1.15)}
.login-card :deep(.el-card__body){padding:38px 40px 36px}.card-mark{margin-bottom:23px;color:rgba(204,219,221,.5)}
.login-card h2{margin:0 0 7px;color:#f7fbfb;font-size:30px;font-weight:550;letter-spacing:.02em}.login-card .muted{margin:0 0 28px;color:rgba(211,224,226,.62)}
.login-card :deep(.el-form-item__label){color:rgba(232,240,241,.82);font-weight:500}.login-card :deep(.el-input__wrapper){min-height:46px;background:rgba(246,250,250,.96);box-shadow:0 0 0 1px rgba(227,238,240,.14) inset}.login-card :deep(.el-input__wrapper.is-focus){box-shadow:0 0 0 2px rgba(229,61,48,.82) inset}.login-card :deep(.el-input__inner){color:#17232d}
.submit{width:100%;margin-top:9px;border:0;background:linear-gradient(105deg,#b92822,#ef4939);box-shadow:0 11px 28px rgba(196,43,34,.3)}.submit:hover,.submit:focus{background:linear-gradient(105deg,#d3342a,#ff5a46)}
.demo{margin-top:23px;padding:12px 14px;border:1px solid rgba(216,230,232,.09);border-radius:8px;background:rgba(255,255,255,.045);color:rgba(211,224,226,.58);font-size:12px;line-height:1.55}
@media(max-width:850px){.login{overflow-y:auto}.login-content{width:min(calc(100% - 32px),470px);grid-template-columns:1fr;align-content:center;gap:24px;padding:28px 0}.intro{text-align:center}.eyebrow,.intro p,.intro-line{display:none}.logo{width:48px;height:48px;margin:0 auto;font-size:24px}.intro h1{margin:15px 0 0;font-size:30px;line-height:1.15}.intro h1 br{display:none}.login-card :deep(.el-card__body){padding:30px 26px 28px}}
@media(max-height:680px) and (min-width:851px){.login-content{padding:28px 0}.intro h1{font-size:48px}}
</style>

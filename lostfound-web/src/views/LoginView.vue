<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const tab = ref(route.query.tab === 'register' ? 'register' : 'login')
const loading = ref(false)

const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', confirm: '', nickname: '', phone: '' })

const doLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    return ElMessage.warning('请输入用户名和密码')
  }
  loading.value = true
  try {
    await store.login(loginForm.value.username, loginForm.value.password)
    ElMessage.success('欢迎回来！')
    router.push((route.query.redirect as string) || '/')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const doRegister = async () => {
  const f = registerForm.value
  if (!f.username || !f.password || !f.nickname) return ElMessage.warning('请填写完整信息')
  if (f.password !== f.confirm) return ElMessage.warning('两次密码不一致')
  loading.value = true
  try {
    await store.register({ username: f.username, password: f.password, nickname: f.nickname, phone: f.phone })
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card lf-card">
      <div class="login-side">
        <h2>寻物志</h2>
        <p>让每一份遗失都有归途</p>
        <ul>
          <li>发布失物/拾物启事</li>
          <li>智能匹配自动推荐</li>
          <li>招领站就近托管</li>
        </ul>
      </div>
      <div class="login-form">
        <el-tabs v-model="tab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form @submit.prevent>
              <el-form-item>
                <el-input v-model="loginForm.username" placeholder="用户名" size="large" />
              </el-form-item>
              <el-form-item>
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="密码"
                  size="large"
                  show-password
                  @keyup.enter="doLogin"
                />
              </el-form-item>
              <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="doLogin">
                登 录
              </el-button>
              <p class="tip">默认管理员：admin / admin123</p>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="注册" name="register">
            <el-form @submit.prevent>
              <el-form-item>
                <el-input v-model="registerForm.nickname" placeholder="昵称" size="large" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="registerForm.username" placeholder="用户名（3-20 位）" size="large" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="registerForm.password" type="password" placeholder="密码（6-32 位）" size="large" show-password />
              </el-form-item>
              <el-form-item>
                <el-input v-model="registerForm.confirm" type="password" placeholder="确认密码" size="large" show-password @keyup.enter="doRegister" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="registerForm.phone" placeholder="手机号（选填）" size="large" />
              </el-form-item>
              <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="doRegister">
                注 册
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100vh - 182px);
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 20% 20%, rgba(37, 99, 235, 0.1), transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(249, 115, 22, 0.08), transparent 40%),
    var(--lf-bg);
  padding: 40px 20px;
}
.login-card {
  display: flex;
  width: min(820px, 100%);
  overflow: hidden;
  border-radius: 20px;
}
.login-side {
  flex: 1;
  background: linear-gradient(150deg, #1e40af, #2563eb 70%, #3b82f6);
  color: #fff;
  padding: 46px 38px;
  display: none;
}
@media (min-width: 760px) {
  .login-side {
    display: block;
  }
}
.login-side h2 {
  font-size: 28px;
  letter-spacing: 3px;
}
.login-side p {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.75);
}
.login-side ul {
  margin-top: 36px;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.login-side li {
  position: relative;
  padding-left: 22px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.88);
}
.login-side li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: #fdba74;
  font-weight: 700;
}
.login-form {
  flex: 1.2;
  padding: 40px 36px;
}
.tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--lf-text-3);
  text-align: center;
}
</style>

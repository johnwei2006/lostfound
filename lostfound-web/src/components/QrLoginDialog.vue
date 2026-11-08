<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Warning, RefreshRight } from '@element-plus/icons-vue'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { closeQrLogin, useQrLogin } from '@/composables/useQrLogin'

const { visible, redirect } = useQrLogin()
const router = useRouter()
const store = useUserStore()

const qrCode = ref('')
const ticket = ref('')
const loading = ref(false)
const error = ref('')
const expired = ref(false)
const seconds = ref(0)

let pollTimer: number | undefined
let countdownTimer: number | undefined

const clearTimers = () => {
  if (pollTimer !== undefined) window.clearInterval(pollTimer)
  if (countdownTimer !== undefined) window.clearInterval(countdownTimer)
  pollTimer = undefined
  countdownTimer = undefined
}

const reset = () => {
  clearTimers()
  qrCode.value = ''
  ticket.value = ''
  error.value = ''
  expired.value = false
  seconds.value = 0
}

const poll = async () => {
  const current = ticket.value
  if (!current || !visible.value) return
  try {
    const data = await authApi.qrStatus(current)
    if (!visible.value || ticket.value !== current) return
    if (data.status === 'confirmed' && data.token && data.user) {
      // 与小程序同一个 user.id 对应的 JWT
      store.setSession({ token: data.token, user: data.user })
      ElMessage.success(`欢迎回来，${data.user.nickname || '朋友'}`)
      // 登录页发起的扫码没有 redirect，成功后回首页；其余场景停留在原页由页面自行刷新
      const target = redirect.value || (router.currentRoute.value.name === 'login' ? '/' : '')
      reset()
      closeQrLogin()
      if (target) router.push(target)
    } else if (data.status === 'expired') {
      expired.value = true
      clearTimers()
    }
  } catch (e) {
    // 轮询失败不打扰用户，下一轮自动重试
    console.error(e)
  }
}

const createTicket = async () => {
  clearTimers()
  loading.value = true
  error.value = ''
  expired.value = false
  qrCode.value = ''
  try {
    const data = await authApi.qrTicket()
    ticket.value = data.ticket
    qrCode.value = data.qrCode
    seconds.value = Math.max(1, data.expiresIn)
    pollTimer = window.setInterval(poll, 2000)
    countdownTimer = window.setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0) {
        expired.value = true
        clearTimers()
      }
    }, 1000)
  } catch (e: any) {
    error.value = e?.message || '生成二维码失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// immediate：发布页等组件可能在弹层挂载前就已打开，需立即补建二维码
watch(visible, (open) => {
  if (open) createTicket()
  else reset()
}, { immediate: true })
onUnmounted(clearTimers)

const goAccountLogin = () => {
  const target = redirect.value
  reset()
  closeQrLogin()
  router.push({ path: '/login', query: target ? { redirect: target } : {} })
}

const statusText = computed(() => {
  if (error.value) return error.value
  if (expired.value) return '二维码已过期，请刷新后重新扫码'
  if (loading.value) return '正在生成二维码…'
  if (qrCode.value) return '等待扫码：请用微信扫描，并在小程序内点「确认登录」'
  return '准备二维码…'
})

const countdownText = computed(() => {
  if (seconds.value <= 0) return '二维码已过期'
  const m = Math.floor(seconds.value / 60)
  const s = String(seconds.value % 60).padStart(2, '0')
  return `二维码将在 ${m}:${s} 后过期`
})
</script>

<template>
  <el-dialog
    v-model="visible"
    class="qr-dialog"
    width="420px"
    align-center
    :close-on-click-modal="false"
    :show-close="true"
  >
    <template #header>
      <div class="qr-head">
        <h3>微信扫码登录</h3>
        <p>扫码后与小程序使用同一个账号，发布与关注记录自动同步</p>
      </div>
    </template>

    <div class="qr-body">
      <div class="qr-box">
        <img v-if="qrCode" :src="qrCode" alt="微信扫码登录小程序码" />
        <div v-else class="qr-box__placeholder">
          <el-icon v-if="loading" :size="28" class="is-loading"><Loading /></el-icon>
          <el-icon v-else :size="28"><Warning /></el-icon>
          <span>{{ loading ? '正在生成二维码…' : (error || '二维码暂不可用') }}</span>
        </div>
        <div v-if="expired && qrCode" class="qr-box__mask">
          <span>二维码已过期</span>
          <el-button size="small" type="primary" round @click="createTicket">刷新二维码</el-button>
        </div>
      </div>

      <ol class="qr-steps">
        <li :class="{ 'is-active': !!qrCode && !expired }"><span>1</span>打开微信扫一扫</li>
        <li><span>2</span>在小程序点「确认登录」</li>
        <li><span>3</span>网页自动完成登录</li>
      </ol>

      <p class="qr-status" role="status" aria-live="polite">{{ statusText }}</p>

      <div class="qr-actions">
        <span class="qr-countdown">{{ countdownText }}</span>
        <el-button link type="primary" :loading="loading" @click="createTicket">
          <el-icon :size="14" style="margin-right: 4px"><RefreshRight /></el-icon>刷新二维码
        </el-button>
      </div>

      <button type="button" class="qr-alt" @click="goAccountLogin">使用账号密码登录</button>
    </div>
  </el-dialog>
</template>

<style scoped>
:deep(.qr-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 18px;
}
:deep(.qr-dialog .el-dialog__header) {
  padding: 22px 26px 8px;
}
.qr-head h3 {
  font-size: 19px;
  font-weight: 600;
  color: var(--lf-text);
}
.qr-head p {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--lf-text-2);
}
.qr-body {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 6px 12px 10px;
}
.qr-box {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 220px;
  height: 220px;
  border: 1px solid var(--lf-border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 26px rgba(37, 99, 235, 0.08);
}
.qr-box img {
  width: 200px;
  height: 200px;
  object-fit: contain;
}
.qr-box__placeholder {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  text-align: center;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--lf-text-3);
}
.qr-box__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  color: var(--lf-text-2);
}
.qr-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 18px 0 8px;
  padding: 0;
  list-style: none;
  flex-wrap: wrap;
}
.qr-steps li {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--lf-text-3);
}
.qr-steps li span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #eef2f8;
  color: var(--lf-text-3);
  font-size: 11px;
}
.qr-steps li.is-active span {
  background: var(--lf-primary);
  color: #fff;
}
.qr-steps li.is-active {
  color: var(--lf-primary);
  font-weight: 600;
}
.qr-status {
  min-height: 20px;
  font-size: 12.5px;
  line-height: 1.6;
  text-align: center;
  color: var(--lf-text-2);
}
.qr-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  max-width: 320px;
  margin-top: 6px;
}
.qr-countdown {
  font-size: 12px;
  color: var(--lf-text-3);
}
.qr-alt {
  margin-top: 14px;
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: var(--lf-primary);
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
  transition: color 180ms ease;
}
.qr-alt:hover {
  color: #1d4ed8;
}
.qr-alt:focus-visible {
  outline: 2px solid var(--lf-primary);
  outline-offset: 2px;
}
@media (max-width: 520px) {
  .qr-box {
    width: 190px;
    height: 190px;
  }
  .qr-box img {
    width: 174px;
    height: 174px;
  }
  .qr-steps {
    flex-direction: column;
    align-items: flex-start;
  }
}
@media (prefers-reduced-motion: reduce) {
  .qr-alt {
    transition: none;
  }
}
</style>

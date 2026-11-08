import { ref } from 'vue'

/**
 * 全局唯一的扫码登录弹层状态（模块级单例）。
 * App.vue 只挂载一个弹层，各页面通过 openQrLogin 唤起，避免多处轮询与重复请求。
 */
const visible = ref(false)
const redirect = ref('')

export function openQrLogin(target?: string) {
  redirect.value = target || ''
  visible.value = true
}

export function closeQrLogin() {
  visible.value = false
}

export function useQrLogin() {
  return { visible, redirect, openQrLogin, closeQrLogin }
}

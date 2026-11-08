<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User as UserIcon } from '@element-plus/icons-vue'
import { itemApi, focusApi, claimApi, authApi } from '@/api'
import type { ClaimVO, ItemVO } from '@/types'
import { useUserStore } from '@/stores/user'
import { openQrLogin } from '@/composables/useQrLogin'
import ItemCard from '@/components/ItemCard.vue'

const router = useRouter()
const store = useUserStore()
const tab = ref('published')

const published = ref<ItemVO[]>([])
const focused = ref<ItemVO[]>([])
const claims = ref<ClaimVO[]>([])
const loading = ref(false)

const profileForm = ref({ nickname: '', phone: '', email: '', avatar: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })

const loadTab = async (name: string) => {
  // 游客不发任何需登录的请求：个人资料与三个列表都保持为空
  if (!store.isLoggedIn) {
    published.value = []
    focused.value = []
    claims.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    if (name === 'published') {
      const [f, l] = await Promise.all([itemApi.mine('found', 1, 50), itemApi.mine('lost', 1, 50)])
      published.value = [...l.records, ...f.records]
    } else if (name === 'focused') {
      const [f, l] = await Promise.all([
        focusApi.mine('FOUND', 1, 50),
        focusApi.mine('LOST', 1, 50),
      ])
      focused.value = [...l.records, ...f.records]
    } else if (name === 'claims') {
      claims.value = await claimApi.mine()
    } else if (name === 'settings') {
      profileForm.value = {
        nickname: store.user?.nickname || '',
        phone: store.user?.phone || '',
        email: store.user?.email || '',
        avatar: store.user?.avatar || '',
      }
    }
  } finally {
    loading.value = false
  }
}
watch(tab, (name) => loadTab(name), { immediate: true })
// 扫码登录成功后立即补齐数据，无需刷新页面
watch(() => store.isLoggedIn, (logged) => {
  if (logged) loadTab(tab.value)
})

const removeItem = async (it: ItemVO) => {
  await ElMessageBox.confirm('确定删除该信息？', '删除确认', { type: 'warning' })
  await itemApi.remove(it.type.toLowerCase() as 'found' | 'lost', it.id)
  ElMessage.success('已删除')
  loadTab('published')
}
const offline = async (it: ItemVO) => {
  await itemApi.changeStatus(it.type.toLowerCase() as 'found' | 'lost', it.id, 'OFFLINE')
  ElMessage.success('已下架')
  loadTab('published')
}
const online = async (it: ItemVO) => {
  await itemApi.changeStatus(it.type.toLowerCase() as 'found' | 'lost', it.id, 'WAITING')
  ElMessage.success('已重新上架')
  loadTab('published')
}

const saveProfile = async () => {
  await authApi.updateProfile(profileForm.value)
  await store.refresh()
  ElMessage.success('资料已更新')
}
const changePwd = async () => {
  if (!pwdForm.value.newPassword) return ElMessage.warning('请输入新密码')
  if (pwdForm.value.newPassword !== pwdForm.value.confirm) return ElMessage.warning('两次密码不一致')
  await authApi.changePassword({ oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword })
  ElMessage.success('密码已修改')
  pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
}

const claimStatusText = computed(() => ({
  PENDING: '待处理',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
}))
</script>

<template>
  <div class="lf-container profile-page">
    <div class="profile-head lf-card">
      <el-avatar :size="64" :class="{ 'profile-avatar--empty': !store.isLoggedIn }">
        {{ store.isLoggedIn ? (store.user?.nickname?.charAt(0) || 'U') : '' }}
      </el-avatar>
      <div>
        <h2 v-if="store.user?.nickname">{{ store.user.nickname }}</h2>
        <p v-if="store.isLoggedIn">{{ store.user?.username }} · {{ store.isAdmin ? '管理员' : '普通用户' }}</p>
        <p v-else class="profile-hint">登录后查看你的发布、关注与认领记录</p>
      </div>
      <el-button v-if="!store.isLoggedIn" type="primary" round @click="openQrLogin()">微信扫码登录</el-button>
      <el-button v-else-if="store.isAdmin" type="warning" round plain @click="router.push('/admin')">
        进入管理后台
      </el-button>
    </div>

    <div v-if="!store.isLoggedIn" class="guest-panel lf-card">
      <div class="guest-panel__icon"><el-icon :size="28"><UserIcon /></el-icon></div>
      <h3>登录后查看个人信息</h3>
      <p>扫码后与小程序使用同一个账号，发布记录、关注与认领会自动同步。</p>
      <div class="guest-panel__tabs" aria-label="登录后可用的栏目">
        <span>我的发布</span>
        <span>我的关注</span>
        <span>我的申请</span>
      </div>
      <el-button type="primary" round @click="openQrLogin()">微信扫码登录</el-button>
      <button type="button" class="guest-panel__alt" @click="router.push('/login')">使用账号密码登录</button>
    </div>

    <el-tabs v-else v-model="tab" class="profile-tabs">
      <el-tab-pane label="我的发布" name="published">
        <div v-if="loading">加载中…</div>
        <div v-else-if="published.length" class="grid">
          <div v-for="it in published" :key="it.id" class="mine-item lf-card">
            <ItemCard :item="it" />
            <div class="mine-ops">
              <el-button v-if="it.status === 'WAITING'" size="small" text type="warning" @click="offline(it)">下架</el-button>
              <el-button v-else-if="it.status === 'OFFLINE'" size="small" text type="success" @click="online(it)">上架</el-button>
              <el-button size="small" text type="danger" @click="removeItem(it)">删除</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有发布过信息" :image="'/empty.svg'">
          <el-button type="primary" round @click="router.push('/publish')">去发布</el-button>
        </el-empty>
      </el-tab-pane>

      <el-tab-pane label="我的关注" name="focused">
        <div v-if="loading">加载中…</div>
        <div v-else-if="focused.length" class="grid">
          <ItemCard v-for="it in focused" :key="it.id" :item="it" />
        </div>
        <el-empty v-else description="暂无关注的物品" :image="'/empty.svg'" />
      </el-tab-pane>

      <el-tab-pane label="我的申请" name="claims">
        <div v-if="claims.length" class="claim-list">
          <div v-for="c in claims" :key="c.id" class="claim-item lf-card">
            <div class="claim-head">
              <b>{{ c.itemTitle || (c.itemType === 'FOUND' ? '拾物 #' + c.itemId : '失物 #' + c.itemId) }}</b>
              <span class="lf-tag" :class="{ 'lf-tag--waiting': c.status === 'PENDING', 'lf-tag--finished': c.status === 'APPROVED', 'lf-tag--offline': c.status === 'REJECTED' }">
                {{ claimStatusText[c.status as keyof typeof claimStatusText] }}
              </span>
            </div>
            <p>{{ c.description }}</p>
          </div>
        </div>
        <el-empty v-else description="暂无认领申请记录" :image="'/empty.svg'" />
      </el-tab-pane>

      <el-tab-pane label="账号设置" name="settings">
        <div class="settings-form lf-card">
          <h4>基本资料</h4>
          <el-form label-width="70px" style="max-width: 420px">
            <el-form-item label="昵称"><el-input v-model="profileForm.nickname" /></el-form-item>
            <el-form-item label="手机号"><el-input v-model="profileForm.phone" /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="profileForm.email" /></el-form-item>
            <el-form-item label="头像URL"><el-input v-model="profileForm.avatar" placeholder="粘贴图片链接" /></el-form-item>
            <el-button type="primary" round @click="saveProfile">保存资料</el-button>
          </el-form>
          <h4 style="margin-top: 34px">修改密码</h4>
          <el-form label-width="70px" style="max-width: 420px">
            <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
            <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
            <el-form-item label="确认密码"><el-input v-model="pwdForm.confirm" type="password" show-password /></el-form-item>
            <el-button type="primary" round @click="changePwd">修改密码</el-button>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 30px 20px 50px;
}
.profile-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 24px;
}
.profile-head h2 {
  font-size: 20px;
}
.profile-head p {
  font-size: 13px;
  color: var(--lf-text-3);
  margin-top: 3px;
}
.profile-head .profile-hint {
  color: var(--lf-text-2);
}
.profile-head .el-button {
  margin-left: auto;
}
.profile-avatar--empty {
  background: #eef2f8;
  color: #94a3b8;
}
.profile-tabs {
  margin-top: 18px;
}
.guest-panel {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
  padding: 46px 24px 44px;
  text-align: center;
  background: linear-gradient(180deg, #fff 0%, #f8fbff 100%);
}
.guest-panel__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
}
.guest-panel h3 {
  font-size: 17px;
  font-weight: 600;
  color: var(--lf-text);
}
.guest-panel p {
  max-width: 420px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--lf-text-2);
}
.guest-panel__tabs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  margin: 6px 0 10px;
}
.guest-panel__tabs span {
  padding: 5px 14px;
  border: 1px solid var(--lf-border);
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  color: var(--lf-text-3);
}
.guest-panel__alt {
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: var(--lf-primary);
  font-size: 13px;
  cursor: pointer;
  transition: color 180ms ease;
}
.guest-panel__alt:hover {
  color: #1d4ed8;
}
.guest-panel__alt:focus-visible {
  outline: 2px solid var(--lf-primary);
  outline-offset: 2px;
  border-radius: 4px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
}
.mine-ops {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  padding: 6px 10px;
  background: #fff;
  border: 1px solid var(--lf-border);
  border-top: none;
  border-radius: 0 0 var(--lf-radius) var(--lf-radius);
}
.claim-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.claim-item {
  padding: 16px 20px;
}
.claim-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.claim-item p {
  font-size: 13.5px;
  color: var(--lf-text-2);
}
.settings-form {
  padding: 26px;
  max-width: 560px;
}
.settings-form h4 {
  font-size: 15px;
  margin-bottom: 16px;
}
@media (max-width: 520px) {
  .profile-head {
    flex-wrap: wrap;
  }
  .guest-panel {
    padding: 34px 16px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .guest-panel__alt {
    transition: none;
  }
}
</style>

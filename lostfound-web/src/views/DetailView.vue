<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, StarFilled, ChatDotRound, Location, Delete } from '@element-plus/icons-vue'
import { claimApi, commentApi, focusApi, itemApi } from '@/api'
import type { ClaimVO, CommentVO, ItemVO } from '@/types'
import { STATUS_MAP } from '@/types'
import { useUserStore } from '@/stores/user'
import { openQrLogin } from '@/composables/useQrLogin'
import MapPicker from '@/components/MapPicker.vue'
import dayjs from 'dayjs'

const props = defineProps<{ type: 'found' | 'lost'; id: number }>()
const route = useRoute()
const router = useRouter()
const store = useUserStore()

const item = ref<ItemVO | null>(null)
const comments = ref<CommentVO[]>([])
const claims = ref<ClaimVO[]>([])
const showClaims = ref(false)
const loading = ref(true)

const isOwner = computed(() => item.value && store.user && item.value.userId === store.user.id)
const status = computed(() => (item.value ? STATUS_MAP[item.value.status] : null))

const load = async () => {
  loading.value = true
  try {
    item.value = await itemApi.detail(props.type, props.id)
    comments.value = await commentApi.list(props.type.toUpperCase() as 'FOUND' | 'LOST', props.id)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

/* 关注 */
const toggleFocus = async () => {
  if (!store.isLoggedIn) return openQrLogin()
  if (!item.value) return
  if (item.value.focused) {
    await focusApi.unfocus(props.type.toUpperCase() as 'FOUND' | 'LOST', props.id)
    item.value.focused = false
    item.value.focusCount--
  } else {
    await focusApi.focus(props.type.toUpperCase() as 'FOUND' | 'LOST', props.id)
    item.value.focused = true
    item.value.focusCount++
  }
}

/* 认领 */
const showClaimDialog = ref(false)
const claimForm = ref({ description: '', contact: '' })
const submitClaim = async () => {
  if (!store.isLoggedIn) return openQrLogin()
  if (!claimForm.value.description.trim()) return ElMessage.warning('请描述物品特征以证明所有权')
  try {
    await claimApi.submit(props.type, props.id, claimForm.value)
    ElMessage.success('申请已提交，等待发布者审核')
    showClaimDialog.value = false
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

/* 查看申请（发布者） */
const viewClaims = async () => {
  claims.value = await claimApi.listByItem(props.type, props.id)
  showClaims.value = true
}
const reviewClaim = async (claim: ClaimVO, approve: boolean) => {
  await ElMessageBox.confirm(
    approve ? '确认通过该认领申请？通过后物品将标记为已完成。' : '确认拒绝该认领申请？',
    '审核确认',
    { type: 'warning' },
  )
  try {
    await claimApi.review(props.type, claim.id, approve)
    ElMessage.success('已处理')
    claims.value = await claimApi.listByItem(props.type, props.id)
    load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

/* 评论 */
const commentText = ref('')
const replyTo = ref<CommentVO | null>(null)
const sendComment = async () => {
  if (!commentText.value.trim()) return
  if (!store.isLoggedIn) return openQrLogin()
  await commentApi.add(props.type.toUpperCase() as 'FOUND' | 'LOST', props.id, {
    content: commentText.value,
    parentId: replyTo.value?.id,
  })
  commentText.value = ''
  replyTo.value = null
  comments.value = await commentApi.list(props.type.toUpperCase() as 'FOUND' | 'LOST', props.id)
  if (item.value) item.value.commentCount++
}
const removeComment = async (c: CommentVO) => {
  await ElMessageBox.confirm('确定删除该评论？', '提示', { type: 'warning' })
  await commentApi.remove(props.type.toUpperCase() as 'FOUND' | 'LOST', c.id)
  comments.value = await commentApi.list(props.type.toUpperCase() as 'FOUND' | 'LOST', props.id)
  if (item.value) item.value.commentCount--
}

/* 状态操作（发布者） */
const changeStatus = async (s: string) => {
  await itemApi.changeStatus(props.type, props.id, s)
  ElMessage.success('状态已更新')
  load()
}
const removeItem = async () => {
  await ElMessageBox.confirm('确定删除该信息？删除后不可恢复。', '删除确认', { type: 'warning' })
  await itemApi.remove(props.type, props.id)
  ElMessage.success('已删除')
  router.push(`/${props.type}`)
}

const fmt = (t?: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '—')
</script>

<template>
  <div class="lf-container detail-page">
    <el-skeleton v-if="loading" animated style="max-width: 860px; margin: 0 auto">
      <template #template>
        <el-skeleton-item variant="rect" style="height: 380px; border-radius: 16px" />
      </template>
    </el-skeleton>

    <template v-else-if="item">
      <el-breadcrumb style="margin-bottom: 18px">
        <el-breadcrumb-item to="/">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="`/${type}`">{{ type === 'found' ? '拾物招领' : '寻物启事' }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ item.title }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="detail-layout">
        <!-- 左：图片 -->
        <div class="gallery" v-if="item.images?.length">
          <el-carousel v-if="item.images.length > 1" height="380px" indicator-position="outside">
            <el-carousel-item v-for="img in item.images" :key="img">
              <img class="gallery-img" :src="img" :alt="item.title" />
            </el-carousel-item>
          </el-carousel>
          <img v-else class="gallery-img gallery-single" :src="item.images[0]" :alt="item.title" />
        </div>

        <!-- 右：信息 -->
        <div class="info-panel lf-card">
          <div class="info-head">
            <h1>{{ item.title }}</h1>
            <span class="lf-tag" :class="status?.cls">{{ status?.text }}</span>
          </div>
          <div class="info-tags">
            <el-tag effect="plain" size="small">{{ item.category }}</el-tag>
            <el-tag v-if="item.siteName" effect="plain" size="small" type="warning">
              {{ item.siteName }}
            </el-tag>
          </div>
          <p class="info-desc">{{ item.description || '暂无描述' }}</p>
          <ul class="info-list">
            <li><span class="k">{{ type === 'found' ? '拾获时间' : '丢失时间' }}</span><b>{{ fmt(item.itemTime) }}</b></li>
            <li><span class="k">{{ type === 'found' ? '拾获地点' : '丢失地点' }}</span>
              <b><el-icon style="vertical-align:-2px"><Location /></el-icon>{{ [item.province, item.city, item.district, item.address].filter(Boolean).join(' ') || '—' }}</b>
            </li>
            <li><span class="k">发布人</span><b>{{ item.nickname }}</b></li>
            <li v-if="isOwner || item.status !== 'WAITING'"><span class="k">联系方式</span><b>{{ item.contact || '—' }}</b></li>
            <li v-else><span class="k">联系方式</span><b style="color:var(--lf-accent)">提交认领申请后可见</b></li>
            <li><span class="k">发布时间</span><b>{{ fmt(item.createTime) }}</b></li>
            <li><span class="k">浏览量</span><b>{{ item.viewCount }}</b></li>
          </ul>

          <div class="info-actions">
            <el-button
              v-if="!isOwner && item.status === 'WAITING'"
              type="primary"
              size="large"
              round
              @click="showClaimDialog = true"
            >
              {{ type === 'found' ? '这是我的，申请认领' : '我捡到了，提供线索' }}
            </el-button>
            <el-button size="large" round @click="toggleFocus">
              <el-icon style="margin-right: 5px">
                <StarFilled v-if="item.focused" color="#f59e0b" />
                <Star v-else />
              </el-icon>
              {{ item.focused ? '已关注' : '关注' }}
              <span style="margin-left: 4px">{{ item.focusCount }}</span>
            </el-button>
            <el-button v-if="isOwner" size="large" round @click="viewClaims">
              <el-icon style="margin-right: 4px"><ChatDotRound /></el-icon>
              认领申请
            </el-button>
          </div>

          <div v-if="isOwner" class="owner-bar">
            <el-button v-if="item.status === 'WAITING'" text type="warning" size="small" @click="changeStatus('OFFLINE')">下架</el-button>
            <el-button v-else-if="item.status === 'OFFLINE'" text type="success" size="small" @click="changeStatus('WAITING')">重新上架</el-button>
            <el-button text type="danger" size="small" :icon="Delete" @click="removeItem">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 地图 -->
      <section v-if="item.longitude && item.latitude" class="lf-card map-section">
        <h3 class="section-title">位置信息</h3>
        <MapPicker :longitude="item.longitude" :latitude="item.latitude" :address="item.address" />
      </section>

      <!-- 评论区 -->
      <section class="lf-card comment-section">
        <h3 class="section-title">评论（{{ item.commentCount }}）</h3>
        <div class="comment-input">
          <el-avatar :size="34">{{ store.user?.nickname?.charAt(0) || 'G' }}</el-avatar>
          <div class="comment-input-main">
            <p v-if="replyTo" class="replying">
              回复 @{{ replyTo.nickname }}
              <el-button text size="small" @click="replyTo = null">取消</el-button>
            </p>
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="2"
              :placeholder="store.isLoggedIn ? '友善发言，帮助物品更快回家…' : '登录后参与评论'"
            />
            <el-button type="primary" round style="margin-top: 8px" @click="sendComment">发表评论</el-button>
          </div>
        </div>
        <div v-if="comments.length" class="comment-list">
          <template v-for="c in comments" :key="c.id">
            <div class="comment">
              <el-avatar :size="34">{{ c.nickname?.charAt(0) || 'U' }}</el-avatar>
              <div class="comment-body">
                <div class="comment-head">
                  <b>{{ c.nickname }}</b>
                  <span>{{ fmt(c.createTime) }}</span>
                </div>
                <p>{{ c.content }}</p>
                <div class="comment-ops">
                  <el-button text size="small" type="primary" @click="replyTo = c">回复</el-button>
                  <el-button v-if="store.user && (store.user.id === c.userId || store.isAdmin)" text size="small" type="danger" @click="removeComment(c)">删除</el-button>
                </div>
                <div v-if="c.children?.length" class="comment-children">
                  <div v-for="cc in c.children" :key="cc.id" class="comment">
                    <el-avatar :size="28">{{ cc.nickname?.charAt(0) || 'U' }}</el-avatar>
                    <div class="comment-body">
                      <div class="comment-head">
                        <b>{{ cc.nickname }}</b>
                        <span>{{ fmt(cc.createTime) }}</span>
                      </div>
                      <p>{{ cc.content }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
        <el-empty v-else description="还没有评论，来抢沙发" :image-size="80" :image="'/empty.svg'" />
      </section>
    </template>

    <!-- 认领申请弹窗 -->
    <el-dialog v-model="showClaimDialog" :title="type === 'found' ? '申请认领' : '提供归还线索'" width="480px">
      <el-alert type="info" :closable="false" style="margin-bottom: 14px">
        请尽可能描述{{ type === 'found' ? '物品的独有特征（如图案、划痕、内容物）' : '你拾获该物品的时间地点' }}，
        便于发布者核实身份。
      </el-alert>
      <el-input v-model="claimForm.description" type="textarea" :rows="4" placeholder="描述物品特征…" />
      <el-input v-model="claimForm.contact" placeholder="联系方式（手机/微信）" style="margin-top: 12px" />
      <template #footer>
        <el-button @click="showClaimDialog = false">取消</el-button>
        <el-button type="primary" @click="submitClaim">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 认领申请列表 -->
    <el-dialog v-model="showClaims" title="收到的认领申请" width="600px">
      <div v-if="claims.length" class="claim-list">
        <div v-for="c in claims" :key="c.id" class="claim-item">
          <div class="claim-head">
            <b>{{ c.nickname }}</b>
            <span class="lf-tag" :class="{ 'lf-tag--waiting': c.status === 'PENDING', 'lf-tag--finished': c.status === 'APPROVED', 'lf-tag--offline': c.status === 'REJECTED' }">
              {{ c.status === 'PENDING' ? '待处理' : c.status === 'APPROVED' ? '已通过' : '已拒绝' }}
            </span>
          </div>
          <p class="claim-desc">{{ c.description }}</p>
          <p class="claim-contact" v-if="c.contact">联系方式：{{ c.contact }}</p>
          <div v-if="c.status === 'PENDING'" class="claim-ops">
            <el-button type="primary" size="small" round @click="reviewClaim(c, true)">通过并完成流转</el-button>
            <el-button size="small" round @click="reviewClaim(c, false)">拒绝</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无认领申请" :image="'/empty.svg'" />
    </el-dialog>
  </div>
</template>

<style src="@/styles/detail.css" scoped></style>

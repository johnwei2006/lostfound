<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { siteApi } from '@/api'
import type { FoundSite, SiteEndorsementPayload } from '@/types'
import { useSiteStore } from '@/stores/site'
import { errMsg, fmtTime } from '@/utils/format'
import SiteEndorsementCard from '@/components/SiteEndorsementCard.vue'

const siteStore = useSiteStore()
const siteId = computed(() => siteStore.currentSiteId)
const site = ref<FoundSite | null>(null)
const ownerName = ref('')
const form = ref<SiteEndorsementPayload>({ endorsementOrg: '', endorsementText: '' })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const previewUpdatedAt = computed(() =>
  form.value.endorsementOrg.trim() || form.value.endorsementText.trim() ? new Date().toISOString() : '')

const load = async () => {
  if (!siteId.value) return
  loading.value = true
  error.value = ''
  try {
    const [detail, managers] = await Promise.all([
      siteApi.detail(siteId.value),
      siteApi.managers(siteId.value),
    ])
    site.value = detail
    const owner = managers.find((m) => m.isOwner)
    ownerName.value = owner?.nickname
      || (detail.ownerUserId ? `用户 #${detail.ownerUserId}` : '尚未指定')
    form.value = {
      endorsementOrg: detail.endorsementOrg || '',
      endorsementText: detail.endorsementText || '',
    }
  } catch (e) {
    error.value = errMsg(e, '站点资料加载失败')
  } finally {
    loading.value = false
  }
}
watch(siteId, load, { immediate: true })

const save = async () => {
  if (!siteId.value || saving.value) return
  saving.value = true
  try {
    await siteApi.updateEndorsement(siteId.value, {
      endorsementOrg: form.value.endorsementOrg.trim(),
      endorsementText: form.value.endorsementText.trim(),
    })
    ElMessage.success('背书信息已更新，站点列表与详情页会立即展示')
    await load()
    await siteStore.load(true)
  } catch (e) {
    ElMessage.error(errMsg(e, '保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="settings-page">
    <header class="page-head">
      <div>
        <h1>站点资料与背书</h1>
        <p>{{ siteStore.currentSite?.name || '选择站点后查看' }} · 维护本站的背书单位与推荐语</p>
      </div>
      <el-button round :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </header>

    <div v-if="error" class="lf-card state-panel" role="alert">
      <el-empty :description="error" :image="'/empty.svg'"><el-button round @click="load">重试</el-button></el-empty>
    </div>
    <div v-else-if="loading" class="lf-card state-panel" v-loading="true">正在加载站点资料…</div>

    <template v-else-if="site">
      <section class="lf-card site-panel">
        <h2>站点基础资料</h2>
        <p class="section-hint">基础资料由平台管理员维护；如需变更，请在「管理后台 → 招领站管理」或联系平台处理。</p>
        <dl class="site-grid">
          <div><dt>站点名称</dt><dd>{{ site.name }}</dd></div>
          <div><dt>站点地址</dt><dd>{{ site.address }}</dd></div>
          <div><dt>主管理员</dt><dd>{{ ownerName || '尚未指定' }}</dd></div>
          <div><dt>联系电话</dt><dd>{{ site.phone || '未填写' }}</dd></div>
          <div><dt>开放时间</dt><dd>{{ site.openTime || '全天开放' }}</dd></div>
          <div class="wide"><dt>站点简介</dt><dd>{{ site.description || '未填写' }}</dd></div>
        </dl>
      </section>

      <div class="edit-layout">
        <section class="lf-card edit-panel">
          <div class="edit-head">
            <div><h2>自定义背书文案</h2><p>背书单位与推荐语会展示在站点列表与详情页，清空两项即取消展示。</p></div>
            <span class="lf-tag" :class="(form.endorsementOrg.trim() || form.endorsementText.trim()) ? 'lf-tag--waiting' : 'lf-tag--muted'">
              {{ (form.endorsementOrg.trim() || form.endorsementText.trim()) ? '已设置背书' : '暂未设置' }}
            </span>
          </div>
          <el-form label-position="top">
            <el-form-item label="背书单位">
              <el-input v-model="form.endorsementOrg" maxlength="100" show-word-limit
                placeholder="如：江汉路社区服务中心" />
            </el-form-item>
            <el-form-item label="推荐语 / 说明">
              <el-input v-model="form.endorsementText" type="textarea" :rows="5" maxlength="500" show-word-limit
                placeholder="如：长期协助社区居民临时保管拾获物品，工作日均有志愿者值守" />
            </el-form-item>
          </el-form>
          <p class="policy">文案由站点自行维护，仅作信息说明，不代表平台认证或资质审核。请勿填写无法核实的承诺。</p>
          <div class="edit-ops">
            <el-button round @click="load">放弃修改</el-button>
            <el-button type="primary" round :loading="saving" @click="save">保存背书</el-button>
          </div>
          <p v-if="site.endorsementUpdatedAt" class="updated">上次更新：{{ fmtTime(site.endorsementUpdatedAt) }}（操作人 #{{ site.endorsementUpdatedBy || '—' }}）</p>
        </section>

        <section class="preview-panel">
          <h3>前台展示预览</h3>
          <SiteEndorsementCard :org="form.endorsementOrg" :text="form.endorsementText" :updated-at="previewUpdatedAt" />
          <div class="preview-compact">
            <p>列表卡片徽标</p>
            <SiteEndorsementCard compact :org="form.endorsementOrg" :text="form.endorsementText" />
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.settings-page { max-width: 1100px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-head h1 { font-size: 20px; font-weight: 600; }
.page-head p { margin-top: 6px; font-size: 13px; color: var(--lf-text-3); }
.state-panel { padding: 44px 20px; min-height: 200px; }
.site-panel { padding: 22px 24px; }
.site-panel h2, .edit-head h2 { font-size: 16px; font-weight: 600; }
.section-hint { margin: 6px 0 18px; font-size: 12.5px; line-height: 1.7; color: var(--lf-text-3); }
.site-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 24px; margin: 0; }
.site-grid dt { font-size: 12px; color: var(--lf-text-3); }
.site-grid dd { margin: 3px 0 0; font-size: 13.5px; line-height: 1.7; word-break: break-word; }
.site-grid .wide { grid-column: 1 / -1; }
.edit-layout { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(280px, 1fr); gap: 18px; margin-top: 18px; align-items: start; }
.edit-panel { padding: 22px 24px; }
.edit-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.edit-head p { margin-top: 6px; font-size: 12.5px; line-height: 1.7; color: var(--lf-text-3); }
.policy { padding: 12px 14px; border-radius: 10px; background: var(--lf-bg); font-size: 12px; line-height: 1.8; color: var(--lf-text-3); }
.edit-ops { display: flex; justify-content: flex-end; gap: 10px; margin-top: 18px; }
.updated { margin-top: 12px; font-size: 12px; color: var(--lf-text-3); text-align: right; }
.preview-panel { padding: 4px 2px; }
.preview-panel > h3 { margin-bottom: 12px; font-size: 13px; color: var(--lf-text-3); }
.preview-panel :deep(.endorse-card) { margin-top: 0; }
.preview-compact { margin-top: 18px; padding: 16px; border-radius: var(--lf-radius); background: #fff; border: 1px dashed var(--lf-border); }
.preview-compact p { margin-bottom: 10px; font-size: 12px; color: var(--lf-text-3); }
@media (max-width: 960px) { .edit-layout { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .site-grid { grid-template-columns: 1fr; } .edit-panel, .site-panel { padding: 18px 16px; } }
</style>

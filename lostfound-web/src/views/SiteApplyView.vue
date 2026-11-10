<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { siteApplicationApi } from '@/api'
import type { SiteApplicationPayload } from '@/types'
import { errMsg } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const submitted = ref(false)
const duplicate = ref(false)

const steps = [
  { title: '注册并登录', desc: '使用平台账号提交' },
  { title: '填写建站资料', desc: '站点信息与申请理由' },
  { title: '平台审核', desc: '管理员核对资料' },
  { title: '自动开通', desc: '建站并绑定你为管理员' },
]

const form = ref<SiteApplicationPayload>({
  name: '', address: '', phone: '', openTime: '', description: '', reason: '', contact: '',
  longitude: undefined, latitude: undefined,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请填写站点名称', trigger: 'blur' },
    { max: 100, message: '站点名称最长 100 字', trigger: 'blur' },
  ],
  address: [
    { required: true, message: '请填写站点地址', trigger: 'blur' },
    { max: 200, message: '站点地址最长 200 字', trigger: 'blur' },
  ],
  reason: [
    { required: true, message: '请说明申请理由', trigger: 'blur' },
    { max: 500, message: '申请理由最长 500 字', trigger: 'blur' },
  ],
}

/** 驳回后可从「我的申请」带回填参数重新提交 */
onMounted(() => {
  const q = route.query
  const text = (key: string) => (typeof q[key] === 'string' ? q[key] as string : '')
  for (const key of ['name', 'address', 'phone', 'openTime', 'description', 'reason', 'contact'] as const) {
    const v = text(key)
    if (v) form.value[key] = v
  }
  for (const key of ['longitude', 'latitude'] as const) {
    const n = Number(text(key))
    if (Number.isFinite(n) && n !== 0) form.value[key] = n
  }
})

const completion = computed(() => {
  const keys: (keyof SiteApplicationPayload)[] = [
    'name', 'address', 'phone', 'openTime', 'description', 'reason', 'contact',
  ]
  const done = keys.filter((k) => String(form.value[k] || '').trim()).length
  return Math.round((done / keys.length) * 100)
})

const submit = async () => {
  if (!formRef.value || submitting.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  duplicate.value = false
  try {
    const f = form.value
    const payload: SiteApplicationPayload = {
      name: f.name.trim(),
      address: f.address.trim(),
      reason: f.reason.trim(),
      phone: f.phone?.trim() || undefined,
      openTime: f.openTime?.trim() || undefined,
      description: f.description?.trim() || undefined,
      contact: f.contact?.trim() || undefined,
      longitude: f.longitude ?? undefined,
      latitude: f.latitude ?? undefined,
    }
    await siteApplicationApi.submit(payload)
    submitted.value = true
    ElMessage.success('申请已提交，请等待平台审核')
  } catch (e) {
    const msg = errMsg(e, '提交失败')
    duplicate.value = msg.includes('已提交过申请')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="lf-container apply-page">
    <header class="apply-hero">
      <div>
        <p class="eyebrow">招领站共建计划</p>
        <h1 class="lf-page-title">申请创建招领站</h1>
        <p class="lf-page-sub">提交资料后由平台管理员审核，通过后自动创建站点并开通你的站点管理权限。</p>
      </div>
      <el-button round @click="router.push('/site/applications')">查看我的申请</el-button>
    </header>

    <ol class="steps" aria-label="建站流程">
      <li v-for="(s, i) in steps" :key="s.title" class="step" :class="{ 'step--done': i < 1, 'step--current': i === 1 }">
        <span class="step-index">{{ i + 1 }}</span>
        <div><b>{{ s.title }}</b><small>{{ s.desc }}</small></div>
      </li>
    </ol>

    <div v-if="submitted" class="lf-card result-card">
      <div class="result-mark">✓</div>
      <h2>申请已提交</h2>
      <p>平台管理员会核对你填写的站点资料与申请理由，审核结果会通过站内消息通知你。</p>
      <p class="result-note">审核通过前不会创建招领站；同一时间你只会有一条待审申请。</p>
      <div class="result-ops">
        <el-button type="primary" round @click="router.push('/site/applications')">查看申请进度</el-button>
        <el-button round @click="router.push('/sites')">浏览招领站网络</el-button>
      </div>
    </div>

    <template v-else>
      <div class="progress-bar lf-card">
        <span>资料完整度</span>
        <el-progress :percentage="completion" :stroke-width="8" :show-text="false" />
        <b>{{ completion }}%</b>
        <small>带 * 为必填项，其余信息有助于管理员核对</small>
      </div>

      <el-alert v-if="duplicate" type="warning" :closable="false" show-icon class="duplicate-alert"
        title="你还有一条待审核的建站申请">
        <template #default>
          <p>请等待当前申请处理完成，或在我的建站申请中撤回后重新提交。</p>
          <el-button size="small" text type="primary" @click="router.push('/site/applications')">前往查看</el-button>
        </template>
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="lf-card apply-card">
        <h2><span class="section-index">01</span>站点资料</h2>
        <p class="section-hint">请填写拟建招领站的实际名称与地址，审核通过后将直接用于站点展示。</p>
        <div class="form-grid">
          <el-form-item label="站点名称 *" prop="name">
            <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="如：江汉路社区便民招领站" />
          </el-form-item>
          <el-form-item label="站点地址 *" prop="address">
            <el-input v-model="form.address" maxlength="200" show-word-limit placeholder="街道、门牌号与楼层" />
          </el-form-item>
          <el-form-item label="经度"><el-input-number v-model="form.longitude" :precision="6" :controls="false" :min="-180" :max="180" style="width: 100%" placeholder="选填" /></el-form-item>
          <el-form-item label="纬度"><el-input-number v-model="form.latitude" :precision="6" :controls="false" :min="-90" :max="90" style="width: 100%" placeholder="选填" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.phone" maxlength="20" placeholder="站点对外电话" /></el-form-item>
          <el-form-item label="开放时间"><el-input v-model="form.openTime" maxlength="100" placeholder="如：周一至周日 9:00-20:00" /></el-form-item>
        </div>
        <el-form-item label="站点简介">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="可说明服务范围、保管条件、可接收的物品类型等" />
        </el-form-item>

        <h2><span class="section-index">02</span>申请说明</h2>
        <p class="section-hint">说明你与拟建站点的关系，以及为什么需要纳入平台招领站网络。</p>
        <el-form-item label="申请理由 *" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="如：社区服务中心设有失物保管柜，希望统一登记并协助归还" />
        </el-form-item>
        <el-form-item label="补充联系方式">
          <el-input v-model="form.contact" maxlength="100" placeholder="邮箱、微信或备用电话，便于审核时联系" />
        </el-form-item>

        <div class="form-footer">
          <p>提交即表示同意平台核对以上资料；审核前不会创建站点，也不会修改你的账号角色。</p>
          <div>
            <el-button round @click="router.push('/sites')">取消</el-button>
            <el-button type="primary" round :loading="submitting" @click="submit">提交申请</el-button>
          </div>
        </div>
      </el-form>
    </template>
  </div>
</template>

<style scoped>
.apply-page { padding: 34px 20px 56px; }
.apply-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.eyebrow { font-size: 12px; letter-spacing: 2px; font-weight: 600; color: var(--lf-primary); text-transform: uppercase; }
.apply-hero .lf-page-title { margin-top: 6px; }
.steps { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 26px 0 22px; list-style: none; }
.step { display: flex; align-items: center; gap: 10px; padding: 14px 16px; border: 1px solid var(--lf-border); border-radius: var(--lf-radius); background: #fff; }
.step-index { display: flex; align-items: center; justify-content: center; width: 30px; height: 30px; flex-shrink: 0; border-radius: 50%; background: #f3f4f6; color: var(--lf-text-3); font-size: 13px; font-weight: 600; }
.step b { display: block; font-size: 13.5px; font-weight: 600; }
.step small { display: block; margin-top: 2px; font-size: 11.5px; color: var(--lf-text-3); }
.step--done .step-index { background: #e7f8f1; color: var(--lf-success); }
.step--current { border-color: #bcd4fb; background: linear-gradient(120deg, #fff, #f2f7ff); box-shadow: var(--lf-shadow-sm); }
.step--current .step-index { background: var(--lf-primary); color: #fff; }
.progress-bar { display: flex; align-items: center; gap: 14px; padding: 14px 20px; margin-bottom: 16px; font-size: 13px; }
.progress-bar .el-progress { flex: 1; max-width: 300px; }
.progress-bar b { color: var(--lf-primary); }
.progress-bar small { margin-left: auto; color: var(--lf-text-3); }
.duplicate-alert { margin-bottom: 16px; }
.apply-card { padding: 28px 30px 24px; }
.apply-card h2 { display: flex; align-items: center; gap: 10px; font-size: 17px; font-weight: 600; margin-top: 6px; }
.apply-card h2:not(:first-child) { margin-top: 34px; padding-top: 26px; border-top: 1px dashed var(--lf-border); }
.section-index { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 9px; background: var(--lf-primary-soft); color: var(--lf-primary); font-size: 12px; }
.section-hint { margin: 6px 0 18px; font-size: 12.5px; color: var(--lf-text-3); }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 22px; }
.form-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--lf-border); }
.form-footer p { font-size: 12px; color: var(--lf-text-3); max-width: 520px; line-height: 1.7; }
.result-card { padding: 48px 32px; text-align: center; background: linear-gradient(160deg, #fff, #f4f8ff); }
.result-mark { display: flex; align-items: center; justify-content: center; width: 62px; height: 62px; margin: 0 auto 18px; border-radius: 50%; background: #e7f8f1; color: var(--lf-success); font-size: 30px; font-weight: 700; }
.result-card h2 { font-size: 22px; font-weight: 600; }
.result-card p { max-width: 520px; margin: 10px auto 0; font-size: 13.5px; line-height: 1.8; color: var(--lf-text-2); }
.result-note { font-size: 12.5px !important; color: var(--lf-text-3) !important; }
.result-ops { display: flex; justify-content: center; gap: 12px; margin-top: 24px; }
@media (max-width: 860px) {
  .steps { grid-template-columns: repeat(2, 1fr); }
  .form-grid { grid-template-columns: 1fr; }
  .progress-bar { flex-wrap: wrap; }
  .progress-bar small { margin-left: 0; }
}
@media (max-width: 520px) {
  .steps { grid-template-columns: 1fr; }
  .apply-hero { flex-direction: column; align-items: flex-start; }
  .apply-card { padding: 20px 16px; }
  .form-footer { flex-direction: column; align-items: flex-start; }
}
</style>

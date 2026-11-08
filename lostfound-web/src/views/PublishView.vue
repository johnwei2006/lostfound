<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { fileApi, itemApi, siteApi } from '@/api'
import type { FoundSite } from '@/types'
import { CATEGORIES } from '@/types'
import { useUserStore } from '@/stores/user'
import MapPicker from '@/components/MapPicker.vue'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const type = ref<'found' | 'lost'>((route.query.type as 'found' | 'lost') === 'found' ? 'found' : 'lost')
const form = ref({
  title: '',
  category: '',
  description: '',
  images: [] as string[],
  province: '',
  city: '',
  district: '',
  address: '',
  longitude: null as number | null,
  latitude: null as number | null,
  itemTime: '',
  contact: '',
  siteId: undefined as number | undefined,
})
const sites = ref<FoundSite[]>([])
const submitting = ref(false)

const isFound = computed(() => type.value === 'found')

onMounted(async () => {
  if (!store.isLoggedIn) {
    ElMessage.warning('请先登录后再发布')
    router.push('/login?redirect=/publish')
    return
  }
  const s = await siteApi.page({ page: 1, size: 50 })
  sites.value = s.records
})

const onMapUpdate = (payload: { longitude: number | null; latitude: number | null; address: string }) => {
  form.value.longitude = payload.longitude
  form.value.latitude = payload.latitude
  if (payload.address) form.value.address = payload.address
}

const uploading = ref(false)
const customUpload = async (options: { file: File; onSuccess: () => void; onError: (e: Error) => void }) => {
  uploading.value = true
  try {
    const { url } = await fileApi.upload(options.file)
    form.value.images.push(url)
    options.onSuccess()
  } catch (e: any) {
    options.onError(e)
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const submit = async () => {
  if (!form.value.title.trim()) return ElMessage.warning('请填写物品名称')
  if (!form.value.category) return ElMessage.warning('请选择物品类别')
  submitting.value = true
  try {
    await itemApi.publish(type.value, {
      ...form.value,
      itemTime: form.value.itemTime ? dayjs(form.value.itemTime).format('YYYY-MM-DD HH:mm:ss') : null,
      contact: form.value.contact || store.user?.phone || '',
    })
    ElMessage.success('发布成功！系统将自动进行智能匹配')
    router.push(`/${type.value}`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="lf-container publish-page">
    <h1 class="lf-page-title">发布{{ isFound ? '拾物招领' : '失物寻找' }}</h1>
    <p class="lf-page-sub" style="margin-bottom: 24px">
      {{ isFound ? '捡到物品？发布后系统会自动匹配寻物启事' : '丢失物品？发布后系统会自动匹配拾物信息' }}
    </p>

    <el-card shadow="never" class="publish-card">
      <!-- 类型切换 -->
      <div class="type-switch">
        <button class="type-btn" :class="{ active: type === 'lost', 'type-btn--lost': type === 'lost' }" @click="type = 'lost'">
          我丢了东西
        </button>
        <button class="type-btn" :class="{ active: type === 'found', 'type-btn--found': type === 'found' }" @click="type = 'found'">
          我捡到东西
        </button>
      </div>

      <el-form label-position="top" style="max-width: 720px">
        <el-form-item label="物品名称" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：蓝色学生证（张三）" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物品类别" required>
              <el-select v-model="form.category" placeholder="选择类别" style="width: 100%">
                <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="isFound ? '拾获时间' : '丢失时间'">
              <el-date-picker
                v-model="form.itemTime"
                type="datetime"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="物品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            :placeholder="isFound ? '描述物品外观、发现地点、当前保管方式等' : '描述物品特征、丢失时间地点等，特征越详细越容易匹配'"
          />
        </el-form-item>

        <el-form-item label="物品图片（最多 6 张）">
          <el-upload
            list-type="picture-card"
            :file-list="form.images.map((u, i) => ({ name: i + '', url: u }))"
            :http-request="customUpload as any"
            :limit="6"
            accept="image/*"
            :on-remove="(_: any, fileList: any[]) => (form.images = fileList.map((f: any) => f.url))"
          >
            <el-icon :size="22"><UploadFilled /></el-icon>
          </el-upload>
        </el-form-item>

        <el-form-item :label="isFound ? '拾获地点' : '丢失地点'">
          <MapPicker
            :longitude="form.longitude"
            :latitude="form.latitude"
            :address="form.address"
            @update="onMapUpdate"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="省">
              <el-input v-model="form.province" placeholder="省" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="市">
              <el-input v-model="form.city" placeholder="市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区">
              <el-input v-model="form.district" placeholder="区" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址">
          <el-input v-model="form.address" placeholder="详细地点" />
        </el-form-item>
        <el-form-item v-if="isFound" label="交至招领站（选填）">
          <el-select v-model="form.siteId" placeholder="物品当前保管在哪个招领站" clearable style="width: 100%">
            <el-option v-for="s in sites" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="form.contact" placeholder="手机号 / 微信号（认领通过后展示给对方）" />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          round
          style="width: 220px"
          :loading="submitting"
          @click="submit"
        >
          发布{{ isFound ? '招领' : '寻物' }}启事
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.publish-page {
  padding: 34px 20px 60px;
}
.publish-card {
  border-radius: var(--lf-radius);
  margin-top: 16px;
}
.type-switch {
  display: flex;
  gap: 12px;
  margin-bottom: 26px;
}
.type-btn {
  border: 1.5px solid var(--lf-border);
  background: #fff;
  padding: 12px 28px;
  border-radius: 12px;
  font-size: 15px;
  cursor: pointer;
  color: var(--lf-text-2);
  transition: all 0.25s var(--ease-out);
}
.type-btn--lost.active {
  border-color: var(--lf-danger);
  color: var(--lf-danger);
  background: #fef2f2;
  font-weight: 600;
}
.type-btn--found.active {
  border-color: var(--lf-success);
  color: var(--lf-success);
  background: #ecfdf5;
  font-weight: 600;
}
</style>

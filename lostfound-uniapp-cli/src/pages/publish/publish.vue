<template>
  <view class="publish">
    <!-- 类型切换 -->
    <view class="type-switch">
      <view class="ts-item" :class="{ active: type === 'lost', danger: type === 'lost' }" @tap="type = 'lost'">
        我丢了东西
      </view>
      <view class="ts-item" :class="{ active: type === 'found', success: type === 'found' }" @tap="type = 'found'">
        我捡到东西
      </view>
    </view>

    <view class="lf-card">
      <view class="form-item">
        <text class="label">物品名称</text>
        <input v-model="form.title" class="input" placeholder="如：蓝色学生证" maxlength="100" />
      </view>
      <view class="form-item">
        <text class="label">物品类别</text>
        <picker :range="categories" @change="(e) => (form.category = categories[e.detail.value])">
          <view class="input picker">{{ form.category || '请选择类别' }}</view>
        </picker>
      </view>
      <view class="form-item">
        <text class="label">{{ type === 'found' ? '拾获时间' : '丢失时间' }}</text>
        <picker mode="date" @change="(e) => (form.date = e.detail.value)">
          <view class="input picker">{{ form.date || '选择日期' }}</view>
        </picker>
      </view>
      <view class="form-item">
        <text class="label">物品描述</text>
        <textarea
          v-model="form.description"
          class="textarea"
          :placeholder="type === 'found' ? '描述物品外观、发现地点、保管方式等' : '描述物品特征、丢失时间地点，越详细越容易匹配'"
          maxlength="2000"
        />
      </view>

      <!-- 九宫格上传 -->
      <view class="form-item">
        <text class="label">物品图片</text>
        <view class="grid">
          <view v-for="(img, i) in images" :key="i" class="grid-item">
            <image :src="img" mode="aspectFill" />
            <view class="grid-del" @tap.stop="removeImage(i)">×</view>
          </view>
          <view v-if="images.length < 6" class="grid-add" @tap="chooseImage">＋</view>
        </view>
      </view>

      <!-- 地图选点：选点接口不可用时仍可手动输入地址，或用当前位置写入坐标 -->
      <view class="form-item">
        <text class="label">{{ type === 'found' ? '拾获地点' : '丢失地点' }}</text>
        <view class="location-row">
          <input v-model="form.address" class="input location-input" placeholder="输入地点，或点击右侧选点（可选）" />
          <view class="location-btn" @tap="chooseLocation">选点</view>
        </view>
        <view class="location-tip">
          <text @tap="useCurrentLocation">重新获取当前位置</text>
          <text v-if="form.longitude != null && form.latitude != null" class="location-coords">
            · 已选 {{ form.longitude }}, {{ form.latitude }}
          </text>
        </view>
        <text class="auto-tip">已自动带入当前位置，可点「选点」修改详细地址</text>
      </view>

      <view class="form-item">
        <text class="label">联系方式</text>
        <input v-model="form.contact" class="input" placeholder="微信号 / 手机号 / 邮箱" />
        <text class="auto-tip">已自动带入个人资料中的联系方式，可修改</text>
      </view>
    </view>

    <button class="lf-btn lf-btn--accent" :loading="submitting" @tap="submit">
      发布{{ type === 'found' ? '招领' : '寻物' }}启事
    </button>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { itemApi, CATEGORIES } from '@/utils/api'
import { uploadFile } from '@/utils/request'
import { ensurePrivacyAuthorize, ensureLocation, getCurrentLocation } from '@/utils/location'

const type = ref('lost')
const categories = CATEGORIES
const images = ref([])
const submitting = ref(false)

const form = reactive({
  title: '',
  category: '',
  date: '',
  description: '',
  address: '',
  longitude: null,
  latitude: null,
  contact: '',
})

/** 上次自动带入的联系方式：用户手动改过后不再覆盖 */
let lastAutoContact = ''

const todayStr = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 仅在用户未改动时自动填坐标；定位不可用（默认城市）不写入，避免虚假位置 */
const fillLocation = (loc) => {
  if (!loc || loc.available === false) return
  if (form.address || form.longitude != null) return
  form.longitude = loc.longitude
  form.latitude = loc.latitude
  form.address = loc.address || '当前位置'
}

/** 发布页自动带入：今日日期、个人资料联系方式、当前定位 */
const fillDefaults = () => {
  if (!form.date) form.date = todayStr()
  const me = uni.getStorageSync('lf_user') || {}
  const autoContact = me.contact || me.phone || me.email || ''
  if (!form.contact || form.contact === lastAutoContact) {
    form.contact = autoContact
    lastAutoContact = autoContact
  }
  fillLocation(getCurrentLocation())
}

onShow(() => {
  if (!uni.getStorageSync('lf_token')) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 600)
    return
  }
  fillDefaults()
  // 启动定位可能尚未完成，异步成功后补齐坐标（复用同一次启动的定位 Promise）
  ensureLocation().then(fillLocation).catch(() => {})
})

/** 任何失败都要给出真实原因，避免点击后毫无反应；主动取消不打扰用户 */
const showApiError = (title, err) => {
  const msg = (err && err.errMsg) || '未知错误'
  if (/cancel/i.test(msg)) return
  console.error('[publish]', title, msg)
  uni.showModal({
    title,
    content: `${msg}\n若为权限问题，请在微信公众平台开通该接口并配置用户隐私保护指引`,
    showCancel: false,
  })
}

const uploadPicked = async (paths) => {
  if (!paths.length) return
  uni.showLoading({ title: '上传中' })
  try {
    for (const path of paths) {
      const url = await uploadFile(path)
      images.value.push(url)
    }
  } catch (e) {
    uni.showToast({ title: e.message || '上传失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

const chooseImage = () => {
  // 图片选择不依赖地理位置隐私授权；失败时弹窗提示，不再静默吞掉
  const count = 6 - images.value.length
  const onFail = (err) => showApiError('选择图片失败', err)
  // 基础库 2.21+ 推荐 chooseMedia，旧版本回退 chooseImage
  if (typeof uni.chooseMedia === 'function') {
    return uni.chooseMedia({
      count,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: (res) => uploadPicked((res.tempFiles || []).map((f) => f.tempFilePath)),
      fail: onFail,
    })
  }
  uni.chooseImage({
    count,
    sizeType: ['compressed'],
    success: (res) => uploadPicked(res.tempFilePaths || []),
    fail: onFail,
  })
}
const removeImage = (i) => images.value.splice(i, 1)

const chooseLocation = async () => {
  // 隐私授权只做尽力触发，不阻断接口调用：由 chooseLocation 自身返回真实错误再提示
  await ensurePrivacyAuthorize()
  uni.chooseLocation({
    success: (res) => {
      form.address = res.address || res.name
      form.longitude = res.longitude
      form.latitude = res.latitude
    },
    fail: (err) => showApiError('打开地图选点失败', err),
  })
}

/** 选点接口不可用时（未开通 / 隐私协议未配置），用 getLocation 写入当前坐标兜底 */
const useCurrentLocation = async () => {
  await ensurePrivacyAuthorize()
  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      form.longitude = res.longitude
      form.latitude = res.latitude
      if (!form.address) form.address = '当前位置'
      uni.showToast({ title: '已写入当前位置', icon: 'none' })
    },
    fail: (err) => showApiError('定位失败', err),
  })
}

const submit = async () => {
  if (!form.title.trim()) return uni.showToast({ title: '请填写物品名称', icon: 'none' })
  if (!form.category) return uni.showToast({ title: '请选择类别', icon: 'none' })
  submitting.value = true
  try {
    await itemApi.publish(type.value, {
      title: form.title,
      category: form.category,
      description: form.description,
      images: images.value,
      address: form.address,
      longitude: form.longitude,
      latitude: form.latitude,
      itemTime: form.date ? form.date + ' 00:00:00' : null,
      contact: form.contact,
    })
    uni.showToast({ title: '发布成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/home/home' }), 900)
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.publish {
  padding: 24rpx;
}
.type-switch {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.ts-item {
  flex: 1;
  text-align: center;
  padding: 22rpx 0;
  border-radius: 16rpx;
  background: #fff;
  color: #6b7280;
  border: 3rpx solid #e5e9f0;
  font-size: 28rpx;
}
.ts-item.danger.active {
  border-color: #ef4444;
  color: #ef4444;
  background: #fef2f2;
  font-weight: 600;
}
.ts-item.success.active {
  border-color: #10b981;
  color: #10b981;
  background: #ecfdf5;
  font-weight: 600;
}
.form-item {
  margin-bottom: 26rpx;
}
.label {
  display: block;
  font-size: 26rpx;
  color: #6b7280;
  margin-bottom: 12rpx;
}
.input {
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 28rpx;
}
.picker {
  color: #1f2937;
}
.textarea {
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 28rpx;
  width: auto;
  height: 180rpx;
}
.location-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.location-input {
  flex: 1;
  min-width: 0;
}
.location-btn {
  flex-shrink: 0;
  background: #eaf1fe;
  color: #2563eb;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 26rpx;
}
.location-tip {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #2563eb;
}
.location-coords {
  color: #9ca3af;
}
.auto-tip {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #9ca3af;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.grid-item {
  position: relative;
  width: 180rpx;
  height: 180rpx;
}
.grid-item image {
  width: 100%;
  height: 100%;
  border-radius: 12rpx;
}
.grid-del {
  position: absolute;
  top: -12rpx;
  right: -12rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: rgba(239, 68, 68, 0.9);
  color: #fff;
  text-align: center;
  line-height: 40rpx;
  font-size: 28rpx;
}
.grid-add {
  width: 180rpx;
  height: 180rpx;
  border-radius: 12rpx;
  background: #f5f7fa;
  border: 2rpx dashed #c6d3ec;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60rpx;
  color: #9ca3af;
}
</style>

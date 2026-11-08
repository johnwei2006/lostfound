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

      <!-- 地图选点 -->
      <view class="form-item">
        <text class="label">{{ type === 'found' ? '拾获地点' : '丢失地点' }}</text>
        <view class="input picker" @tap="chooseLocation">
          {{ form.address || '点击选择位置（可选）' }}
        </view>
      </view>

      <view class="form-item">
        <text class="label">联系方式</text>
        <input v-model="form.contact" class="input" placeholder="手机号 / 微信号" />
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

onShow(() => {
  if (!uni.getStorageSync('lf_token')) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 600)
  }
})

const chooseImage = () => {
  uni.chooseImage({
    count: 6 - images.value.length,
    sizeType: ['compressed'],
    success: async (res) => {
      uni.showLoading({ title: '上传中' })
      try {
        for (const path of res.tempFilePaths) {
          const url = await uploadFile(path)
          images.value.push(url)
        }
      } catch (e) {
        uni.showToast({ title: e.message || '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
  })
}
const removeImage = (i) => images.value.splice(i, 1)

const chooseLocation = () => {
  uni.chooseLocation({
    success: (res) => {
      form.address = res.address || res.name
      form.longitude = res.longitude
      form.latitude = res.latitude
    },
    fail: () => {
      // 未授权或取消则忽略
    },
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

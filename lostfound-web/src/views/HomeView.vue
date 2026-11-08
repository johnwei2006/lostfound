<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Location, OfficeBuilding } from '@element-plus/icons-vue'
import { itemApi, siteApi } from '@/api'
import type { ItemVO, FoundSite } from '@/types'
import { CATEGORIES } from '@/types'
import ItemCard from '@/components/ItemCard.vue'

const router = useRouter()
const keyword = ref('')
const latestFound = ref<ItemVO[]>([])
const latestLost = ref<ItemVO[]>([])
const sites = ref<FoundSite[]>([])
const loading = ref(true)

const load = async () => {
  try {
    const [f, l, s] = await Promise.all([
      itemApi.latest('found', 4),
      itemApi.latest('lost', 4),
      siteApi.page({ page: 1, size: 4 }),
    ])
    latestFound.value = f
    latestLost.value = l
    sites.value = s.records
  } finally {
    loading.value = false
  }
}
onMounted(load)

const search = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  router.push({ name: 'foundList', query: { keyword: kw } })
}

const goCategory = (c: string) => router.push({ name: 'foundList', query: { category: c } })
</script>

<template>
  <div class="home">
    <!-- Hero -->
    <section class="hero">
      <div class="lf-container hero-inner">
        <h1 class="hero-title">让每一份遗失<br />都有归途</h1>
        <p class="hero-sub">发布失物与拾物信息，智能匹配帮你更快重逢</p>
        <div class="hero-search">
          <el-input
            v-model="keyword"
            size="large"
            placeholder="输入物品名称，如：校园卡、黑色钱包…"
            class="hero-input"
            @keyup.enter="search"
          >
            <template #append>
              <el-button type="primary" @click="search">
                <el-icon style="margin-right: 4px"><Search /></el-icon>搜索
              </el-button>
            </template>
          </el-input>
        </div>
        <div class="hero-actions">
          <el-button type="primary" size="large" round @click="router.push('/publish?type=lost')">
            我丢了东西
          </el-button>
          <el-button size="large" round class="hero-btn-found" @click="router.push('/publish?type=found')">
            我捡到东西
          </el-button>
        </div>
        <div class="hero-stats">
          <div class="stat"><strong>智能匹配</strong><span>失物拾物自动配对</span></div>
          <div class="stat"><strong>招领站网络</strong><span>线下站点就近托管</span></div>
          <div class="stat"><strong>地图定位</strong><span>丢失地点一目了然</span></div>
        </div>
      </div>
      <div class="hero-wave">
        <svg viewBox="0 0 1440 90" preserveAspectRatio="none">
          <path d="M0,60 C360,100 720,10 1440,55 L1440,90 L0,90 Z" fill="#f5f7fa" />
        </svg>
      </div>
    </section>

    <!-- 分类快捷入口 -->
    <section class="lf-container section">
      <div class="category-row">
        <button v-for="c in CATEGORIES" :key="c" class="category-item" @click="goCategory(c)">
          {{ c }}
        </button>
      </div>
    </section>

    <!-- 最新拾物 -->
    <section class="lf-container section">
      <div class="section-head">
        <div>
          <h2 class="lf-page-title">最新拾物</h2>
          <p class="lf-page-sub">捡到的物品等待认领，看看有没有你的</p>
        </div>
        <el-button text type="primary" @click="router.push('/found')">查看更多 →</el-button>
      </div>
      <div v-if="loading" class="grid">
        <div v-for="i in 4" :key="i" class="lf-card" style="height: 280px"><div class="lf-skeleton" style="height:100%;border-radius:14px"/></div>
      </div>
      <div v-else-if="latestFound.length" class="grid">
        <ItemCard v-for="it in latestFound" :key="it.id" :item="it" />
      </div>
      <el-empty v-else description="还没有拾物信息" :image="'/empty.svg'" />
    </section>

    <!-- 最新失物 -->
    <section class="lf-container section">
      <div class="section-head">
        <div>
          <h2 class="lf-page-title">最新寻物</h2>
          <p class="lf-page-sub">有人正在急切寻找，或许你能帮上忙</p>
        </div>
        <el-button text type="primary" @click="router.push('/lost')">查看更多 →</el-button>
      </div>
      <div v-if="loading" class="grid">
        <div v-for="i in 4" :key="i" class="lf-card" style="height: 280px"><div class="lf-skeleton" style="height:100%;border-radius:14px"/></div>
      </div>
      <div v-else-if="latestLost.length" class="grid">
        <ItemCard v-for="it in latestLost" :key="it.id" :item="it" />
      </div>
      <el-empty v-else description="还没有寻物信息" :image="'/empty.svg'" />
    </section>

    <!-- 招领站 -->
    <section class="lf-container section" style="padding-bottom: 48px">
      <div class="section-head">
        <div>
          <h2 class="lf-page-title">线下招领站</h2>
          <p class="lf-page-sub">拾获物品可就近交至招领站托管</p>
        </div>
        <el-button text type="primary" @click="router.push('/sites')">全部站点 →</el-button>
      </div>
      <div class="site-grid">
        <div v-for="s in sites" :key="s.id" class="site-card lf-card lf-card--hover" @click="router.push('/sites/' + s.id)">
          <div class="site-icon"><el-icon :size="20"><OfficeBuilding /></el-icon></div>
          <div class="site-info">
            <h4>{{ s.name }}</h4>
            <p><el-icon :size="12"><Location /></el-icon>{{ s.address }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%);
  color: #fff;
  padding: 76px 0 96px;
  overflow: hidden;
}
.hero::before {
  content: '';
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(249, 115, 22, 0.28), transparent 65%);
  top: -160px;
  right: -100px;
}
.hero-inner {
  position: relative;
  z-index: 1;
}
.hero-title {
  font-size: clamp(32px, 5vw, 52px);
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: 2px;
}
.hero-sub {
  margin-top: 14px;
  font-size: 16px;
  color: rgba(255, 255, 255, 0.82);
  letter-spacing: 1px;
}
.hero-search {
  margin-top: 30px;
  max-width: 560px;
}
.hero-search :deep(.el-input__wrapper) {
  border-radius: 999px 0 0 999px;
  height: 48px;
}
.hero-search :deep(.el-input-group__append) {
  border-radius: 0 999px 999px 0;
  background: var(--lf-accent);
  border: none;
  padding: 0 8px;
}
.hero-actions {
  margin-top: 24px;
  display: flex;
  gap: 14px;
}
.hero-actions :deep(.el-button--primary) {
  background: var(--lf-accent);
  border: none;
  padding: 22px 34px;
  font-size: 15px;
}
.hero-btn-found {
  padding: 22px 34px;
  font-size: 15px;
}
.hero-stats {
  margin-top: 44px;
  display: flex;
  gap: 40px;
  flex-wrap: wrap;
}
.stat strong {
  display: block;
  font-size: 16px;
  font-weight: 600;
}
.stat span {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.65);
}
.hero-wave {
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  line-height: 0;
}
.hero-wave svg {
  width: 100%;
  height: 70px;
}
.section {
  margin-top: 42px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 18px;
}
.category-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.category-item {
  border: 1px solid var(--lf-border);
  background: #fff;
  padding: 9px 22px;
  border-radius: 999px;
  font-size: 14px;
  cursor: pointer;
  color: var(--lf-text-2);
  transition: all 0.25s var(--ease-out);
}
.category-item:hover {
  border-color: var(--lf-primary);
  color: var(--lf-primary);
  background: var(--lf-primary-soft);
  transform: translateY(-2px);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 18px;
}
.site-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.site-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  cursor: pointer;
}
.site-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.site-info h4 {
  font-size: 14.5px;
  font-weight: 600;
}
.site-info p {
  font-size: 12px;
  color: var(--lf-text-3);
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

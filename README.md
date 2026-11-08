# 寻物志 · 失物招领平台（重构版）

基于 **SpringBoot 3 + Vue3 + uni-app (Vue3)** 重建的失物招领平台，对齐旧系统（`Lostfound_backend` / `LostFound_frontend` / 参考站 http://120.26.254.139/）全部核心功能，并在分页、缓存、图片压缩、鉴权等方面全面提升性能。

> 旧工程代码保留未动，新工程为以下三个独立目录。

## 工程结构

| 目录 | 技术栈 | 说明 |
|------|--------|------|
| `lostfound-server/` | SpringBoot 3.2 · JDK17 · MyBatis-Plus · MySQL 8 · JWT · Caffeine | 后端 REST API |
| `lostfound-web/` | Vue3 · Vite · TS · Element Plus · Pinia · ECharts | Web 单端（用户 + /admin 管理后台，角色路由守卫区分） |
| `lostfound-uniapp/` | uni-app Vue3 · uni.request · uni map | 微信小程序（可编译 H5/App） |

## 核心功能

- 账号注册/登录（JWT，默认管理员 `admin / admin123`），以及微信小程序 openid 静默登录：进入小程序即自动建号/登录，令牌失效自动重登
- **Web 先浏览、后登录**：游客可完整查看失物/拾物清单、详情、筛选、搜索、排序与距离；发布、关注、评论与认领会原地弹出登录引导，个人中心显示空态；本地登录态过期只静默清除，不打断浏览
- **微信扫码登录（与小程序同一身份）**：Web 展示小程序码，微信扫码后在小程序「确认登录网页」页点一次确认；后端把票据绑定到当前小程序 `openid` 对应的 `user.id` 并签发同一套 JWT，两端共享发布、关注、消息与认领记录。票据仅存服务内存（默认 5 分钟、一次性消费），不落库
- 小程序启动自动定位（`getLocation`），地图以当前位置为中心；拒绝授权时回退默认城市并自动切为时间排序
- Web 列表（`/lost`、`/found`）默认按距离近→远排序，可切换「最新发布 / 最多浏览」；浏览器定位（WGS84→GCJ02）在 HTTPS 或 localhost 下可用，拒绝授权时回退默认城市并自动切为时间排序，卡片显示距离
- 首页失物/拾物列表默认按距离近→远排序，可切换为按发布时间近→远，卡片显示距离
- **地图选点切换当前位置**：小程序首页、Web 首页 `/` 与列表页 `/lost`、`/found` 都可发起选点；选点后列表按新坐标重新按距离排序、卡片距离同步刷新。所选位置仅存于本次会话（模块内存，不写入 storage），小程序冷启动 / Web 刷新后自动恢复定位，任何时刻都可点「恢复当前位置」。Web 首页版块仍按最新展示，位置在全局生效
- 失物/拾物发布、浏览、关键词/类别/状态/时间筛选、分页
- 认领流转：提交申请（凭证描述）→ 发布者审核 → 物品状态流转（待认领/待确认/已完成/已下架）
- 招领站：列表/详情/站内物品托管，管理员管理站点
- 评论（两级回复）、关注/收藏、站内消息
- 图片上传（本地存储 + Thumbnailator 压缩 + 静态映射，可配置切对象存储）
- 智能匹配：发布后自动按 类别(40)+关键词相似度(30)+空间距离(20)+时间接近度(10) 计算，≥50 分写入 `match_record` 并双向消息通知
- 管理后台：物品审核、招领站管理、用户管理（禁用/恢复）、ECharts 数据统计（趋势/类别分布）
- 地图：高德地图（未配置 Key 自动降级为坐标输入/列表模式）

## 数据库

- 建表脚本：`lostfound-server/src/main/resources/sql/schema.sql`（MySQL 8，14 张表，含旧系统缺失的 message/claim_request/foundsite_manager/match_record 等）
- 参考：旧 `lostfound.sql`（11 表）与 16 个 `.hbm.xml` 实体映射；演示数据已脱敏，未导入

## 启动步骤

### 1. 后端（端口 8080）

```bash
# 1) 准备 MySQL 8，执行建表脚本
mysql -u root -p < lostfound-server/src/main/resources/sql/schema.sql

# 2) 配置（可用环境变量覆盖，详见 application.yml）
#    DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
#    JWT_SECRET / UPLOAD_DIR / UPLOAD_BASE_URL
#    WX_APPID / WX_SECRET（微信登录必需，已从代码默认值中移除，避免密钥入库）
#    SERVER_ADDRESS（生产设为 127.0.0.1，由 Nginx 反代）
cd lostfound-server
mvn spring-boot:run
```

启动后自动创建默认管理员 `admin / admin123`（按用户名判断，存量库同样会补齐）。
列表接口支持 `sort=distance&userLat=..&userLng=..`（距离近→远，返回 `distance` 公里数）、
`sort=time`（发布时间近→远）、`sort=hot`（浏览量）；未传合法坐标时自动回退时间排序。

### 2. Web 前端（端口 5173，已配置 /api 与 /uploads 代理）

```bash
cd lostfound-web
npm install
npm run dev          # 开发
npm run build        # 生产构建 → dist/
```

本地未启动后端时，可让 Vite 代理指向线上接口以直接预览真实数据：

```powershell
$env:VITE_API_TARGET = 'https://www.globalfinding.com'
npm run dev
```

可选：复制 `.env.example` 为 `.env` 并填入 `VITE_AMAP_KEY`（高德 Web 端 Key）启用地图。
已预置旧系统的高德 Key（`.env` 中 `VITE_AMAP_KEY`，沿用原 `gaodeMap.js` 的 Key，JS API 2.0 验证可用）；uni-app H5 端 Key 已配置在 `manifest.json → h5.sdkConfigs.maps.amap`。

本地调试选点：Web 弹层复用 `src/components/MapPicker.vue`，未配置 `VITE_AMAP_KEY` 时降级为 GCJ02 经纬度输入（经度 -180~180、纬度 -90~90，不做二次坐标换算）。浏览器定位仅在 HTTPS 或 `localhost` 下可用；手动位置不写入 `localStorage`，刷新即恢复自动定位，可用 `sort=distance&userLat=..&userLng=..` 对比不同坐标验证排序与 `distance` 变化。小程序端使用微信原生 `uni.chooseLocation`，需在微信开发者工具或真机中验证。

本地调试扫码登录：后端需配置 `WX_SECRET`（用于 `cgi-bin/token` 换取 `access_token`）；未配置时 Web 弹层会显示「生成小程序码失败」并保留账号密码登录。开发阶段小程序码默认指向体验版（`lostfound.qr-login.env-version=trial`、`check-path=false`），需先用微信开发者工具把包含 `pages/web-login/web-login` 的版本上传为体验版。票据只保存在后端进程内存，重启后端后旧二维码立即失效；Web 与小程序必须指向同一个后端实例，否则状态轮询与确认不在同一票据缓存上。

### 3. 微信小程序（HBuilderX）

1. HBuilderX 导入 `lostfound-uniapp` 目录
2. 修改 `utils/request.js` 中的 `BASE_URL` 为后端地址（真机调试改为局域网 IP）
3. `manifest.json` 中填入微信小程序 `appid`（测试可留空，用开发者工具"测试号"）
4. 运行 → 运行到小程序模拟器 → 微信开发者工具

## 生产部署要点

1. **配置外置**：`DB_PASSWORD`、`JWT_SECRET`、上传目录等全部通过环境变量注入（旧代码中明文 appsecret/密码已移除）
2. **前端部署**：`npm run build` 产物部署至 Nginx，`/api` 与 `/uploads` 反代到后端 8080
3. **图片存储**：默认本地磁盘 `./uploads`，静态映射 `/uploads/**`；切 OSS 时仅需替换 `FileService`
4. **审核开关**：`lostfound.review.required=true` 时发布需管理员审核（默认关闭，管理员仍可驳回下架）
5. **性能**：热点列表 Caffeine 缓存（5 分钟）、分页插件（单页上限 100）、HikariCP 连接池、图片压缩存储、核心字段索引 + 全文索引（ngram）

## 与旧系统对照

| 旧系统 | 重构后 |
|--------|--------|
| Ant + JDK1.6 + Spring XML + Hibernate 3 + Jersey | Maven + JDK17 + SpringBoot 3 + MyBatis-Plus |
| WeX5 前端（.w XML 模板，不可复用） | Vue3 + Element Plus 单端（含管理后台） |
| 支付宝小程序空壳 | uni-app Vue3 微信小程序（完整业务） |
| 微信 OAuth 登录 + 微信支付 | 账号体系 + JWT（微信能力预留，未选择） |
| Google Map + 高德混用 | 高德统一（降级策略） |
| FTP 图片上传 | 本地存储 + 压缩，可切 OSS |
| 无审核/统计 | 物品审核 + 用户管理 + ECharts 统计 |
=======
# lostfound


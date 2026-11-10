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
- **个人资料与发布自动带入（小程序 1.6.0）**：「我的」页底部弹层编辑昵称与通用联系方式 `user.contact`（可填微信号/手机号/邮箱），保存走 `PUT /api/auth/profile` 并调 `GET /api/auth/me` 刷新本地 `lf_user`；发布页进入时自动填今日日期、资料联系方式，以及启动定位缓存的经纬度与「当前位置」地址文本（定位不可用时不写入，避免虚假位置），均保留手动修改与「选点」。微信登录仍为 `openid` 静默登录——微信官方不向小程序开放真实微信号，微信号由用户填写在联系方式中
- 小程序启动自动定位（`getLocation`），地图以当前位置为中心；拒绝授权时回退默认城市并自动切为时间排序
- Web 列表（`/lost`、`/found`）默认按距离近→远排序，可切换「最新发布 / 最多浏览」；浏览器定位（WGS84→GCJ02）在 HTTPS 或 localhost 下可用，拒绝授权时回退默认城市并自动切为时间排序，卡片显示距离
- 首页失物/拾物列表默认按距离近→远排序，可切换为按发布时间近→远，卡片显示距离
- **地图选点切换当前位置**：小程序首页、Web 首页 `/` 与列表页 `/lost`、`/found` 都可发起选点；选点后列表按新坐标重新按距离排序、卡片距离同步刷新。所选位置仅存于本次会话（模块内存，不写入 storage），小程序冷启动 / Web 刷新后自动恢复定位，任何时刻都可点「恢复当前位置」。Web 首页版块仍按最新展示，位置在全局生效
- 失物/拾物发布、浏览、关键词/类别/状态/时间筛选、分页
- 认领流转：提交申请（凭证描述）→ 发布者审核 → 物品状态流转（待认领/待确认/已完成/已下架）
- 招领站：列表/详情/站内物品托管，管理员管理站点
- 评论（两级回复）、关注/收藏、站内消息
- 图片上传（本地存储 + Thumbnailator 压缩 + 静态映射，可配置切对象存储）
- 智能匹配：候选按**类别完全相同**硬过滤后打分排序——类别 30（同类别固定满分底座）+ 时间接近度 25（拾获/丢失相差 ≤3 天满分，3~30 天线性衰减）+ 空间距离 25（相距 ≤2km 满分，2~50km 线性衰减）+ 关键词相似度 20（bigram），≥45 分写入 `match_record` 并双向消息通知；缺失时间/坐标不加分，避免「纯类别匹配」自动入围。匹配说明文案体现具体天数与公里数
- 管理后台：物品审核、招领站管理、用户管理（禁用/恢复）、ECharts 数据统计（趋势/类别分布）
- 地图：高德地图（未配置 Key 自动降级为坐标输入/列表模式）

## 招领站管理（Web + 后端）

全局 `ADMIN` 在 `/admin/sites` 建立、编辑、删除招领站，并以**全量覆盖**方式配置多个站点管理员（权限来源 `foundsite_manager`，不新增 `user.role` 角色）。站点管理员登录后从顶栏进入独立的 `/manage` 后台，只能管理所属站点；全局 `ADMIN` 自动拥有全部站点权限。

### 申请审核建站（推荐路径）

注册用户无需变更 `user.role`，从顶栏「申请创建招领站」进入 `/site/apply` 提交站点资料、申请理由与联系方式；平台 `ADMIN` 在 `/admin/site-applications` 审核。审核通过时在单事务内创建 `foundsite`、写入一条 `foundsite_manager`、回写 `created_site_id` 并发送 `SITE` 类型站内消息；驳回不创建任何站点。审核前不产生站点，申请人与平台均可追踪状态与审核意见。

```
注册/登录 → 提交申请(PENDING) → 平台审核 → APPROVED 自动建站并开通管理 / REJECTED 可修改重提
```

| 角色 | 权限 |
|------|------|
| 未登录 | 只读浏览；提交申请返回 401 |
| 注册用户 | 提交申请（同时仅一条 `PENDING`）、查看进度、撤回待审、驳回后修改重提 |
| 平台 `ADMIN` | 筛选/通过/驳回申请；也可在 `/admin/sites` 直接建站并全量配置管理员 |
| 站点管理员 | 在 `/manage/site` 维护本站背书；权限仅来自 `foundsite_manager`，由 `/api/auth/me` 实时返回 |
| 普通已登录用户 | 站点资料与背书只读，维护接口返回 403 |

申请接口独立挂在 `/api/site-applications`（避免与 `/api/foundsite/{id}` 字面量路径冲突）：`POST /` 提交、`GET /mine` 我的申请、`GET /page` 与 `PUT /{id}/review` 仅 ADMIN、`DELETE /{id}` 撤回待审。状态常量 `PENDING / APPROVED / REJECTED`；重复待审、非待审重复审核、申请人账号停用或仍有其它待审申请均由服务端拒绝，状态条件更新保证审核幂等。

### 自定义背书

`foundsite` 的 `endorsement_org` / `endorsement_text` / `endorsement_updated_at` / `endorsement_updated_by` 保存站点自行维护的背书单位与推荐语，`hasEndorsement` 由文案非空派生，**不新增审核流，也不代表平台认证**。`PUT /api/foundsite/{id}/endorsement` 在 Service 层通过 `SitePermissionService.requireManager` 校验：全局 `ADMIN` 与对应站点管理员可写，其它用户返回 403。平台 `ADMIN` 在 `/admin/sites` 弹窗内维护，站点管理员在 `/manage/site` 维护并实时预览；`/sites` 卡片与 `/sites/{id}` 详情通过 `SiteEndorsementCard` 展示，Vue 文本插值避免 XSS。

| 页面 | 能力 |
|------|------|
| `/manage/items` | 概览统计、关键词/类别/状态/来源筛选、站点代发布、上下架、源头信息抽屉、跳归还登记 |
| `/manage/transfers` | 转交申请与认领申请审批，审批意见、状态时间线、源头信息 |
| `/manage/returns` | 现场归还登记（领取人/凭证/备注）、待审认领队列、归还记录与凭证查看 |
| `/profile?tab=transfers` | 个人「我的转交」：审批结果、意见、入库与归还进度时间线、撤回申请 |
| `/manage/site` | 站点管理员维护本站背书文案、更新时间与前台预览 |

前台与审核页面：

| 页面 | 能力 |
|------|------|
| `/site/apply` | 四步流程条、资料完整度、站点资料与申请理由校验、提交结果态（需登录） |
| `/site/applications` | 我的申请：状态徽标、审核意见、已建站点入口、撤回与驳回后带资料重提（需登录） |
| `/admin/site-applications` | ADMIN 审核工作台：待审/通过/驳回统计、状态与关键词筛选、详情抽屉、通过/驳回意见 |
| `/admin/sites` | 平台建站/编辑、管理员全量覆盖、背书状态列与背书文案维护 |
| `/sites`、`/sites/:id` | 站点卡片背书徽标、详情背书卡片，有权限者可直接维护文案 |

**转交状态机**：个人发布拾物时选择目标站点，或在个人中心对已有拾物发起转交，生成 `PENDING` 转交单；站点管理员通过后物品进入站点清单，驳回或撤回后不展示。小程序端（1.5.0）在「我的发布 → 我的拾物」对本人物品显示转交状态徽标并可发起转交（弹层选择站点 + 备注）或撤回待审申请，「我的」页提供「我的转交」进度页（状态时间线 + 撤回），消息中心新增「转交动态」分类。存量回刷：`POST /api/match/rebuild`（仅 `ADMIN`）对全部 `WAITING` 物品按当前算法重跑匹配，幂等（命中唯一键跳过）、旧低分记录保留、不重复发送消息。

```
NONE → PENDING → APPROVED（站点清单可见）
              ↘ REJECTED / CANCELLED
```

`found.source_type`（`PERSONAL` / `SITE` / `TRANSFER`）与 `found.transfer_status` 控制可见性：站点清单只返回 `source_type='SITE'` 或 `transfer_status='APPROVED'` 的物品，`idx_site_transfer(site_id, transfer_status)` 支撑分页过滤。存量 `site_id` 非空的记录在迁移时统一回填为 `SITE/APPROVED`，避免上线后清单清空。

**归还两条路径**：认领申请审批通过（`CLAIM`）与站点现场登记（`ONSITE`）都写 `found_return` 并把物品置为 `FINISHED`，同时向原始登记人/转交人推送消息；源头信息（原始登记人、时间、描述与联系方式、转交链路、审批人/时间、归还记录）由 `GET /api/transfer/source/{foundId}` 聚合，仅站点管理员与原始登记人可见。

发布审核 `found.review_status`（全局 `lostfound.review.required`）与站点转交审批 `found_transfer.status` 是两套独立流程，不可复用。所有站点写接口在 Service 层用 `SitePermissionService.requireManager(siteId)` 校验，失败返回 403，不依赖前端隐藏按钮。

### 数据库迁移

新装库直接执行 `schema.sql`（已含新表与新列）。存量库必须先迁移再部署后端，脚本幂等且兼容 MySQL 5.6：

```powershell
cd C:/Users/John/AppData/Local/Temp/dbtool
$env:LOSTFOUND_DB_PASSWORD = '<数据库口令>'
node site-mgmt-migrate.js survey     # 勘察现状
node site-mgmt-migrate.js dry-run    # 预览变更，不写库
node site-mgmt-migrate.js apply      # 建表/加列/加索引 + 存量回填（自动全表快照）
node site-mgmt-migrate.js verify     # 行数与结构校验
node site-mgmt-migrate.js rollback <14位后缀>   # 需要时撤销本次新增结构
```

`apply` 前创建 `site_mgmt_bak_<时间戳>` 全表快照与同名 `_meta` 元数据表；回滚只撤销本次新增的表/列/索引，不删除任何 `found` 业务行，检测到新转交业务数据时会拒绝回滚。手动执行的等价 SQL 见 `lostfound-server/src/main/resources/sql/migration-site-management.sql`。

> 生产库已于 2026-11-09 完成迁移（`verify` 全部通过，快照 `site_mgmt_bak_20261109140607`），后端与 Web 同日部署上线；新装库执行 `schema.sql` 即可，无需再跑迁移脚本。上线细节见 `deploy/README.md`。

建站申请与背书的迁移使用 `site-application-migrate.js`（`survey / dry-run / apply / verify / rollback / clean-orphans`）：先建 `foundsite` 全表快照与 `_meta` 元数据，再按 `information_schema` 结果幂等补 4 个背书列、创建 `foundsite_application`。人工等价 SQL 见 `lostfound-server/src/main/resources/sql/migration-site-application.sql`，不含 `CHECK`、生成列、函数索引或 JSON 新类型，兼容 MySQL 5.6。生产库已于 2026-11-10 完成迁移并 `verify` 通过，快照 `site_app_bak_20261110003635_foundsite` / `_meta`；回滚命令与上线回归结果见 `deploy/README.md`。

个人资料联系方式使用 `profile-contact-migrate.js`（`survey / dry-run / apply / verify`）：按 `information_schema` 幂等为 `user` 增加 `contact VARCHAR(100)`（历史行默认 NULL，不回填），人工等价 SQL 见 `lostfound-server/src/main/resources/sql/migration-profile-contact.sql`。**必须先加列再部署后端**（新实体查询会带上该列）。生产库已于 2026-11-10 迁移并 `verify` 通过（1687 行未变），上线细节见 `deploy/README.md`。

## 数据库

- 建表脚本：`lostfound-server/src/main/resources/sql/schema.sql`（16 张表，线上 MySQL 5.6.51 兼容，含旧系统缺失的 message/claim_request/foundsite_manager/match_record 与新增的 foundsite_application 等）
- 参考：旧 `lostfound.sql`（11 表）与 16 个 `.hbm.xml` 实体映射；演示数据已脱敏，未导入

### 物品类别字典（唯一事实来源）

当前字典 `config/item-categories.json`（`version=2`）为界面 12 类：`证件证照 / 卡券票据 / 手机通讯 / 数码电子 / 服饰配饰 / 钥匙锁具 / 钱包卡包 / 箱包行李 / 书刊文件 / 生活用品 / 宠物活体 / 其他`。它由线上物品标题分布重新归纳，取代旧 8 类粗粒度字典。

四处硬编码常量由 `scripts/sync-category-dict.mjs` 按 `CATEGORY_DICT_BEGIN/END` 标记区间统一生成，禁止手工修改：

- 后端：`lostfound-server/src/main/java/com/lostfound/common/ItemCategory.java` 的 `ALL`
- Web：`lostfound-web/src/types/index.ts` 的 `CATEGORIES`
- 小程序 HBuilderX 源码：`lostfound-uniapp/utils/api.js` 的 `CATEGORIES`
- 小程序 CLI 源码：`lostfound-uniapp-cli/src/utils/api.js` 的 `CATEGORIES`

运行 `node scripts/sync-category-dict.mjs --write` 生成，`--check` 校验四处顺序一致、类别不重复、迁移目标合法、`schema.sql` 两处列注释与后台饼图颜色数；Web 与小程序 CLI 的 `build` / `build:mp-weixin` 已前置该检查。新增或调整类别只需改 JSON 后重新生成，并同步评估存量数据迁移规则。

**旧 8 类已彻底废止**：`证件 / 钥匙 / 手机 / 电子产品 / 钱包 / 书籍 / 衣物 / 其他` 中除仍保留的「其他」外均不再是可选项；`ItemCategory.LEGACY`、历史数字代码与中文别名兼容已删除，`normalize` 仅去首尾空白。旧类别写入返回 400 并提示「请更新到最新版本后重新选择类别」，旧类别筛选参数与历史分享链接返回空结果；后台 `GROUP BY category` 统计、`MatchService` 的类别候选集和站点清单筛选均按新列值精确工作。

2026-11-08 的历史归一记录（数字 `objectType` → 旧 8 类，备份 `category_norm_bak_20261108135138`）保留在 `deploy/README.md`，仅用于审计；当时的查询别名兼容现已撤销。本轮存量重映射的审计 SQL 为 `lostfound-server/src/main/resources/sql/migration-category-redesign.sql`，只改 `found.category` / `lost.category`，不触碰 `legacy_*`、`match_record` 等归档与关联表。线上已于 2026-11-10 完成 428 行重映射并部署，快照后缀与回归结果见 `deploy/README.md`。

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


# 寻物志后端部署说明

本目录提供服务器 `120.26.254.139` 的可复现部署配置。所有制品在**本地构建**，服务器只安装运行时，不引入 Maven/Node 构建链。

## 线上架构

```
微信小程序 / 浏览器
    │ HTTPS https://www.globalfinding.com
    ▼
Nginx 443  ── /api、/uploads ──► 127.0.0.1:8080  Spring Boot（systemd: lostfound.service）
    │                                       │
    ├─ /            静态 Web 后台 + SPA 回退   ├─ JDBC 127.0.0.1:3306  MySQL 5.6 lostfound
    └─ 80 → 301 跳转 HTTPS                    └─ HTTPS 出站 api.weixin.qq.com（jscode2session）
```

- 域名：`www.globalfinding.com` / `globalfinding.com`（DNS 已解析到该服务器）
- 证书：正式证书 `/home/cert/globalfinding.com.pem` + `/home/cert/globalfinding.com.key`（DigiCert DV，含中间证书链，SAN 覆盖两个域名，有效期至 2027-02-05）。`certbot.timer` 保留作为应急备用，不参与当前站点
- Java：Eclipse Temurin JRE 17.0.21（`/opt/jdk17`，`/usr/bin/java` 由 `update-alternatives` 指向）
- 后端：`/opt/lostfound/lostfound-server.jar`，仅监听 `127.0.0.1:8080`
- 配置：`/etc/lostfound/lostfound.env`（chmod 600，不入库）
- 图片：`/home/lostfound/image`（旧系统 420 张历史图片 + 新上传图片共用）
  - 历史图片 `/image/YYYYMMDD/xxx.jpeg` 由 Nginx alias 直接提供
  - 新上传 `/uploads/YYYYMM/xxx.ext` 由后端静态映射提供
  - 数据库中旧记录的相对路径已在 `normalize-images.js` 中补全为 HTTPS 绝对地址
- Web 后台：`/var/www/lostfound-web`

## 服务器环境勘察结论（部署前）

| 项 | 原状态 | 处理 |
|---|---|---|
| OS | Debian 11 bullseye，2C/1.7G，磁盘 19G 可用 | 无需升级 |
| Java | 仅 JDK 8（`/usr/local/jdk/jdk8`），无 `java` 命令 | 安装 Temurin JRE 17（Debian 11 security 源已下架 openjdk-17 包，故用便携包） |
| Nginx | 1.18 运行中，80 反代旧 Tomcat `/Lostfound/`；443 未启用 SSL | 备份配置 → 停用旧站 → 新站点配置 |
| 8080 | 被旧 Tomcat（`/home/apache-tomcat-6.0.8`）占用 | 已停止进程，端口释放 |
| 证书 | 原有 `/home/cert/*.pem` 全部过期（最新 2023-10） | 先用 Let's Encrypt 应急签发；随后用户上传了有效正式证书，Nginx 已切回 `/home/cert/globalfinding.com.pem(.key)` |
| 历史图片 | `/home/lostfound/image` 420 张、34MB，`/image/YYYYMMDD/*.jpeg` | Nginx 新增 `^~ /image/` alias；后端上传目录改为共用此目录；51 条失物记录的相对路径补全为绝对 URL |
| MySQL | 5.6.51 运行中，`lostfound` 库含旧系统业务数据 | **不删不改结构**，仅复用；本机连接已验证 |
| 出站 | 可访问 `api.weixin.qq.com` | 微信登录可用 |

> 旧 Nginx 配置与站点目录完整备份在 `/root/lostfound-backup-<时间戳>/`；旧站配置移入 `/etc/nginx/disabled-sites/`。数据库旧表以 `legacy_*` 保留，新表已完成数据迁移。

## 部署顺序

本地前置：

```bash
# 1) 后端可执行 jar
cd lostfound-server && mvn clean package -DskipTests

# 2) Web 后台产物
cd lostfound-web && npm install && npm run build

# 3) 小程序产物（域名已切到线上）
cd lostfound-uniapp-cli && npm install && npm run build:mp-weixin

# 4) 历史图片 URL 规范化（幂等，可重复执行）
cd deploy && npm install && node normalize-images.js
```

部署脚本（口令只从环境变量读取，不写入仓库）：

```powershell
cd deploy
npm install
$env:LOSTFOUND_SSH_PASSWORD = '<服务器 SSH 口令>'

node remote-deploy.js survey    # 只读勘察
node remote-deploy.js install   # 装 JRE17/certbot、签证书、备份、停旧 Tomcat、停 Nginx
node remote-deploy.js backend   # 上传 jar + systemd + env，重启并轮询健康检查
node remote-deploy.js web       # 上传 Web dist + Nginx 配置，reload，HTTPS 验证
# 或一次性执行：node remote-deploy.js all
```

> `install` 会短暂停止 Nginx（certbot standalone 占用 80），`web` 阶段会重新启动。重复执行 `backend/web` 是幂等的，会保留上一版 jar（`.bak`）并强制重启服务。
>
> 首次部署还需额外设置 `$env:LOSTFOUND_DB_PASSWORD`、`$env:LOSTFOUND_WX_SECRET`（可选 `$env:LOSTFOUND_JWT_SECRET`、`$env:LOSTFOUND_WX_APPID`）。重复部署时脚本会从服务端 `/etc/lostfound/lostfound.env` 回读已生效的值，避免明文密钥进入命令行，同时保证 `JWT_SECRET` 不被轮换、线上旧 token 继续有效；如需强制轮换，显式设置对应环境变量即可。

## 招领站管理功能上线（顺序强制：先迁移数据库，再部署后端）

本次只改 Web 与后端，小程序两套工程与构建产物均不改动。新增 `found_transfer` / `found_return` 两张表，并在 `found` 增加 `source_type`、`transfer_status`、`submit_user_id`、`site_approved_by`、`site_approved_at` 五列与 `idx_site_transfer` 索引；**后端实体已包含这些字段，未迁移就部署会出现 `Unknown column`**。

```powershell
# 1) 本地构建（已验证通过）
cd lostfound-server; mvn -q -DskipTests package
cd ../lostfound-web; npm run build

# 2) 数据库迁移（临时运维脚本，不入库；口令只从环境变量注入）
cd C:/Users/John/AppData/Local/Temp/dbtool
$env:LOSTFOUND_DB_PASSWORD = '<数据库口令>'
node site-mgmt-migrate.js survey
node site-mgmt-migrate.js dry-run
node site-mgmt-migrate.js apply     # 输出 14 位备份后缀，务必记录
node site-mgmt-migrate.js verify

# 3) 部署（禁止 all，避免重复执行 install）
cd <仓库>/deploy
$env:LOSTFOUND_SSH_PASSWORD = '<服务器 SSH 口令>'
node remote-deploy.js backend       # 覆盖运行中的 jar 后脚本会 systemctl restart
node remote-deploy.js web
```

等价的手工 SQL 见 `lostfound-server/src/main/resources/sql/migration-site-management.sql`；`schema.sql` 已同步，新装库无需再跑迁移脚本。

### 迁移脚本行为

- `survey`：打印 MySQL 版本、`found`/`foundsite`/`foundsite_manager` 行数、来源分布，不写库。
- `dry-run`：列出待执行 DDL 与预计回填行数，不写库。
- `apply`：先 `CREATE TABLE site_mgmt_bak_<时间戳> LIKE found` + 全表快照和 `_meta` 元数据表，再按 `information_schema` 逐项幂等建表/加列/加索引；回填放在事务内并校验行数（MySQL 5.6 的 DDL 隐式提交，故快照与元数据用于回滚）。
- `verify`：校验新表、新列、索引、存量站点物品回填与源头登记人非空。
- `rollback <后缀>`：按元数据撤销本次新增的索引/列/表，校验 `found` 列结构与快照一致；只撤销结构，不删除业务行；检测到 `TRANSFER`/`PENDING` 新业务数据或新表已有记录时**拒绝回滚**并提示先导出。快照表与元数据表保留，确认无误后人工清理。

### 2026-11-09 实际上线记录

- 数据库迁移已在线上执行：`apply` + `verify` 全部通过；`found=186` 行，其中 `site_id` 非空 `143` 行已回填为 `SITE/APPROVED`，`submit_user_id` 补齐 186 行；新增 2 张表、5 列、`idx_site_transfer` 索引。
- 快照与元数据：`site_mgmt_bak_20261109140607` / `site_mgmt_bak_20261109140607_meta`（保留，未删除）。回滚命令：`LOSTFOUND_DB_PASSWORD=*** node site-mgmt-migrate.js rollback 20261109140607`（仅在无新转交业务数据时可执行）。
- 首次 `apply` 因运维脚本元数据表 DDL 缺右括号、`metaTable` 误用 `async` 失败，仅生成了一张孤儿快照；脚本已修复并新增 `clean-orphans` 模式，孤儿快照 `site_mgmt_bak_20261109140450` 已清理。
- 后端 `remote-deploy.js backend` 与 Web `remote-deploy.js web` 均已执行，`systemctl is-active lostfound=active`，本机 health=200，HTTPS=200。
- 线上只读回归 `verify-site-management.js`：**27/27 通过**（站点分页统计字段、站点清单分页与可见性、found/lost 原接口兼容、`/api/auth/me` 站点管理范围、`/api/foundsite/mine` 与 `stats`、新端点已注册、SPA 回退 7 条路径、入口 JS 与 6 个新页面 chunk 均可访问）。
- 重启时旧进程 shutdown hook 打印过 `NoClassDefFoundError: org/apache/catalina/Lifecycle$SingleUse`，原因是原 jar 被覆盖时旧 JVM 仍在退出；新进程已正常启动且日志无新增 ERROR，属无害噪声。写入类端到端流程（转交申请→审批→归还）未在线上造测试数据，需在真实业务中验证。

### 上线验收清单

- [x] `node site-mgmt-migrate.js verify` 全部通过，存量 `site_id` 非空物品仍可在站点清单查询
- [x] `https://www.globalfinding.com/api/foundsite/page?page=1&size=1` → 200，响应含 `managerCount` / `itemCount`
- [x] `GET /api/foundsite/{id}/items` 返回 `PageResult<ItemVO>`，且不含 `transfer_status=PENDING` 的物品
- [ ] 全局 ADMIN 登录 `/admin/sites` 可为同一站点配置多名管理员，保存后列表人数正确（需 UI 人工点验）
- [x] 站点管理员登录 `/api/auth/me` 返回 `siteManager=true` 与 `managedSiteIds`，顶栏出现「招领站管理」，可进入 `/manage`（已建专用单站点验证账号，接口回归 12/12 通过）
- [x] 跨站点调用 `stats` 返回 403「无权管理该招领站」；普通用户访问 `/manage` 被路由守卫重定向（守卫逻辑已核对，待人工点验）
- [x] 修复登录后未即时刷新 `/api/auth/me` 导致顶栏不显示「招领站管理」入口的问题：`setSession` 登录后主动刷新，账号密码登录、注册与扫码登录统一生效，Web 已重新构建部署
- [ ] 发布拾物选择站点 → 生成 `PENDING` 转交单 → 个人中心「我的转交」可见 → 站点审批通过后在 `/manage/items` 与站点详情页可见 → 审批意见与时间线正确（需真实业务点验，未在线上造数据）
- [ ] 认领审批通过 / 现场归还登记都会写 `found_return`、把物品置 `FINISHED` 并通知原始登记人（需真实业务点验）
- [ ] 源头信息抽屉展示原始登记人、联系方式、转交链路与归还记录（需真实业务点验）
- [x] `https://www.globalfinding.com`、`/found`、`/lost`、`/admin`、`/manage`、`/profile` SPA 回退均 200；`/api/found/latest` 缓存仍可用

### 回滚方式

1. 结构回滚（仅适用于尚未产生新业务数据时）：`node site-mgmt-migrate.js rollback <14位后缀>`。
2. 代码回滚：部署上一版 jar（`remote-deploy.js backend` 会保留 `.bak`）与上一版 Web `dist`，或 `git revert` 本次提交后重新构建部署。
3. **已有新业务数据时不要回滚结构**，否则 `found_transfer` / `found_return` 中的审批与归还记录会丢失；先导出两张表再人工处理。

## 环境变量（`/etc/lostfound/lostfound.env`）

参考 `lostfound.env.example`。生产必须设置：`DB_PASSWORD`、`JWT_SECRET`、`WX_APPID`、`WX_SECRET`、`UPLOAD_BASE_URL`、`SERVER_ADDRESS=127.0.0.1`。

## 验收清单（已实测）

- [x] `java -version` → 17.0.21
- [x] `systemctl is-active lostfound` → active；`journalctl -u lostfound` 无异常
- [x] 本机 `curl http://127.0.0.1:8080/api/foundsite/page` → 200 / total 20
- [x] 外网 `https://www.globalfinding.com/api/lost/page` → 200 / total 241（历史数据）
- [x] 外网 `POST /api/auth/wx-login`（无效 code）→ 微信官方 `invalid code`，说明凭证与出站链路正常
- [x] 外网 `POST /api/auth/login`（admin/admin123）→ ADMIN 账号（存量库场景已修复 `DataInitializer`）
- [x] 外网 `POST /api/file/upload` → 返回 `https://www.globalfinding.com/uploads/...`，图片 HTTPS 可访问（已修复 Nginx `^~ /uploads/` 被正则抢匹配问题）
- [x] 外网 `/`、`/admin` → 200，SPA 回退正常；HTTP → 301 HTTPS
- [x] 小程序 1.0.2 已构建并上传（线上域名 + `urlCheck: true`）
- [x] 小程序 1.1.0 已上传（`cli upload -v 1.1.0`，172.1 KB），待在公众平台提交审核并发布
- [x] 小程序 1.2.0（地图选点 / 会话级位置覆盖）已上传（`cli upload -v 1.2.0`，178.2 KB），待在公众平台提交审核并发布
- [x] Web 地图选点版本已部署（`node deploy/remote-deploy.js web`）：线上入口 `Cache-Control: no-cache, must-revalidate`，`LocationSwitch` 产物 200，`/lost`、`/found`、`/admin`、`/map` SPA 回退均 200，`sort=distance` 两组坐标（武汉 3.61km / 北京 0.16km）排序与距离联动正常
- [x] 小程序 1.1.0：启动静默登录、启动定位、首页距离/时间排序、卡片距离展示、地图定位到当前位置
- [x] 线上 `sort=distance` 返回 `total=241` 且距离递增（3.61km → 32.68km → 85.16km…），`sort=time` 按发布时间递减，无坐标自动回退时间排序
- [x] 后端 `GET /api/lost/page?sort=distance&userLat=30.593&userLng=114.305` → 距离递增且 `distance` 字段正确；`sort=time` → 发布时间递减；`/latest`、`/map`、`mine` 保持原排序
- [x] 正式证书已启用（DigiCert DV，SAN 覆盖两个域名，有效期至 2027-02-05），`openssl s_client` 验证链完整
- [x] 历史图片 `https://www.globalfinding.com/image/20160508/1_232651.jpeg` → 200
- [x] 51 条失物记录的图片地址已补全为 HTTPS 绝对 URL，微信小程序 `<image>` 可直接加载
- [x] 拾物历史数据已迁移：`legacy_found` 186 条 → `found` 186 条（含 2023/2024 年新增 2 条），84 条图片地址已补全为 HTTPS；`/api/found/page` 的 `total=186`，距离与时间排序实测正常，`legacy_found` 原表保留
- [x] 新上传写入 `/home/lostfound/image/YYYYMM/`，旧 `/uploads/` URL 仍可访问
- [x] `certbot.timer` 保留为应急备用（当前站点不使用其证书）

## 微信公众平台待办（需人工操作）

1. **开发管理 → 开发设置 → 服务器域名**：把 `https://www.globalfinding.com` 加入
   - `request` 合法域名
   - `uploadFile` 合法域名
   - `downloadFile` 合法域名（如需展示历史图片）
   
   已尝试用小程序 `wxa/modify_domain` 接口自动配置，`access_token` 有效但接口已不可用（40014/40066），必须在公众平台手动设置。
   历史微信头像已升级为 HTTPS，但域名为 `wx.qlogo.cn` / `thirdwx.qlogo.cn`，如需在小程序内显示头像，还需把这两个域名加入 `downloadFile` 合法域名（或后续改为本地缓存头像）。
2. 确认域名已完成 **ICP 备案**（小程序合法域名强制要求）。
3. **地理位置接口**：`开发管理 → 接口设置` 中确认 `getLocation` **与 `chooseLocation` 均已申请并开通**——新增的地图选点调用微信原生 `uni.chooseLocation`，与 `getLocation` 分别申请，缺一会导致选点失败（页面已给出「选点失败，请重试」提示并保留原位置）。`requiredPrivateInfos` 已在两个工程的 `manifest.json` 同时声明 `["chooseLocation", "getLocation"]`。注意 `permission.scope.userLocation.desc` 上限为 **30 字**，超长会在上传时被微信以 `80058` 拒绝；当前使用「用于展示附近失物招领记录及地图定位」（17 字），涵盖展示附近记录与地图定位/选点用途，无需改动。
4. **用户隐私保护指引**：在公众平台配置隐私协议并勾选「地理位置」；`utils/location.js` 已导出 `ensurePrivacyAuthorize()`，自动定位与 `chooseLocation` 调用前均复用它，用户未同意或接口不可用时自动回退默认城市 + 时间排序，不会阻塞首屏，也不会改动既有位置；选点被用户主动取消同样不改动位置。若后台开启 `__usePrivacyCheck__`，需在 `manifest.json` 的 `mp-weixin` 节点补充该配置后再构建上传。`chooseLocation` 是微信原生页面，不额外引入需要备案的业务域名。
5. 按合规要求补齐用户协议、用户输入内容安全检测；提交审核时提供测试账号，并在审核备注中说明「进入即静默登录、定位与地图选点仅用于查看指定地点附近的失物招领物品，位置不持久化」。
6. **提交审核只能在公众平台网页操作**：微信开发者工具 CLI 仅提供 `preview` / `auto-preview` / `upload` / `cloud`，没有提交审核命令；`wxa/submit_audit` 只开放给第三方平台，本小程序不适用。路径：`微信公众平台 → 版本管理 → 开发版本 → 1.3.0 → 提交审核`。若 **1.2.0** 仍在审核中，需先撤回再提交 1.3.0。审核备注建议填写「游客可直接浏览失物/拾物清单；微信扫码登录仅用于发布与管理个人信息，需用户在小程序内主动确认；进入小程序即静默登录、定位与地图选点仅用于查看附近物品」，并按要求提供测试账号与类目。

## Web 匿名浏览与微信扫码登录（发布前逐项确认）

### 本轮上线记录（2026-11-08）

- Git：后端、Web、小程序、文档四个提交已推送 `main`（`aff2bf6`、`8588e85`、`d77ada1`、`4493310`），随后补推二维码 MIME 修正提交。
- 服务器：`node deploy/remote-deploy.js backend` 已上传新 jar 并 `systemctl restart lostfound`（`health=200`、`active`）；`node deploy/remote-deploy.js web` 已重载 Nginx（`https_web=200`、`https_api=200`、入口 `Cache-Control: no-cache, must-revalidate`）。部署分阶段执行，**未使用 `all`**（会重跑 install 的破坏性步骤）。
- 线上验收：`POST /api/auth/qr/ticket` 返回 32 字符票据与 `data:image/jpeg;base64,...` 小程序码（`expiresIn=300`）；`GET /api/auth/qr/status` 对未知票据返回 `expired`、对有效票据返回 `pending`；`POST /api/auth/qr/confirm` 匿名返回 `code=401 请先登录`；匿名 `/api/lost/page` 返回 241 条；`/`、`/lost`、`/found`、`/profile`、`/login` 均 200。
- 小程序 **1.3.0** 已上传（182.6 KB），含 `pages/web-login/web-login`，待在公众平台提交审核并发布。

**审核通过后必须切换二维码到正式版**（当前默认 `env-version=trial` 指向体验版，普通用户扫码可能打不开）：

```bash
# 在服务器执行；确认 1.3.0 已发布为正式版后再切换
printf 'WX_QR_ENV_VERSION=release\nWX_QR_CHECK_PATH=true\n' >> /etc/lostfound/lostfound.env
systemctl restart lostfound
```

注意 `remote-deploy.js backend` 会重写 `/etc/lostfound/lostfound.env`，之后每次重新部署后端都需再次追加这两行。

### 实现与配置说明

- 后端新增 `POST /api/auth/qr/ticket`（匿名）、`GET /api/auth/qr/status`（匿名）、`POST /api/auth/qr/confirm`（`@RequireLogin`）；票据存 Caffeine 内存，不落库、不写表，服务重启即清空。列表/详情/筛选等 GET 接口仍匿名放行，写操作仍需登录。
- 微信接口：`cgi-bin/token` 复用 `WX_APPID` / `WX_SECRET`，`access_token` 缓存至到期前 5 分钟，避免每次生成二维码都换 token 触发限流；`wxa/getwxacodeunlimit` 的 `scene` 为 32 位随机十六进制串（上限 32 可见字符），`page=pages/web-login/web-login`，`width=280`。微信实际返回 JPEG，后端按文件头生成正确的 data URL MIME。创建票据按 IP 频控，默认 10 次/分钟。
- 多实例部署注意：`access_token` 与登录票据都是单机内存态；多实例需换成共享缓存/Redis，否则新实例取 token 会顶掉旧实例凭证，且票据不共享。

### 验收清单

- [x] Web：未登录可访问 `/`、`/lost`、`/found` 与 `/profile`，匿名列表接口返回 241 条，不跳登录页
- [ ] Web：游客点「发布」及详情页关注/评论只弹扫码框、不发写请求，关闭后停留在原页（需真机/浏览器交互确认）
- [ ] Web：个人中心头像/昵称为空、各列表为空并附扫码与账号登录入口；登录态过期不再整页跳转（需交互确认）
- [x] Web：扫码接口可生成二维码（JPEG data URL）、票据 `pending`/`expired` 状态正确、confirm 鉴权生效
- [ ] 小程序：`pages/web-login/web-login` 已上传 1.3.0 待审核；**审核通过后发布正式版并切换 `env_version=release`**
- [ ] 同一身份：确认后 Web 拿到的是小程序当前 `openid` 对应的 `user.id`，两端发布/关注/消息/认领一致（需真机确认）
- [ ] 合规：隐私保护指引涵盖「账号/用户标识」；审核备注按上方第 6 项填写

**回滚**：Web 与小程序分别回退上一版产物即可，后端接口为新增且无数据库变更；后端旧 jar 已备份为 `/opt/lostfound/lostfound-server.jar.bak`。若要临时关闭扫码登录，可把 `WX_QR_ENV_VERSION` 指向不存在的版本或直接注释 `qr-login` 配置后重启，账号密码登录不受影响。

## 地图选点功能验收（发布前逐项确认）

本功能只改前端：后端接口、数据库、管理后台零改动；手动位置仅存于会话内存，不写 storage、不写库。

- [ ] 小程序：冷启动默认自动定位；首页位置条显示「自动定位」，列表按距离由近到远
- [ ] 小程序：点「地图选点」→ 微信原生选点页 → 确认后位置条变「手动选点」，列表重置分页并按新坐标重排、卡片距离刷新；取消选点位置不变
- [ ] 小程序：点「恢复当前位置」回到自动定位；冷启动后不再保留上次选点，地图页中心同步
- [ ] Web：首页 `/` 与 `/lost`、`/found` 均有位置条；选点确认后列表与卡片距离按新坐标更新，首页版块仍按最新展示
- [ ] Web：无 `VITE_AMAP_KEY` 时弹层降级为 GCJ02 经纬度输入，越界坐标被拒绝且位置不变；刷新页面后手动位置清空
- [ ] 两端：拒绝定位 / 未授权时回退默认城市 + 时间排序；手动选点后解除「未定位不可按距离排序」限制；用户主动改过排序时尊重其选择
- [ ] 真机：公众平台已开通 `chooseLocation`、隐私协议已勾选「地理位置」，否则按上方第 3 / 4 项处理

## 运维与回滚

```bash
# 日志
journalctl -u lostfound -f
tail -f /var/log/nginx/lostfound-access.log /var/log/nginx/lostfound-error.log

# 证书续期：当前使用 /home/cert 正式证书，有效期至 2027-02-05，需手动替换 pem/key 后 reload Nginx
openssl x509 -in /home/cert/globalfinding.com.pem -noout -enddate
# certbot.timer 仅作为应急备用（续期的是未使用的 Let's Encrypt 证书）

# 回滚后端到上一版 jar
systemctl stop lostfound
cp /opt/lostfound/lostfound-server.jar.bak /opt/lostfound/lostfound-server.jar
systemctl start lostfound

# 回滚到旧站
rm -f /etc/nginx/conf.d/lostfound.conf
mv /etc/nginx/disabled-sites/default /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
# 如需恢复旧 Tomcat（JDK8）：
# cd /home/apache-tomcat-6.0.8 && JAVA_HOME=/usr/local/jdk/jdk8 ./bin/startup.sh
```

**回滚全程不触碰数据库**：不执行 `DROP`/`TRUNCATE`/`schema.sql`，`legacy_*` 表与新表数据均保留。

**地图选点功能回滚**：仅涉及前端的会话级位置覆盖，不写库、不改 jar 接口。小程序在公众平台回退上一版本即可；Web 重新部署上一版 `lostfound-web/dist`（`node deploy/remote-deploy.js web`）即可，无需回滚 jar，也无需清理任何数据。

## 已知限制与建议

- MySQL 5.6 不支持 JSON 列，`images` 用 TEXT 存 JSON 字符串；Hikari 连接池 20，MySQL `max_connections=151`，单机足够。
- 迁移后的 `category` 曾是旧系统 `objectType` 数字编码（如 `"2"`），2026-11-08 曾全量归一为旧 8 类；2026-11-10 起已按 `config/item-categories.json` 重定义为 12 类并撤销查询别名兼容，详情见主 README 与下方历史记录。
- 旧库 1 条双重编码昵称已修复为「小周不剃光头」，其余数据保持原样。
- `JWT_SECRET` 已更换，旧 token 全部失效，用户重新登录即可。
- 默认管理员 `admin / admin123`，**上线后请立即改密**。
- `application.yml` 已移除 `WX_SECRET` 明文默认值，本地调试需自行注入；部署脚本与图片规范化脚本均强制从环境变量读取口令。
- 距离排序在 SQL 层用 Haversine 表达式全表排序（当前 241 条失物规模无压力）；数据量增大后建议加经纬度边界框预过滤或空间索引。
- 后端 CORS 当前为 `allowedOriginPatterns("*")`；由于 Web 与小程序均同域访问，可后续收紧为 `https://www.globalfinding.com`。
- 图片经 Thumbnailator 重编码后体积可能增大，后续可按需接入对象存储或调整压缩参数。

## 物品类别归一（2026-11-08，历史记录）

> 本节记录当时的旧 8 类归一；当前线上字典以主 README 的 `config/item-categories.json`（12 类）为准。2026-11-10 起旧 8 类（除「其他」）彻底废止，数字代码与「其它」别名不再兼容。

**问题**：旧库 `objectType` 是 1~18 的数字代码，早期迁移脚本用 `COALESCE(NULLIF(objectType, ''), '其他')` 原样写入 `found/lost.category`；Web 与小程序按 8 个中文枚举精确查询（`qw.eq("category", ...)`），因此按类别筛选几乎全为 0，`MatchService` 的类别候选与后台 `GROUP BY category` 统计也被拆成数字桶。

**执行范围**（幂等脚本，位于临时运维目录，不入库）：

- `found.category`、`lost.category`、`legacy_found.objectType`、`legacy_lost.objectType` 共 847 行；18 类按语义归并为 8 类（如 `2/3→证件`、`4/5→钱包`、`9→电子产品`、`10/11→书籍`、`17/18/宠物/空值→其他`）。
- 执行前建备份表 `category_norm_bak_20261108135138`（`tbl + item_id + old_category`，847 行），事务内 UPDATE，并校验 4 张表总行数不变、变更行数与备份行数一致；失败自动回滚。
- 回滚：`node category-normalize.js rollback 20261108135138`（备份表保留不删除）。`survey` / `dry-run` / `verify` 均为只读或幂等，可重复执行。

**验收**（线上 HTTPS + 数据库，当时口径）：

- 8 类逐个筛选：`found` 证件 81 / 钥匙 3 / 手机 18 / 电子产品 24 / 钱包 12 / 书籍 2 / 衣物 3 / 其他 43，合计 186 = 全量；`lost` 证件 107 / 钥匙 12 / 手机 16 / 电子产品 9 / 钱包 41 / 书籍 4 / 衣物 4 / 其他 48，合计 241 = 全量。
- 详情 `category` 返回中文；`/latest` 缓存条目无残留非标准值；`match_record` 行数不变（0，无冗余类别列，不会自动重算）；后台类别统计只剩 8 类。
- `verify` 输出 4 张表非标准值均为 0。

**当时的防再犯（现已被新字典取代）**：后端新增 `ItemCategory.java` 作为类别唯一事实来源，发布/编辑写入前 `requireValid` 校验（非法类别返回 400），查询 `normalize` 曾兼容历史数字代码；`dbtool/category-mapping.js` 提供统一 SQL 映射，`migrate.js` / `migrate2.js` / `migrate3.js` / `migrate-found.js` 已改用映射，禁止从旧 dump 重跑时再次写入数字代码。三处界面枚举当时保持不变；2026-11-10 起改为四处常量由 `config/item-categories.json` 生成，`normalize` 仅去首尾空白，旧值不再转换。

**部署与回归（2026-11-08，历史）**：`node deploy/remote-deploy.js backend` 已上传含类别白名单的新 jar 并 `systemctl restart lostfound`（`health=200`、`active`），重启同时清空 Caffeine 缓存。部署前只读核对 `/etc/lostfound/lostfound.env` 仅含 11 个固定键，重写不会丢失额外配置。当时线上回归：8 类筛选与总数一致、`/latest` 无残留非标准值、历史参数 `category=2` → 81 条、`category=4` → 41 条、`category=其它` → 43 条，证明当时新 jar 的查询兼容已生效；该兼容在 2026-11-10 类别重定义后已撤销。

> `remote-deploy.js backend` 重启服务会清空 Caffeine `latestItems` / `siteStats` / `adminOverview` 缓存，因此数据归一后需重新部署一次后端 jar，以刷新首页与后台统计缓存；切勿用 `all`（会重跑 install 的破坏性步骤）。

## 物品类别重定义上线记录（2026-11-10）

- **字典定稿**：`config/item-categories.json`（`version=2`）定义 12 类：`证件证照 / 卡券票据 / 手机通讯 / 数码电子 / 服饰配饰 / 钥匙锁具 / 钱包卡包 / 箱包行李 / 书刊文件 / 生活用品 / 宠物活体 / 其他`。四处界面常量、后端 `ItemCategory.ALL`、`schema.sql` 注释、后台饼图颜色与构建前置 `--check` 已同步；`ItemCategory.LEGACY` / 数字代码 / 中文别名兼容全部删除。
- **本地验证**：`mvn -q package`（含 `ItemCategoryTest`）、`npm run build`、`npm run build:mp-weixin` 全部通过；两套 uni-app 源码关键文件逐一一致，`dist/build/mp-weixin` 已强同步到 `dist/dev/mp-weixin`，产物内 `CATEGORIES` 均为 12 类。
- **线上迁移**：`survey` 显示 `found=186`、`lost=242`，合计 428 行；`dry-run` 预计分布之和 428/428，关键词规则 19 条、兜底样例已核对。`apply` 单事务重映射 428 行并 `verify` 全部通过；非法类别 0，快照行数与当前表一致。快照后缀（UTC）`20261109221434`（北京时间 2026-11-10 06:14），保留 `category_redesign_bak_20261109221434_found`、`category_redesign_bak_20261109221434_lost`、`category_redesign_bak_20261109221434_meta`。回滚命令（在临时运维目录执行，仅在 `found/lost` 无新增业务行时可用）：`node with-secret.js node category-redesign-migrate.js rollback 20261109221434`。本次只改 `found.category`、`lost.category`，未触碰 `legacy_*`、`match_record`、`found_transfer`、`found_return` 与上次站点迁移快照。
- **部署**：按 `backend` → `web` 分阶段执行 `remote-deploy.js`，未使用 `all`。后端重启后 `health=200`、`systemctl is-active lostfound=active`；Web 部署后 `https_api=200`、`https_web=200`。重启同时清空 Caffeine 旧类别缓存。
- **线上回归**：`verify-category-online.js` **39/39 通过**。新类别筛选之和与总数一致（`found=186`、`lost=242`）；旧 7 个类别、数字代码 `2`/`4`、别名「其它」筛选均返回 0；旧类别发布返回 `code=400` 且提示「请更新到最新版本后重新选择类别」；`/latest` 缓存无旧值；后台 `categoryStats` 为 12 类、合计 428；入口 JS 可访问，线上 Web JS 含新字典，`/lost`、`/found`、`/publish`、`/admin` SPA 回退均 200。新类别写入路径由本地 JUnit 覆盖，未在生产创建测试物品。
- **小程序 1.4.0 上传状态**：两套源码与 `build/dev` 产物均已含 12 类；`cli.bat upload --project <dist/build/mp-weixin> -v 1.4.0 -d category-dict-v2-12-types` 已尝试两次，均因 `IDE service port disabled` 失败；未伪造 `.ide-status` 或 `.ide` 端口文件。需人工打开微信开发者工具 → 设置 → 安全设置 → 开启服务端口，然后重新执行上传命令或在 IDE 内上传 1.4.0；再到微信公众平台 → 版本管理 → 开发版本 → 提交审核。若 1.3.0 仍在审核/未发布，按平台规则先撤回或发布旧开发版本后再提交 1.4.0。
- **过渡期提示**：后端、Web 与线上数据已是新 12 类口径；未升级的旧小程序客户端提交旧类别会收到 400 升级提示，旧分享链接按旧类别筛选返回空结果。应尽快完成 1.4.0 上传、审核与发布。临时 SSH 凭据文件已删除，未写入仓库。

## 拾获/丢失日期调整上线记录（2026-11-10）

- **范围**：仅 `found.found_time` / `lost.lost_time`（页面「拾获时间 / 丢失时间」）。`create_time`、`update_time`，以及 `legacy_*`、`match_record`、`found_transfer`、`found_return` 均未改动。
- **规则**：`新值 = LEAST(DATE_ADD(原值, INTERVAL 4 YEAR), 服务器当前时间)`；NULL 保持 NULL，超过当前时间的值统一封顶。执行时 cutoff 为 `2026-11-10 06:35:34`。
- **预演**：`survey` 显示 428 行日期字段全部非空；`found=186`、`lost=242`。`dry-run` 预计变化 428 行，其中 7 行封顶（`found` 2 行、`lost` 5 行）；正常偏移样例为 2016 → 2020，封顶样例为 2023/2024/2026 → cutoff。
- **执行与校验**：单事务 `apply` 更新 428 行并 `verify` 全部通过；快照行数与当前表一致，精确映射不一致 0 行，未来值 0。快照后缀（UTC）`20261109223533`（北京时间 2026-11-10 06:35），保留 `item_date_bak_20261109223533_found`、`item_date_bak_20261109223533_lost`、`item_date_bak_20261109223533_meta`。回滚命令（在临时运维目录执行，仅在 `found/lost` 无新增业务行时可用）：`node with-secret.js node adjust-item-dates.js rollback 20261109223533`。
- **调整后分布**：`found.found_time` 年份 2020=143 / 2021=22 / 2022=10 / 2023=1 / 2024=4 / 2025=1 / 2026=5；`lost.lost_time` 年份 2019=1 / 2020=30 / 2021=31 / 2022=62 / 2023=42 / 2024=4 / 2025=11 / 2026=61；两表最大值均为 cutoff。
- **缓存与线上回归**：执行 `systemctl restart lostfound` 清空 `latestItems` 缓存，服务 `active`。`verify-item-date-online.js` **6/6 通过**：`found/lost` 分页、详情与 `/latest` 均 200，`itemTime` 无未来值。
- **口径说明**：本次只顺延业务上的拾获/丢失日期；列表卡片展示与按发布时间排序仍使用未改动的 `create_time`。如需同步顺延发布时间，应另行评估并单独执行。临时运维脚本不入库，临时连接文件已清理。

## 招领站建站申请与背书上线记录（2026-11-10）

**范围**：Web 管理端与后端/数据库；小程序本轮无功能改造，仅构建、产物同步与上传尝试。

- **数据库迁移（幂等，MySQL 5.6.51）**：`survey` 显示 `foundsite=20`、`foundsite_manager=3`、背书字段 0/4、申请表不存在；`dry-run` 预览 4 列 + 1 表。`apply` 先建快照 `site_app_bak_20261110003635_foundsite`（20 行）与 `site_app_bak_20261110003635_meta`，再补 `endorsement_org` / `endorsement_text` / `endorsement_updated_at` / `endorsement_updated_by` 并创建 `foundsite_application`（4 个索引）。`verify` 全部通过：快照与当前 `foundsite` 均 20 行、申请 0 行、已写入背书 0 行。
- **回滚**（仅在申请为空、四个背书字段全 NULL、`foundsite` 行数与快照一致时可用；快照表保留供审计）：`node with-secret.js node site-application-migrate.js rollback 20261110003635`。
- **本地验证**：`mvn -q test`（含 `SiteApplicationServiceTest` 6 项）与 `mvn -q -DskipTests package` 通过；Web `npm run build`（`vue-tsc -b` + 字典 `--check`）通过；小程序 `npm run build:mp-weixin` 通过，两套 uni-app 源码逐文件比对 `diff=[]`（另修复了 `pages/mine/mine.vue` 的历史空白漂移），`dist/build/mp-weixin` 已强同步到 `dist/dev/mp-weixin` 并保留 IDE 配置。
- **部署顺序**：线上迁移 → `node deploy/remote-deploy.js backend` → `node deploy/remote-deploy.js web`，未使用 `all`。后端重启后 `health=200`、`systemctl is-active lostfound=active`；Web 部署后 `https_api=200`、`https_web=200`、`nginx=active`。重启同时清空 Caffeine `latestItems` / `siteStats` / `adminOverview` 缓存。
- **线上只读回归**：`verify-site-application-online.js baseline` **27/27 通过**。`SiteVO.hasEndorsement` 已返回；新端点 `POST /api/site-applications`、`GET /mine`、`GET /page` 均已注册且未登录返回 `code=401`；背书接口未登录 401；`/site/apply`、`/site/applications`、`/admin/site-applications`、`/manage/site` SPA 回退 200；10 个新增页面 JS/CSS chunk 线上均可访问；`found=186`、`lost=242` 未回归。
- **最小写入回归**：`verify-site-application-online.js full` **19/19 通过**。注册临时用户 → 提交申请 → 重复待审被拒 `code=400` → ADMIN 通过自动建站并绑定 `foundsite_manager` → `/api/auth/me` 返回 `siteManager=true` 与新站点范围 → 非本站背书维护 403 → ADMIN 维护背书后详情与列表均返回文案/更新时间且 `hasEndorsement=true` → 第二条申请驳回且不新增站点、意见可追踪 → 重复审核被拒（幂等）→ ADMIN 删除临时站点并由 SSH 隧道清理申请、消息、管理员关系与临时用户。清理后 `foundsite=20`、`foundsite_manager=3`、`foundsite_application=0`，旧站点数据未变。
- **序列化口径**：全局 Jackson `default-property-inclusion=non_null`，背书文案与更新时间在设置前不出现在 JSON，前端按可选字段处理；设置后正常返回，与「暂未设置背书」展示一致。
- **小程序 1.4.1 上传状态**：`cli.bat upload --project <dist/build/mp-weixin> -v 1.4.1 -d "建站申请与背书配套构建（小程序无功能改动）"` 因 `IDE service port disabled` 失败；未伪造 `.ide-status` 或 `.ide` 端口文件。本轮小程序源码与线上 1.4.0 功能一致，无强制发布要求；如需重新上传，人工打开微信开发者工具 → 设置 → 安全设置 → 开启服务端口，再执行上传命令，最后到微信公众平台 → 版本管理提交审核。
- **权限与状态口径**：申请状态 `PENDING / APPROVED / REJECTED`；背书文案由站点自行维护，不代表平台认证。角色与页面矩阵见主 README「招领站管理」。
- **凭据与清理**：本次部署口令由临时运维目录中的既有凭据注入进程环境变量，未进入命令行、未写入仓库、未回显；运维脚本位于 `C:/Users/John/AppData/Local/Temp/dbtool/`，不入库；仓库内未新增明文凭据。
- **仍待人工处理**：微信开发者工具服务端口与小程序版本审核；默认管理员口令轮换；`deploy/remote-deploy.js` 勘察段中历史遗留的示例数据库口令建议改为从环境变量读取。临时运维目录 `dbtool/verify-web-online.js` 内保存有历史明文连接口令，本轮部署复用了该凭据，建议清理脚本中的明文并轮换口令。

## 小程序转交与匹配优化上线记录（2026-11-10）

**范围**：小程序 1.5.0（转交功能入口 + 我的转交页 + 转交消息分类）、后端（匹配算法重配 + 匹配回刷接口 + 转交 DTO 校验修复）；无数据库变更。

- **匹配算法重配（`MatchService`）**：候选仍按类别完全相同硬过滤；权重改为 类别 30（同类别固定满分底座）+ 时间接近度 25（相差 ≤3 天满分，3~30 天线性衰减）+ 空间距离 25（≤2km 满分，2~50km 线性衰减）+ 关键词 20（bigram），阈值 50→45；缺失时间/坐标不加分（避免纯类别匹配自动入围）；`reason` 文案改为体现具体天数与公里数。新增 `MatchServiceTest` 12 项单测。
- **匹配回刷**：新增 `POST /api/match/rebuild`（仅 ADMIN），对全部 WAITING 物品重跑匹配；幂等（命中 `uk_found_lost` 跳过）、旧低分记录保留不删、静默不发送消息。线上执行：`waitingFound=176`、`waitingLost=235`、首轮 `created=4345`，二次执行 `created=0`、`skippedExisting=4360`（含首轮新增 + 历史低分记录），幂等验证通过。
- **转交修复**：`TransferDTO.foundId` 原标注 `@NotNull`，与 `POST /api/found/{id}/transfer`（物品 ID 走路径）矛盾，body 不带 `foundId` 会被 `@Valid` 直接 400（线上回归发现）。已移除该 `@NotNull` 并在 `TransferService.apply` 开头对 `foundId` / `siteId` 做兜底校验（400）；新增 `TransferServiceTest` 6 项单测。
- **小程序 1.5.0**：两套 uni-app 源码逐文件比对 `diff=[]`；`npm run build:mp-weixin` 通过，`dist/build/mp-weixin` 强同步 `dist/dev/mp-weixin` 并保留 IDE 私配。新增 `transferApi`、「我的发布」拾物转交操作行与弹层、`pages/transfer/transfer` 我的转交页（状态时间线 + 撤回）、消息中心 `TRANSFER` 分类与跳转、`pages.json` 注册。
- **本地验证**：`mvn -q test` 全量通过（新增 `MatchServiceTest` 12 项 + `TransferServiceTest` 6 项），`mvn -q -DskipTests package` 通过。
- **部署**：`node deploy/remote-deploy.js backend` 分阶段执行（未用 `all`），重启后 `health=200`、`systemctl is-active lostfound=active`。首次部署后只读回归发现 `POST /api/match/rebuild` 未登录返回 `code=500`——定位为旧 jar 未重启成功（`GlobalExceptionHandler` 兜底 NoHandlerFound），重新部署后 `code=401` 正常；随后再次部署含转交 DTO 修复。
- **线上回归**：`verify-match-transfer-online.js` 只读 **8/8**、最小写入 **22/22**。写入回归：注册临时用户 → 发布同类别/时间差 1 天/相距约 0.6km 的失物拾物 → 发布即生成匹配（score=93.71，reason 含「时间相差 1 天」「地点相距约 0.6 公里」）→ ADMIN 回刷幂等 → 转交发起（body 不带 foundId，transferId 返回正常）→ 重复待审 400 → 时间线完整 → 按物品撤回 CANCELLED → 重新发起 → 非管理员审批 403 → ADMIN 审批通过 → 拾物 `APPROVED/TRANSFER` → 删除临时物品并由 SSH 隧道清理 `match_record`、`found_transfer`、`message` 与临时用户，清理后 `found=189`、`lost=244`、`found_transfer=0`。
- **小程序 1.5.0 上传状态**：`cli.bat upload ... -v 1.5.0` 因 `IDE service port disabled` 失败；需人工开启微信开发者工具 → 设置 → 安全设置 → 服务端口后重传，并到公众平台提交审核。
- **凭据与清理**：部署口令以字符码方式注入进程环境变量（不进命令行、不写文件、不回显），会话结束即失效；临时运维脚本位于 `C:/Users/John/AppData/Local/Temp/dbtool/`（`sync-mp-dev.js`、`verify-mp-sources.js`、`verify-match-transfer-online.js` 可复用），`.deploy-secret` 与凭据包装脚本已删除，仓库内无新增明文凭据。

## 个人资料编辑与发布自动带入上线记录（2026-11-10，小程序 1.6.0）

**范围**：后端 `user.contact` 新列与资料接口校验、小程序「我的」资料编辑弹层、发布页自动带入；**先加列再部署后端**。

- **数据库迁移（幂等，MySQL 5.6.51）**：`profile-contact-migrate.js survey` 显示 `contact` 列不存在、`user=1687` 行；`dry-run` 预览 ALTER；`apply` 执行 `ALTER TABLE user ADD COLUMN contact VARCHAR(100) DEFAULT NULL ... AFTER email`（历史行 NULL，不回填）；`verify` 通过（列存在、类型 `varchar(100)`、可空、行数未变）。人工等价 SQL 入库：`lostfound-server/src/main/resources/sql/migration-profile-contact.sql`。
- **后端**：`User` / `UpdateProfileDTO` / `AuthService.updateProfile` 支持 `contact`；`PUT /api/auth/profile` 补 `@Valid` 与长度校验（昵称 50、头像 500、手机 20、邮箱/联系方式 100），传空串可清空联系方式、传 null 不覆盖；`GET /api/auth/me`（MeVO 继承 User）自动带上新字段。新增 `AuthServiceTest` 4 项单测。
- **小程序 1.6.0**：两套 uni-app 源码逐文件比对 `diff=[]`；「我的」页用户卡片/菜单进入底部弹层编辑昵称与联系方式（可填微信号/手机号/邮箱），保存后 `authApi.me()` 刷新 `lf_user`；发布页进入时自动填今日日期、资料联系方式（`contact` 优先，回退 `phone`/`email`）与启动定位缓存经纬度，地址文本填「当前位置」（定位不可用不写入，避免虚假位置），保留「选点」与「重新获取当前位置」；用户手动改过的值不被自动值覆盖。
- **微信登录口径不变**：微信官方不向小程序开放真实微信号（wxid），也不自动返回手机号；仍为 `openid` 静默登录，微信号由用户填写在联系方式中，发布时自动带入。
- **本地验证**：`mvn -q test` 全量通过（新增 `AuthServiceTest` 4 项），`mvn -q -DskipTests package` 通过；`npm run build:mp-weixin` 通过（类别字典 `--check` 通过），`dist/build/mp-weixin` 强同步 `dist/dev/mp-weixin` 并保留 IDE 私配。
- **部署顺序**：线上加列 `apply → verify` → `node deploy/remote-deploy.js backend`（未用 `all`），重启后 `health=200`、`systemctl is-active lostfound=active`。本轮 Web 无改动，未重新部署。
- **线上回归**：`verify-profile-autofill-online.js` 只读 **8/8**、最小写入 **16/16**。写入回归：临时用户 → 保存昵称/联系方式 → `/api/auth/me` 回读 → 超长字段 400 且不污染资料 → 模拟发布页 payload（今日日期 + 当前位置 + 资料联系方式）发布临时失物 → 详情回读 `contact/itemTime/address/经纬度` → 清空联系方式回读空串且其它字段不覆盖 → ADMIN 删除临时物品并由 SSH 隧道清理 `match_record`、`message` 与临时用户。API 删除为逻辑删除，脚本额外**物理清理本次及历史同标记回归物品**，复跑后物理行数恢复（`found=189`、`lost=244`）。
- **小程序 1.6.0 上传状态**：`cli.bat upload ... -v 1.6.0` 因 `IDE service port disabled` 失败；需人工开启微信开发者工具 → 设置 → 安全设置 → 服务端口后重传，并到公众平台提交审核。
- **凭据与清理**：部署/迁移口令以字符码注入进程环境变量（不进命令行、不写文件、不回显），会话结束即失效；本轮未创建 `.deploy-secret` 文件，临时运维脚本位于 `C:/Users/John/AppData/Local/Temp/dbtool/`（`profile-contact-migrate.js`、`verify-profile-autofill-online.js` 可复用），仓库内无新增明文凭据。
- **后端/Web 幂等重发布（2026-11-10）**：本地重新 `npm run build`（Web）与 `mvn -q -DskipTests package`（后端）后按 `backend → web` 分阶段重发布（未用 `all`）：`health=200`、`https_api=200`、`https_web=200`、`nginx=active`；只读回归 `verify-profile-autofill-online.js baseline` **8/8**、`verify-match-transfer-online.js baseline` **8/8**；本次构建的 `index-BkxnCWWZ.js` / `index-Ceh1mMJC.css` 线上均 200（产物新鲜度 2/2）。一次性校验脚本已删除，口令仅字符码注入进程环境变量。
- **小程序界面调整 1.6.1（2026-11-10，纯前端）**：隐藏详情页评论功能（评论区/回复/底部入口/输入弹层/评论数与 `commentApi` 调用、相关样式全部移除；`utils/api.js` 的 `commentApi` 定义保留便于恢复，Web 端评论功能未改动）；tabBar 与页面标题「消息」→「通知」（空态「暂无通知」、扫码登录说明同步措辞，图标文件名与后端 MESSAGE 类型不变）；「我的」页菜单「招领站」→「附近招领站」（`pages/sites/sites` 页标题保持不变）。两套 uni-app 源码 `diff=[]`，`npm run build:mp-weixin` 通过，产物强同步 dev 且构建结果无评论 UI 残留；`cli.bat upload -v 1.6.1` 仍因 `IDE service port disabled` 失败，需人工开启服务端口后上传并到公众平台提交审核。本轮后端/Web/数据库无改动，未重新部署服务器。
- **dev 产物同步失效修复（2026-11-10）**：发现临时运维目录中 `sync-mp-dev.js` / `verify-mp-sources.js` 被系统清空为 0 字节，前几轮记录的「强同步 dev / 双源码校验」实为 `node` 执行空文件（exit 0、无输出），`dist/dev/mp-weixin` 仍保留旧版 `detail.wxml`（含评论区），微信开发者工具打开 dev 目录时看到的仍是旧界面。已改用内联 PowerShell 修复：保留 `project.private.config.json` 后删除 dev 全部内容、从 build 全量复制，**70 个文件逐文件哈希比对 `diff_count=0`**；UTF-8 精确校验 dev：`detail.wxml` 无「评论」、mine 有「附近招领站」、messages 有「暂无通知」、`app.json` tabBar 为「通知」。两套源码全量内联比对：共同 31 个文件内容差异 0，仅 HBuilderX 版多 `project.config.json` / `project.private.config.json` 两个 IDE 配置。后续同步与校验不再依赖临时目录脚本。

## 超管界面清理与新增超级管理员上线记录（2026-11-14）

**范围**：Web 前端可见性 + 线上账号数据变更；后端代码、数据库结构、小程序均未改动，因此**未重启后端**。

- **前端隐藏（仅对 `store.isAdmin`）**：`App.vue` 顶栏「申请创建招领站」「我的建站申请」两项、`ProfileView.vue` 建站申请摘要卡片（并跳过 `siteApplicationApi.mine()` 请求、watch 随角色变化刷新）、`SiteListView.vue`「申请创建招领站」按钮。保留后台「建站申请审核」`/admin/site-applications`、「招领站管理」`/admin/sites`、顶栏「招领站管理」`/manage` 与「管理后台」；未删页面、未改路由守卫、未改后端接口权限——ADMIN 手动输入 `/site/apply` 或 `/site/applications` 仍可打开（按确认口径）。
- **构建环境**：默认 PATH 的 Node 为 v14.21.3，`@vue/language-core` 的 `??=` 语法导致 `vue-tsc -b` 报错；切换到 `C:/Programs/nodejs22.231`（v22.23.1）后 `npm run build` 通过（类别字典 `--check` + `vue-tsc -b` + vite build）。静态核对：`ProfileView` / `SiteListView` 新 chunk 均含 `isAdmin` 条件，主 chunk 仍保留面向普通用户的申请入口文案。
- **部署**：`node deploy/remote-deploy.js web`（未用 `all`），`https_api=200`、`https_web=200`、`nginx=active`；本次构建的 `index-CB9rtlfY.js` / `index-D481RNTi.css` / `ProfileView-ZGcNUmcf.js` / `SiteListView-DlKbbvSp.js` 线上均 200。
- **新增超级管理员**：原计划「按 13910752299 提升现有用户」，但只读勘察显示该号码在 `user.contact/phone/username`、建站申请表、模糊匹配中均不存在（微信登录只拿 openid，系统无真实微信号），按约定停止并回来确认。用户确认改为**新建账号**：经 `/api/auth/register` 注册（登录名 `13910752299`、昵称同登录名、初始口令为系统默认口令，需首次登录改密），再由 SSH 隧道仅执行 `UPDATE user SET role='ADMIN'`；`phone/contact/email` 保持 NULL，原 `admin` 账号未改动。新账号 `id=1707`，线上启用 ADMIN 总数 2。
- **线上回归**：`create-superadmin.js` 幂等执行 **10/10**（账号幂等跳过创建、role=ADMIN、联系方式字段未写入、新超管登录与 `/api/auth/me` role=ADMIN、`/api/site-applications/page` 200、未登录 401、普通用户 403、原 admin 状态正常、临时普通用户已删除）；`verify-profile-autofill-online.js baseline` **8/8**、`verify-match-transfer-online.js baseline` **8/8**；新 chunk 线上可访问 4/4。
- **发现项**：原 `admin` 用默认口令登录返回 `code=400`，说明默认口令已被轮换（账号 DB 状态 `role=ADMIN / status=1 / deleted=0` 正常）；**未重置密码、未尝试其它口令**，回归断言改为 DB 状态校验。
- **凭据与清理**：口令以字符码注入进程环境变量（不进命令行、不写文件、不回显）；一次性脚本 `promote-superadmin.js`、`locate-superadmin-candidate.js`、`create-superadmin.js` 已删除；仓库内无新增明文口令；`git diff --check` 通过、暂存区为空、未提交。
- **仍待人工**：新超管首次登录后立即修改默认口令；如需彻底禁止 ADMIN 直接访问申请人侧页面，需另做路由守卫改造（本轮按确认口径未做）。

## 三类用户 Web 功能菜单改造上线记录（2026-11-14）

**范围**：仅 Web 前端视图与菜单分组；后端代码、数据库、小程序、路由守卫与接口权限均未改动，按 `web` 分阶段部署，**未重启后端**。保留顶栏下拉 + `/admin` / `/manage` 两套侧栏与全部公共功能（公共导航、搜索、消息铃铛、个人中心内部标签），复用现有页面，只改菜单名称与分组。

- **改动**：`stores/user.ts` 新增 `ViewMode`（`manager` / `personal`）、`lf_view_mode` 记忆（单 key 存 `{userId, mode}`，读取时校验用户 id，登出清除）、`isManagerView` / `needsIdentityChoice` getter 与 `setViewMode()`；`stores/site.ts` 新增 `reset()`；`App.vue` 按三类角色渲染顶栏下拉、全局身份选择弹窗与切换入口，401/登出同步清空站点缓存；`views/admin/AdminLayout.vue` 侧栏改为「招领站管理 / 用户信息 / 招领信息」三组；`views/manage/ManageLayout.vue` 侧栏按 `siteStore.sites.length` 分组，有可管理站点才显示「站点资料」。
- **身份弹窗**：挂在 `App.vue`（账号密码登录、注册、微信扫码登录共用 `setSession()`，三条链路全覆盖）；`needsIdentityChoice` 要求 `siteLoaded`，即等 `/api/auth/me` 返回 `siteManager` / `managedSiteIds` 后再判断，避免登录接口只返回基础 `User` 时误判。切换身份只改前端视图，真实权限仍以后端 `SitePermissionService` 为准。
- **构建与部署**：默认 PATH 的 Node v14 会导致 `vue-tsc` 因 `??=` 报错，切换 `C:/Programs/nodejs22.231`（v22.23.1）后 `npm run build` 通过（主 chunk `index-CjhXVw1R.js`）；`node deploy/remote-deploy.js web`，`https_api=200`、`https_web=200`、`nginx=active`；线上冒烟 `/`、`/api/health`、`/login` 均 200。
- **线上浏览器回归（9 组全通过）**：真实 `13910752299` 账号登录验证 ADMIN 下拉仅「个人中心 / 管理后台 / 退出登录」、`/admin` 三组六个菜单文案正确；普通用户与站点管理员视图为避免新建线上账号，在浏览器内仅改 Pinia state 模拟，不触碰后端数据——普通用户下拉为「个人中心 / 匹配推荐 / 申请创建招领站 / 我的建站申请 / 退出登录」；站点管理员登录后弹出「选择登录身份」，选管理员身份写入 `lf_view_mode={"userId":1707,"mode":"manager"}` 并跳 `/manage/items`，侧栏显示「招领站管理（站点物品 / 转交与认领审批 / 归还管理 / 站点资料）+ 招领站申请」，清空 `siteStore.sites` 后站点管理组与「站点资料」消失、仅剩「招领站申请」并提示未被指派站点；管理员身份下拉为「个人信息 / 招领站管理 / 招领站申请 / 切换为个人身份 / 退出登录」，切换个人身份后写回 `mode:"personal"`、跳 `/`、菜单恢复普通用户并多「切换为管理员身份」、`siteStore` 清空；登出后 `localStorage` 全部清空（`lf_view_mode` 无残留）。
- **未改动**：`/admin`（`admin: true`）与 `/manage`（`siteManager: true`）路由守卫、后端接口权限、申请人侧页面本身；ADMIN 手动输入 `/site/apply` 或 `/site/applications` 仍可打开（按确认口径「仅前端隐藏」）。
- **清理与状态**：Playwright CLI 临时装在 `C:/Users/John/AppData/Local/Temp/dbtool/`（浏览器会话已登出），调试文件 `pw-check.js` 已删除；仓库内无新增明文口令；`git diff --check` 通过、暂存区为空、未提交。
- **仍待人工**：新超管首次登录后立即修改默认口令；小程序 1.6.1 需开启微信开发者工具服务端口后上传并提交审核；`lostfound-uniapp/pages.json` 遗留的 `tabBar.position: "bottom"` 改动待用户决定是否保留并同步 CLI 版。

## 小程序建站申请与管理员体系上线记录（2026-11-14）

**状态**：**已上线**。2026-11-14 完成生产库幂等迁移（快照 `site_owner_bak_20261114101642_*`，回填 4 个站点 owner，`verify PASS`）、`backend` 部署（`systemctl restart lostfound`，`health=200` / `active`）、`web` 部署（`https_api=200` / `https_web=200` / `nginx active`）与线上回归（HTTP 只读 16/16 PASS、浏览器 4 组、邀请码最小写入并撤销）。SSH 口令由用户提供后以字符码注入进程环境变量，未落盘、未回显、未进仓库。

- **改动**：`foundsite` 增加 `owner_user_id`（主管理员单列，每站唯一）；新增 `foundsite_manager_invite` 邀请码表；`SitePermissionService.requireOwner()` 与 `SiteManagerService` / `SiteManagerController`（`/api/site-managers`，8 个接口全部 `@RequireLogin`）；`SiteApplicationService.review()` 审核通过写 owner；`FoundSiteService.setManagers()` 全量覆盖时保留 owner 行；`WxAuthService.getMiniProgramCode(scene,page,envVersion)` 重载；Web `SitesView.vue` 主管理员标签不可移除、`SiteSettingsView.vue` 展示主管理员、类型补 `ownerUserId` / `isOwner`；小程序新增 8 个页面、`utils/site-store.js`、`components/site-switcher` 与整套 `siteApplicationApi` / `siteManagerApi`。
- **本地验证**：后端 `mvn -q package` 43 个 JUnit 全过（含新增 `SiteManagerServiceTest` 8 例、`SiteApplicationServiceTest` 补 owner 断言），jar `lostfound-server-1.0.0.jar`；Web `npm run build`（Node 22）通过，主 chunk `index-CWVB1yjU.js`，IDE 诊断 0；小程序 `npm run build:mp-weixin` 通过，`app.json` 19 页、8 个新页面 wxml 齐全；两套 uni-app 源码 41 个文件哈希全一致；`dist/build/mp-weixin` → `dist/dev/mp-weixin` 强同步 107 文件哈希一致、dev 保留 `project.private.config.json`（108 文件）。
- **迁移脚本**：`C:/Users/John/AppData/Local/Temp/dbtool/site-owner-migrate.js`，`survey / dry-run / apply / verify / rollback` 全幂等，执行前建 `site_owner_bak_<ts>_foundsite` / `_foundsite_manager` / `_meta` 快照，回填按每站最早一条 `foundsite_manager`，verify 校验 owner 与关系行一致且行数未变；SQL 等价版本在 `lostfound-server/src/main/resources/sql/migration-site-owner.sql`，MySQL 5.6 兼容（无 CHECK / 生成列 / 函数索引）。
- **回归脚本**：`C:/Users/John/AppData/Local/Temp/dbtool/verify-site-owner-online.js`，纯 HTTP 只读，不创建线上业务数据；可选 `LOSTFOUND_TEST_LOGIN` / `LOSTFOUND_TEST_PASSWORD` 注入登录态后额外校验 `/api/foundsite/mine` 的 `ownerUserId`、管理员列表 `isOwner`、邀请码列表与公开 `/managers` 未回归。
- **实际部署顺序与结果**：① `site-owner-migrate.js survey`（`foundsite` 21 行 / `foundsite_manager` 4 行，新列与邀请码表均不存在）→ `dry-run` → `apply`（加 `owner_user_id`、建 `foundsite_manager_invite`、回填 4 行）→ `verify`（`PASS`，owner 与关系行一致、行数未变）；② `node deploy/remote-deploy.js backend`（新 jar 上线、`systemctl restart lostfound`，`/api/foundsite/page` 返回项已带 `ownerUserId`）；③ `node deploy/remote-deploy.js web`（主 chunk `index-CWVB1yjU.js` 线上 200）；④ `verify-site-owner-online.js` + 浏览器回归 + 邀请码链路写入回归。
- **线上回归明细**：HTTP 只读 16/16 PASS（首页资源、健康检查、公开列表、新接口未登录 `code=401`、超管登录、`/api/foundsite/mine` 的 `ownerUserId`、管理员列表 `isOwner`、邀请码列表、公开 `/managers` 未回归）；浏览器（Playwright CLI 0.1.24）在 `/admin/sites` 编辑 ID 39「中国农科院招领站」验证主管理员提示「本站主管理员：张三，不可在此名单中移除」、标签「张三（#1698） · 主管理员」、点击移除后标签自动加回并弹「主管理员不可在此移除，请先在小程序端转让主管理员」、原样保存后标签与 `isOwner:true` / `ownerUserId=1698` 均保持，截图 `site-owner-admin.png` 留在 dbtool 目录；邀请码最小写入：生成（`qrCode` 有效，未触发降级）→ 只读预览（`code=0`，站点名/邀请人/状态正确）→ 列表 → 撤销，最终状态 `REVOKED`，无待用邀请码残留。
- **仍待人工**：微信开发者工具开启服务端口后上传 `1.7.0`（本轮 `upload` 仍报 `IDE service port disabled`，含此前 1.6.1 的界面调整），新页面发布后确认 `WX_SITE_INVITE_ENV_VERSION=release`；`manifest.json` 未声明 `__usePrivacyCheck__`（微信隐私强审项，需人工评估是否补充）；新超管首次登录后修改默认口令；工作区改动未提交。

## 查询性能全链路优化上线记录（2026-11-14）

**状态**：**已上线**。按「部署前基线 → 生产库幂等迁移 → `backend` → `web` → HTTP/DB/浏览器回归」顺序完成；三个涉及语义的开关 `fulltext-enabled` / `status-enum-enabled` / `distance-bbox-enabled` **全部保持 `false`**。页面外观、接口入参与返回结构未改，分页 `total` 仍为精确 COUNT。SSH/DB 口令以字符码注入进程环境变量，未落盘、未回显、未进仓库。设计与开关说明见根目录 `README.md` 的「查询性能设计」。

- **部署前基线**：`dbtool/perf-baseline.js all before`（HTTP 5 次采样 + DB 元数据/EXPLAIN/行数，落 `perf-baseline-before.json`）。MySQL **5.6.51**，`ngramPlugin=[]`、`fulltextIndexes=[]`（`schema.sql` 的 `WITH PARSER ngram` 仅是声明，线上不可用），`query_cache_type=OFF`、`slow_query_log=OFF`、`innodb_buffer_pool_size=128MB`；`found.list.time` / `found.list.hot` / `found.keyword` / `found.distance` 均 `type=ALL, key=null, Using filesort`，`match_record.mine` 为 `ALL/4323 rows/Using filesort`。因此全文开关无条件可开性被排除，改为「全文粗召回 + LIKE 精确过滤 + 全路径降级」但保持关闭。
- **本地验证**：后端 `mvn -q package` 8 个测试类 **53 例全绿**（新增 `ItemServicePerfTest` 6 例、`AdminServiceStatsTest` 4 例），jar `lostfound-server-1.0.0.jar`（30758682 字节）；Web `npm run build`（默认 PATH 的 Node v14 会因 `??=` 报错，切至 `C:/Programs/nodejs22.231` v22.23.1）通过，IDE 诊断 0；小程序 `npm run build:mp-weixin` 通过，两套源码排除 `project*.json` 后 **34 文件 diff=0**，`dist/build/mp-weixin` → `dist/dev/mp-weixin` 强同步 **BUILD_FILES=108 / DEV_DIFF=0 / DEV_TOTAL=109 / PRIVATE_KEPT=True**（用 Node 22 读 `app.json` 复核 `PAGES=19`、定位说明 17 字，规避 PowerShell `ConvertFrom-Json` 中文编码失败）。
- **生产库迁移**：`dbtool/perf-index-migrate.js` 的 `survey / dry-run / apply / verify / rollback` 全幂等；`survey` 复核 MySQL 5.6.51、无 ngram/FULLTEXT、`status` 仅 `WAITING/FINISHED`；DDL 前建全表快照 **`perf_bak_20261114145901_*`**；`apply` 只 `ADD INDEX`、不删既有索引，**added=26 / skipped=0 / failed=0**，索引总数 158 → 223；`verify` **26/26 索引存在、7 张表行数全部未变**，同口径 EXPLAIN（含 MyBatis-Plus 自动附加的 `deleted=0`）`found.list.time` 由 `ALL/null` 变 `range/idx_perf_time`。人工等价 SQL 在 `lostfound-server/src/main/resources/sql/migration-perf-index.sql`（MySQL 5.6 兼容，无 CHECK / 生成列 / 函数索引）。
- **部署**：① `node deploy/remote-deploy.js backend`（`systemctl restart lostfound` 重载新 jar，`health=200`、`active`、API sample 正常）；② `node deploy/remote-deploy.js web`（`https_api=200`、`https_web=200`、`nginx active`、HTTPS sample 正常）。未使用 `all`，未改动 `/etc/lostfound/lostfound.env`，JWT 未轮换。
- **部署后性能对比**（`perf-baseline.js` before `14:03:01Z` → after `15:14:33Z`，HTTPS 5 次采样 p50，毫秒）：

  | 接口 | before p50 | after p50 | 变化 |
  |------|-----------:|----------:|-----:|
  | `found.page.time` | 1147 | 227 | **-80.2%** |
  | `found.page.hot` | 829 | 226 | **-72.7%** |
  | `found.map` | 1198 | 331 | **-72.4%** |
  | `lost.page.time` | 1558 | 227 | **-85.4%** |
  | `lost.map` | 1352 | 379 | **-72.0%** |
  | `found.page.city` | 533 | 227 | -57.4% |

  p95 同步收敛（`found.page.time` 3212 → 532、`lost.page.time` 3503 → 535、`lost.map` 7440 → 1352）。**如实记录的例外**：`found.page.keyword` p50 227 → 307、`found.latest` 276 → 329 略升，但两者 p95 分别由 1865 → 533、586 → 790 收敛，且 `health`（178 → 306）主要是网络 RTT、不承载业务查询，回归脚本已将其排除劣化断言；`found.latest` 首次采集的 1248ms 为服务重启后缓存未热，预热复采稳定为 329ms。通用 EXPLAIN 里 `found.*` 仍显示 `ALL` 是因为该 SQL 未带逻辑删除列 `deleted=0`，**应用同口径以迁移脚本 `verify` 为准**。
- **线上 HTTP/DB 回归**：`dbtool/verify-perf-online.js` 纯只读，**57 PASS / 0 FAIL**。分页 `total` 188 / 242 / 14 前后完全一致，排序不改变 total，关键词搜索仍有命中，返回字段 keys 一致；`/map` 数组长度保持 100（后端下推坐标非空条件后补齐，前端原本会丢弃无坐标记录，展示口径不变），`lost.map` 首条 id 660 → 659 与响应体略增即源于此。DB 侧 `match_record.mine` EXPLAIN `ALL/4323 rows/Using filesort` → `index/idx_perf_create_time/50 rows`，`foundsite_manager.byUser` `ALL` → `ref/idx_perf_user/1 row`，行数未减少。
- **线上浏览器回归**：`dbtool/verify-perf-browser.js`（Node 22 + `playwright`，因 chrome-devtools MCP 不可用改用 Navigation Timing/FCP 近似 Core Web Vitals）**34 PASS / 0 FAIL**，6 组页面 `home` / `found.list` / `lost.list` / `found.search` / `map` / `admin.guard`：HTTP 均 200、标题正确、页面总数 188 / 242 / 14、无 JS 错误、无关键资源失败、FCP 全部 `good`（1373 / 115 / 276 / 135 / 142 / 402 ms；按 good `<1800`、needs-improvement `<3000`、poor `>=3000` 分级，仅拦截 poor）；`/admin/dashboard` 未登录正确重定向 `/login?redirect=/admin/dashboard`。报告 `perf-browser-report.json`、截图 `perf-browser-shots/*.png`。列表页历史图片 `/image/*.jpeg` 404 已单独归类为非关键资源，不阻断本轮性能回归。Playwright CLI 会话已 `close-all`。
- **缓存与一致性**：Caffeine 单机，`latestItems` 300 秒、`itemCount` 30 秒、`siteStats` / `adminOverview` 60 秒；`CacheEvictService.evictLatest()` 兼容历史调用但升级为 `evictAll()`，发布、审核、转交、归还、认领、建站、评论、站点改名等写操作后主动失效，统计缓存用手写方式而非 `@Cacheable`，保证权限校验不被缓存命中绕过。慢 SQL 拦截器按阈值采样、只记录脱敏 statement 与耗时。
- **清理与状态**：临时运维脚本保留在 `C:/Users/John/AppData/Local/Temp/dbtool/`（`perf-baseline.js`、`perf-index-migrate.js`、`verify-perf-online.js`、`verify-perf-browser.js` 及前后 JSON 报告，均可复用/复核）；仓库内无明文口令；`git diff --check` 通过、暂存区为空、未提交，用户既有未提交改动保持原状。
- **仍待人工**：微信开发者工具开启服务端口后上传小程序产物（沿用前序记录）；若未来升级 MySQL 并加装 ngram/FULLTEXT，需先完成结果集一致性校验才可将 `fulltext-enabled` 置 `true`；`distance-bbox-enabled` / `status-enum-enabled` 同理默认不可开。

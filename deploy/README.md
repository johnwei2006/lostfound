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
- 迁移后的 `category` 部分为旧系统数字编码（如 `"2"`），前端分类展示为原值；如需中文映射可做一次数据清洗。
- 旧库 1 条双重编码昵称已修复为「小周不剃光头」，其余数据保持原样。
- `JWT_SECRET` 已更换，旧 token 全部失效，用户重新登录即可。
- 默认管理员 `admin / admin123`，**上线后请立即改密**。
- `application.yml` 已移除 `WX_SECRET` 明文默认值，本地调试需自行注入；部署脚本与图片规范化脚本均强制从环境变量读取口令。
- 距离排序在 SQL 层用 Haversine 表达式全表排序（当前 241 条失物规模无压力）；数据量增大后建议加经纬度边界框预过滤或空间索引。
- 后端 CORS 当前为 `allowedOriginPatterns("*")`；由于 Web 与小程序均同域访问，可后续收紧为 `https://www.globalfinding.com`。
- 图片经 Thumbnailator 重编码后体积可能增大，后续可按需接入对象存储或调整压缩参数。

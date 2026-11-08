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
- [x] 小程序 1.1.0：启动静默登录、启动定位、首页距离/时间排序、卡片距离展示、地图定位到当前位置
- [x] 线上 `sort=distance` 返回 `total=241` 且距离递增（3.61km → 32.68km → 85.16km…），`sort=time` 按发布时间递减，无坐标自动回退时间排序
- [x] 后端 `GET /api/lost/page?sort=distance&userLat=30.593&userLng=114.305` → 距离递增且 `distance` 字段正确；`sort=time` → 发布时间递减；`/latest`、`/map`、`mine` 保持原排序
- [x] 正式证书已启用（DigiCert DV，SAN 覆盖两个域名，有效期至 2027-02-05），`openssl s_client` 验证链完整
- [x] 历史图片 `https://www.globalfinding.com/image/20160508/1_232651.jpeg` → 200
- [x] 51 条失物记录的图片地址已补全为 HTTPS 绝对 URL，微信小程序 `<image>` 可直接加载
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
3. **地理位置接口**：`开发管理 → 接口设置` 中确认 `getLocation` 已申请并开通（`requiredPrivateInfos` 已在 `manifest.json` 声明，`permission.scope.userLocation.desc` 写明用于距离排序与地图定位）。注意该 `desc` 上限为 **30 字**，超长会在上传时被微信以 `80058` 拒绝；当前使用「用于展示附近失物招领记录及地图定位」（17 字）。
4. **用户隐私保护指引**：在公众平台配置隐私协议并勾选「地理位置」；1.1.0 的 `utils/location.js` 已兼容 `getPrivacySetting` / `requirePrivacyAuthorize`，用户未同意或接口不可用时自动回退默认城市 + 时间排序，不会阻塞首屏。若后台开启 `__usePrivacyCheck__`，需在 `manifest.json` 的 `mp-weixin` 节点补充该配置后再构建上传。
5. 按合规要求补齐用户协议、用户输入内容安全检测；提交审核时提供测试账号，并在审核备注中说明「进入即静默登录、定位用于附近物品排序」。
6. 版本管理中将 **1.1.0** 开发版提交审核并发布（此前 1.0.2 为上一版本）。

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

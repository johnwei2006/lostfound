/**
 * 寻物志后端非交互部署脚本（在本地 Windows/Linux 运行）
 *
 * 用法：
 *   npm install
 *   LOSTFOUND_SSH_PASSWORD=xxx LOSTFOUND_DB_PASSWORD=xxx LOSTFOUND_WX_SECRET=xxx \
 *     node remote-deploy.js all
 *
 * 阶段：survey | install | backend | web | all
 * 口令只从环境变量读取，绝不写入仓库。
 */
const { Client } = require('ssh2')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const ROOT = path.resolve(__dirname, '..')
const HOST = process.env.LOSTFOUND_SSH_HOST || '120.26.254.139'
const USER = process.env.LOSTFOUND_SSH_USER || 'root'
const PASSWORD = process.env.LOSTFOUND_SSH_PASSWORD
const DOMAIN = process.env.LOSTFOUND_DOMAIN || 'www.globalfinding.com'

const JAR_LOCAL = path.join(ROOT, 'lostfound-server', 'target', 'lostfound-server-1.0.0.jar')
const WEB_DIST = path.join(ROOT, 'lostfound-web', 'dist')
const SERVICE_LOCAL = path.join(__dirname, 'lostfound.service')
const NGINX_LOCAL = path.join(__dirname, 'nginx-lostfound.conf')

if (!PASSWORD) {
  console.error('缺少环境变量 LOSTFOUND_SSH_PASSWORD')
  process.exit(1)
}

function envContent() {
  const missing = ['LOSTFOUND_DB_PASSWORD', 'LOSTFOUND_WX_SECRET']
    .filter((k) => !process.env[k])
  if (missing.length) {
    throw new Error(`缺少环境变量：${missing.join(', ')}（口令不从代码读取）`)
  }
  const jwt = process.env.LOSTFOUND_JWT_SECRET || crypto.randomBytes(32).toString('hex')
  return [
    'DB_HOST=127.0.0.1',
    'DB_PORT=3306',
    'DB_NAME=lostfound',
    'DB_USER=root',
    `DB_PASSWORD=${process.env.LOSTFOUND_DB_PASSWORD}`,
    'SERVER_ADDRESS=127.0.0.1',
    `JWT_SECRET=${jwt}`,
    `WX_APPID=${process.env.LOSTFOUND_WX_APPID || 'wxb45bbe07cf07250c'}`,
    `WX_SECRET=${process.env.LOSTFOUND_WX_SECRET || ''}`,
    'UPLOAD_DIR=/home/lostfound/image',
    `UPLOAD_BASE_URL=https://${DOMAIN}/uploads`,
    '',
  ].join('\n')
}

function connect() {
  return new Promise((resolve, reject) => {
    const conn = new Client()
    conn.on('ready', () => resolve(conn))
    conn.on('error', reject)
    conn.connect({ host: HOST, port: 22, username: USER, password: PASSWORD, readyTimeout: 30000 })
  })
}

function exec(conn, cmd, label) {
  return new Promise((resolve) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return resolve({ code: -1, out: '', err: err.message })
      let out = '', errOut = ''
      stream.on('close', (code) => resolve({ code, out: out.trim(), err: errOut.trim() }))
      stream.on('data', (d) => { out += d.toString() })
      stream.stderr.on('data', (d) => { errOut += d.toString() })
    })
  })
}

async function run(conn, cmd, label, { fatal = true } = {}) {
  const r = await exec(conn, cmd, label)
  const head = label ? `[${label}] ` : ''
  if (r.out) console.log(head + r.out.split('\n').slice(0, 30).join('\n'))
  if (r.err && r.code !== 0) console.log(head + 'stderr: ' + r.err.split('\n').slice(0, 10).join('\n'))
  if (fatal && r.code !== 0) throw new Error(`${label || cmd} 失败 (exit ${r.code})`)
  return r
}

function sftp(conn) {
  return new Promise((resolve, reject) => {
    conn.sftp((err, s) => (err ? reject(err) : resolve(s)))
  })
}

function mkdirp(s, dir) {
  return new Promise((resolve, reject) => {
    const parts = dir.split('/').filter(Boolean)
    let cur = ''
    const next = () => {
      if (!parts.length) return resolve()
      cur += '/' + parts.shift()
      s.mkdir(cur, (e) => {
        if (e && e.code !== 4 && !/exists|failure/i.test(e.message)) return reject(new Error(`${cur}: ${e.message}`))
        next()
      })
    }
    next()
  })
}

function putFile(s, local, remote, mode) {
  return new Promise((resolve, reject) => {
    s.fastPut(local, remote, (err) => {
      if (err) return reject(err)
      if (mode) return s.chmod(remote, mode, (e) => (e ? reject(e) : resolve()))
      resolve()
    })
  })
}

function putBuffer(s, content, remote, mode) {
  const tmp = path.join(__dirname, '.tmp-env-' + Date.now())
  fs.writeFileSync(tmp, content)
  return putFile(s, tmp, remote, mode).finally(() => fs.existsSync(tmp) && fs.unlinkSync(tmp))
}

async function putDir(s, localDir, remoteDir) {
  await mkdirp(s, remoteDir)
  for (const entry of fs.readdirSync(localDir, { withFileTypes: true })) {
    const l = path.join(localDir, entry.name)
    const r = `${remoteDir}/${entry.name}`
    if (entry.isDirectory()) await putDir(s, l, r)
    else await putFile(s, l, r)
  }
}

async function survey(conn) {
  const cmds = [
    ['OS', 'cat /etc/os-release | head -4'],
    ['JAVA', 'java -version 2>&1 || true'],
    ['NGINX', 'nginx -v 2>&1; systemctl is-active nginx'],
    ['PORTS', "ss -lntp | grep -E ':80 |:443 |:8080 |:3306 ' || true"],
    ['CERT', `openssl x509 -in /etc/letsencrypt/live/${DOMAIN}/fullchain.pem -noout -enddate 2>/dev/null || echo '证书不存在'`],
    ['MYSQL', '/home/mysql/bin/mysql -h127.0.0.1 -uroot -plostfound -N -e "SELECT COUNT(*) FROM lostfound.lost" 2>/dev/null || true'],
    ['SERVICE', 'systemctl is-active lostfound 2>/dev/null || true'],
  ]
  for (const [n, c] of cmds) await run(conn, c, n, { fatal: false })
}

async function install(conn) {
  console.log('\n--- 1/5 安装 Java 17 运行时 + certbot ---')
  await run(conn, 'apt-get update', 'apt-update')
  // Debian 11 的 security 源已下架 openjdk-17 相关包，且 ca-certificates 版本冲突，
  // 因此 JRE 采用 Eclipse Temurin 17 便携包，certbot 单独从 bullseye main 安装。
  await run(conn, 'DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends certbot', 'apt-certbot')
  const jreUrl = 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jre/x64/linux/OpenJDK17U-jre_x64_linux_hotspot_17.0.21_9.tar.gz'
  await run(conn,
    `if [ ! -x /opt/jdk17/bin/java ]; then mkdir -p /opt/jdk17 && curl -fsSL --max-time 300 -o /tmp/jre17.tar.gz '${jreUrl}' && tar -xzf /tmp/jre17.tar.gz -C /opt/jdk17 --strip-components=1 && rm -f /tmp/jre17.tar.gz; fi; update-alternatives --install /usr/bin/java java /opt/jdk17/bin/java 1700`,
    'install-jre17')
  await run(conn, 'java -version 2>&1', 'java-version')

  console.log('\n--- 2/5 备份 Nginx 与旧站配置 ---')
  const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
  await run(conn, `mkdir -p /root/lostfound-backup-${ts} && cp -a /etc/nginx /root/lostfound-backup-${ts}/ && echo BACKUP=/root/lostfound-backup-${ts}`, 'backup')

  console.log('\n--- 3/5 停用旧 Tomcat 释放 8080 ---')
  await run(conn, `pid=$(ss -lntp | grep ':8080 ' | sed -n 's/.*pid=\\([0-9]*\\).*/\\1/p' | head -1); if [ -n "$pid" ]; then echo "stopping pid=$pid"; kill "$pid"; sleep 3; fi; ss -lntp | grep ':8080 ' || echo '8080 已释放'`, 'stop-tomcat')

  console.log('\n--- 4/5 HTTPS 证书 ---')
  const formalCert = await exec(conn,
    'test -f /home/cert/globalfinding.com.pem && test -f /home/cert/globalfinding.com.key && echo yes || echo no')
  if (formalCert.out.trim() === 'yes') {
    await run(conn,
      'openssl x509 -in /home/cert/globalfinding.com.pem -noout -subject -issuer -enddate; a=$(openssl x509 -in /home/cert/globalfinding.com.pem -noout -modulus | openssl md5); b=$(openssl rsa -in /home/cert/globalfinding.com.key -noout -modulus 2>/dev/null | openssl md5); [ "$a" = "$b" ] && echo "证书与私钥匹配" || echo "警告：证书与私钥不匹配"',
      'cert-check')
    console.log('检测到 /home/cert 正式证书，跳过 certbot 签发')
  } else {
    console.log('未找到正式证书，申请 Let\'s Encrypt 证书（standalone，短暂占用 80）')
    await run(conn, 'systemctl stop nginx', 'stop-nginx')
    const cert = await exec(conn,
      `certbot certonly --standalone --non-interactive --agree-tos --register-unsafely-without-email -d ${DOMAIN} -d globalfinding.com --preferred-challenges http --keep-until-expiring 2>&1`)
    console.log(cert.out.split('\n').slice(-25).join('\n'))
    if (cert.code !== 0 && cert.code !== -1) {
      await run(conn, 'systemctl start nginx', 'restore-nginx', { fatal: false })
      throw new Error('证书申请失败，已恢复旧 Nginx；请检查 80 端口外网可达性或提供有效证书')
    }
    await run(conn, `test -f /etc/letsencrypt/live/${DOMAIN}/fullchain.pem && openssl x509 -in /etc/letsencrypt/live/${DOMAIN}/fullchain.pem -noout -subject -enddate`, 'cert-check')
  }

  console.log('\n--- 5/5 创建目录 ---')
  await run(conn, 'mkdir -p /opt/lostfound/uploads /home/lostfound/image /etc/lostfound /var/www/lostfound-web /var/www/letsencrypt && chmod 600 /etc/lostfound && echo OK', 'dirs')
  console.log('环境升级完成；Nginx 暂处停止状态，执行 web 阶段时启动')
}

async function backend(conn) {
  if (!fs.existsSync(JAR_LOCAL)) throw new Error(`找不到 jar：${JAR_LOCAL}，请先运行 mvn clean package -DskipTests`)
  const s = await sftp(conn)

  console.log('\n--- 上传 jar + systemd + 环境文件 ---')
  await run(conn, 'if [ -f /opt/lostfound/lostfound-server.jar ]; then cp /opt/lostfound/lostfound-server.jar /opt/lostfound/lostfound-server.jar.bak; fi', 'backup-jar')
  await mkdirp(s, '/opt/lostfound/uploads')
  await mkdirp(s, '/home/lostfound/image')
  await mkdirp(s, '/etc/lostfound')
  // 切换到历史图片目录后，把此前上传到旧目录的文件并入（只复制不删除，保证旧 URL 仍可用）
  await run(conn, 'if [ -d /opt/lostfound/uploads ]; then cp -an /opt/lostfound/uploads/. /home/lostfound/image/ 2>/dev/null || true; fi; mkdir -p /home/lostfound/image && echo "图片目录文件数: $(find /home/lostfound/image -type f | wc -l)"', 'merge-uploads')
  await putFile(s, JAR_LOCAL, '/opt/lostfound/lostfound-server.jar')
  await putFile(s, SERVICE_LOCAL, '/etc/systemd/system/lostfound.service')
  await putBuffer(s, envContent(), '/etc/lostfound/lostfound.env', 0o600)
  console.log('上传完成')

  console.log('\n--- 启动服务 ---')
  // 必须 restart：enable --now 对已 active 的服务不会重载新 jar，
  // 运行中覆盖 jar 会导致 JVM 类加载失败（ClassNotFoundException）。
  await run(conn, 'systemctl daemon-reload && systemctl enable lostfound && systemctl restart lostfound', 'start')
  await run(conn,
    'for i in $(seq 1 30); do code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://127.0.0.1:8080/api/foundsite/page?page=1&size=1"); [ "$code" = "200" ] && break; sleep 2; done; echo "health=$code"; systemctl is-active lostfound',
    'health', { fatal: false })
  await run(conn, 'curl -s --max-time 10 "http://127.0.0.1:8080/api/foundsite/page?page=1&size=1" | head -c 300', 'api-sample', { fatal: false })
  s.end()
}

async function web(conn) {
  if (!fs.existsSync(WEB_DIST)) throw new Error(`找不到 Web 构建产物：${WEB_DIST}，请先运行 npm run build`)
  const s = await sftp(conn)

  console.log('\n--- 上传 Web 后台静态产物 ---')
  await putDir(s, WEB_DIST, '/var/www/lostfound-web')

  console.log('\n--- 配置 Nginx ---')
  await putFile(s, NGINX_LOCAL, '/etc/nginx/conf.d/lostfound.conf')
  // 必须移出 sites-enabled 目录：nginx.conf 用通配符 include，改名后缀仍会被加载
  await run(conn, 'mkdir -p /etc/nginx/disabled-sites && (mv /etc/nginx/sites-enabled/default* /etc/nginx/disabled-sites/ 2>/dev/null || true)', 'disable-old-site')
  await run(conn, 'nginx -t', 'nginx-test')
  await run(conn, 'systemctl is-active nginx >/dev/null && systemctl reload nginx || systemctl start nginx; systemctl is-active nginx', 'nginx-start')

  console.log('\n--- HTTPS 验证 ---')
  await run(conn, `curl -sk -o /dev/null -w "https_api=%{http_code}\\n" --max-time 10 "https://${DOMAIN}/api/foundsite/page?page=1&size=1"`, 'https-api', { fatal: false })
  await run(conn, `curl -sk --max-time 10 "https://${DOMAIN}/api/lost/page?page=1&size=1" | head -c 300`, 'https-sample', { fatal: false })
  await run(conn, `curl -sk -o /dev/null -w "https_web=%{http_code}\\n" --max-time 10 https://${DOMAIN}/`, 'https-web', { fatal: false })
  await run(conn, 'systemctl list-timers 2>/dev/null | grep certbot || true', 'cert-renew', { fatal: false })
  s.end()
}

async function main() {
  const phase = process.argv[2] || 'survey'
  const conn = await connect()
  console.log(`已连接 ${HOST}，执行阶段：${phase}`)
  try {
    if (phase === 'survey') await survey(conn)
    else if (phase === 'install') await install(conn)
    else if (phase === 'backend') await backend(conn)
    else if (phase === 'web') await web(conn)
    else if (phase === 'all') { await install(conn); await backend(conn); await web(conn) }
    else throw new Error(`未知阶段：${phase}`)
    console.log(`\n阶段 ${phase} 完成`)
  } catch (e) {
    console.error('\n执行失败：' + e.message)
    process.exitCode = 1
  } finally {
    conn.end()
  }
}
main().catch((e) => { console.error(e); process.exit(1) })

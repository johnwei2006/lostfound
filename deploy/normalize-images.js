/**
 * 规范化历史图片 URL（幂等）
 *
 * 旧系统把图片路径存为 /image/YYYYMMDD/xxx.jpeg 这类相对路径，
 * 微信小程序 <image> 会把它当成本地包内路径而无法加载。
 * 本脚本将其补全为线上 HTTPS 绝对地址；Nginx /image/ 从 /home/lostfound/image 提供文件。
 *
 * 用法：
 *   LOSTFOUND_DB_PASSWORD=xxx node normalize-images.js
 */
const mysql = require('mysql2/promise')

const DOMAIN = process.env.LOSTFOUND_DOMAIN || 'www.globalfinding.com'
const BASE = `https://${DOMAIN}`

if (!process.env.LOSTFOUND_DB_PASSWORD) {
  console.error('缺少环境变量 LOSTFOUND_DB_PASSWORD（口令不从代码读取）')
  process.exit(1)
}

const config = {
  host: process.env.LOSTFOUND_DB_HOST || '120.26.254.139',
  port: Number(process.env.LOSTFOUND_DB_PORT || 3306),
  user: process.env.LOSTFOUND_DB_USER || 'root',
  password: process.env.LOSTFOUND_DB_PASSWORD,
  database: process.env.LOSTFOUND_DB_NAME || 'lostfound',
  charset: 'utf8mb4',
  connectTimeout: 15000,
}

function normalizeOne(value) {
  if (!value) return value
  if (value.startsWith('http://') || value.startsWith('https://')) {
    // 微信头像等外部图片升级为 HTTPS，避免 HTTPS 页面混合内容被拦截
    return value.replace(/^http:\/\//, 'https://')
  }
  if (value.startsWith('/')) return BASE + value
  return BASE + '/' + value
}

async function normalizeJsonColumn(conn, table) {
  const [rows] = await conn.query(
    `SELECT id, images FROM \`${table}\` WHERE images IS NOT NULL AND images <> ''`)
  let changed = 0
  for (const row of rows) {
    let list
    try { list = JSON.parse(row.images) } catch { continue }
    if (!Array.isArray(list)) continue
    const next = list.map(normalizeOne)
    const json = JSON.stringify(next)
    if (json !== JSON.stringify(list)) {
      await conn.query(`UPDATE \`${table}\` SET images = ?, update_time = NOW() WHERE id = ?`, [json, row.id])
      changed++
    }
  }
  return { total: rows.length, changed }
}

async function main() {
  const conn = await mysql.createConnection(config)
  for (const table of ['lost', 'found']) {
    const r = await normalizeJsonColumn(conn, table)
    console.log(`${table}.images: ${r.changed}/${r.total} 行已规范化`)
  }
  const [avatar] = await conn.query(
    `UPDATE user SET avatar = CONCAT('https://', SUBSTRING(avatar, 8)), update_time = NOW() WHERE avatar LIKE 'http://%'`)
  console.log(`user.avatar: ${avatar.affectedRows} 行升级为 HTTPS`)

  const [samples] = await conn.query(
    `SELECT id, images FROM lost WHERE images LIKE '%/image/%' LIMIT 3`)
  console.log('样例:', samples)
  await conn.end()
  console.log('DONE（可重复执行）')
}
main().catch((e) => { console.error('ERR:', e.message); process.exit(1) })
